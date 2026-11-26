package cl.duoc.pedidos360.orders_service.dto;

import java.time.Instant;

/** Respuesta de GET /payments/fraud-status/{userId} de ms-pago */
public record FraudStatusResponse(boolean blocked, Instant until) {
}
