package cl.duoc.pedidos360.orders_service.exception;

import lombok.Getter;

import java.time.Instant;

/** El usuario está bloqueado por el antifraude de ms-pago -> 423 Locked */
@Getter
public class UsuarioBloqueadoException extends RuntimeException {

    private final transient Instant until;

    public UsuarioBloqueadoException(String userId, Instant until) {
        super("Usuario " + userId + " bloqueado por actividad sospechosa hasta " + until);
        this.until = until;
    }
}
