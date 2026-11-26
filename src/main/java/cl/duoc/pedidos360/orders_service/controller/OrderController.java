package cl.duoc.pedidos360.orders_service.controller;


import cl.duoc.pedidos360.orders_service.dto.CreateOrderRequest;
import cl.duoc.pedidos360.orders_service.event.*;
import cl.duoc.pedidos360.orders_service.model.*;
import cl.duoc.pedidos360.orders_service.repository.OrderRepository;
import cl.duoc.pedidos360.orders_service.service.FraudStatusClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderKafkaPublisher orderKafkaPublisher;
    private final FraudStatusClient fraudStatusClient;

    public OrderController(OrderRepository orderRepository, OrderEventPublisher eventPublisher,
                           OrderKafkaPublisher orderKafkaPublisher, FraudStatusClient fraudStatusClient) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.orderKafkaPublisher = orderKafkaPublisher;
        this.fraudStatusClient = fraudStatusClient;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String usuarioId = extraerClaim(authHeader, "oid");
        String email = extraerClaim(authHeader, "preferred_username");

        // Usuario bloqueado por el antifraude de ms-pago -> 423 (si ms-pago no responde, fail-open)
        fraudStatusClient.verificarNoBloqueado(usuarioId, authHeader);

        List<OrderItem> items = request.getItems().stream()
                .map(i -> OrderItem.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        double total = items.stream().mapToDouble(i -> i.getUnitPrice() * i.getQuantity()).sum();

        Order order = Order.builder()
                .usuarioId(usuarioId)
                .total(total)
                .status(Order.OrderStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .items(items)
                .build();

        items.forEach(i -> i.setOrder(order));
        Order saved = orderRepository.save(order);

        eventPublisher.publishOrderCreated(OrderCreatedEvent.builder()
                .orderId(saved.getId())
                .usuarioId(usuarioId)
                .email(email)
                .total(total)
                .items(saved.getItems().stream()
                        .map(i -> OrderCreatedEvent.ItemEvent.builder()
                                .productId(i.getProductId())
                                .quantity(i.getQuantity())
                                .build())
                        .collect(Collectors.toList()))
                .build());

        // Kafka (order-events) para reportes-service. Si Kafka falla, la compra no falla.
        orderKafkaPublisher.publicar(saved);

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Order>> getMyOrders(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String usuarioId = extraerClaim(authHeader, "oid");
        return ResponseEntity.ok(orderRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId));
    }

    private String extraerClaim(String authHeader, String claim) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            String[] partes = token.split("\\.");
            String payload = partes[1];

            int resto = payload.length() % 4;
            if (resto == 2) payload += "==";
            else if (resto == 3) payload += "=";

            String payloadJson = new String(Base64.getUrlDecoder().decode(payload));

            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + claim + "\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(payloadJson);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            System.err.println("No se pudo extraer " + claim + " del token: " + e.getMessage());
            return null;
        }
    }
}