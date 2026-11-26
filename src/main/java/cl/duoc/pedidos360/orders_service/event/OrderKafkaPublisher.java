package cl.duoc.pedidos360.orders_service.event;

import cl.duoc.pedidos360.orders_service.config.KafkaTopics;
import cl.duoc.pedidos360.orders_service.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publica el pedido en el tópico order-events para reportes-service.
 * Si Kafka falla, solo se loguea: la compra del usuario NO debe fallar por esto.
 */
@Slf4j
@Component
public class OrderKafkaPublisher {

    private final KafkaTemplate<String, OrderKafkaEvent> kafkaTemplate;

    public OrderKafkaPublisher(KafkaTemplate<String, OrderKafkaEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Arma el evento a partir del pedido ya guardado y lo publica */
    public void publicar(Order order) {
        OrderKafkaEvent evento = aEvento(order);
        String key = String.valueOf(order.getId());
        try {
            // KEY = orderId: todos los mensajes de un mismo pedido caen en la misma partición
            kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, key, evento)
                    .whenComplete((resultado, error) -> {
                        // Error asíncrono: el broker no confirmó el mensaje
                        if (error != null) {
                            log.error("No se pudo publicar el pedido {} en Kafka: {}", key, error.getMessage());
                        }
                    });
        } catch (Exception e) {
            // Error síncrono: por ejemplo, no hay metadata del broker dentro de max.block.ms
            log.error("Kafka no disponible, el pedido {} no se publicó en order-events: {}", key, e.getMessage());
        }
    }

    static OrderKafkaEvent aEvento(Order order) {
        List<OrderKafkaEvent.ItemEvent> items = order.getItems() == null ? List.of()
                : order.getItems().stream()
                        .map(i -> OrderKafkaEvent.ItemEvent.builder()
                                .productId(i.getProductId())
                                .productName(i.getProductName())
                                .quantity(i.getQuantity())
                                .unitPrice(i.getUnitPrice())
                                .build())
                        .toList();
        return OrderKafkaEvent.builder()
                .orderId(order.getId())
                .userId(order.getUsuarioId())
                .total(order.getTotal())
                .fecha(order.getCreatedAt())
                .estado(order.getStatus() != null ? order.getStatus().name() : null)
                .items(items)
                .build();
    }
}
