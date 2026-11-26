package cl.duoc.pedidos360.orders_service.event;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Evento de pedido que va al tópico order-events (Kafka).
 * Es distinto de OrderCreatedEvent (RabbitMQ) para no tocar lo que leen stock y notificaciones.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderKafkaEvent {
    private Long orderId;
    private String userId;
    private Double total;
    /** Fecha del pedido, sin zona: el mismo reloj (LocalDateTime.now()) que usaba reportes-service */
    private LocalDateTime fecha;
    private String estado;
    private List<ItemEvent> items;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ItemEvent {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Double unitPrice;
    }
}
