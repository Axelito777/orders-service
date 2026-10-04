package cl.duoc.pedidos360.orders_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Getter @Setter
public class CreateOrderRequest {

    @NotEmpty(message = "El pedido debe tener al menos un item")
    @Valid
    private List<ItemRequest> items;

    @Getter @Setter
    public static class ItemRequest {
        @NotNull private Long productId;
        @NotBlank private String productName;
        @Min(1) private Integer quantity;
        @NotNull private Double unitPrice;
    }
}
