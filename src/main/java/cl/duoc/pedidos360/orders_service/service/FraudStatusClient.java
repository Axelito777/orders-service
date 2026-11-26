package cl.duoc.pedidos360.orders_service.service;

import cl.duoc.pedidos360.orders_service.dto.FraudStatusResponse;
import cl.duoc.pedidos360.orders_service.exception.UsuarioBloqueadoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consulta a ms-pago si el usuario está bloqueado por el antifraude, reenviando su JWT.
 *
 * FAIL-OPEN: si ms-pago no responde, da error o rechaza el token, se deja pasar y se loguea.
 * Por qué: para llegar aquí el usuario ya pagó en ms-pago (si ms-pago está caído, nadie puede pagar),
 * y rechazar en ese momento dejaría cobros sin pedido. El bloqueo principal ocurre en ms-pago al pagar.
 */
@Slf4j
@Component
public class FraudStatusClient {

    private final RestClient msPagoRestClient;

    public FraudStatusClient(@Qualifier("msPagoRestClient") RestClient msPagoRestClient) {
        this.msPagoRestClient = msPagoRestClient;
    }

    /** Lanza UsuarioBloqueadoException (423) solo si ms-pago confirma que el usuario está bloqueado */
    public void verificarNoBloqueado(String userId, String authHeader) {
        if (userId == null || userId.isBlank() || authHeader == null || authHeader.isBlank()) {
            return; // sin usuario o sin token no hay qué consultar
        }
        FraudStatusResponse estado;
        try {
            estado = msPagoRestClient.get()
                    .uri("/payments/fraud-status/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .body(FraudStatusResponse.class);
        } catch (Exception e) {
            log.warn("No se pudo consultar el antifraude en ms-pago para {} (se deja pasar, fail-open): {}",
                    userId, e.getMessage());
            return;
        }
        if (estado != null && estado.blocked()) {
            log.warn("Operación rechazada: usuario {} bloqueado hasta {}", userId, estado.until());
            throw new UsuarioBloqueadoException(userId, estado.until());
        }
    }
}
