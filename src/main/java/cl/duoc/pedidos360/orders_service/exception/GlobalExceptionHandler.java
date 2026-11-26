package cl.duoc.pedidos360.orders_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Por ahora solo traduce el bloqueo antifraude a 423 (mismo body que ms-pago y carrito-service).
 * El resto de los errores sigue con el manejo por defecto de Spring, igual que antes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsuarioBloqueadoException.class)
    public ResponseEntity<Map<String, Object>> usuarioBloqueado(UsuarioBloqueadoException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "USER_BLOCKED");
        body.put("until", ex.getUntil());
        body.put("message", "Tu cuenta está bloqueada temporalmente por actividad sospechosa");
        return new ResponseEntity<>(body, HttpStatus.LOCKED);
    }
}
