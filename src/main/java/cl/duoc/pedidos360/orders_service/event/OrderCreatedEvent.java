package cl.duoc.pedidos360.orders_service.event;

import lombok.*;
import java.util.List;

@Getter @Setter @Builder
public class OrderCreatedEvent {
    private Long orderId;
    private String usuarioId;
    private String email;
    private Double total;
    private List<ItemEvent> items;

    @Getter @Setter @Builder
    public static class ItemEvent {
        private Long productId;
        private Integer quantity;
    }
}