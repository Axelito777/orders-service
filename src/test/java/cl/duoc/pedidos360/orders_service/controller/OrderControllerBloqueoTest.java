package cl.duoc.pedidos360.orders_service.controller;

import cl.duoc.pedidos360.orders_service.config.SecurityConfig;
import cl.duoc.pedidos360.orders_service.event.OrderEventPublisher;
import cl.duoc.pedidos360.orders_service.event.OrderKafkaPublisher;
import cl.duoc.pedidos360.orders_service.exception.UsuarioBloqueadoException;
import cl.duoc.pedidos360.orders_service.model.Order;
import cl.duoc.pedidos360.orders_service.repository.OrderRepository;
import cl.duoc.pedidos360.orders_service.service.FraudStatusClient;
import cl.duoc.pedidos360.orders_service.support.TokenDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /orders con el usuario bloqueado por el antifraude: 423 y no se guarda ni se publica nada */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerBloqueoTest {

    private static final String BODY = """
            {"items":[{"productId":1,"productName":"Casio","quantity":2,"unitPrice":99990}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderEventPublisher eventPublisher;

    @MockitoBean
    private OrderKafkaPublisher orderKafkaPublisher;

    @MockitoBean
    private FraudStatusClient fraudStatusClient;

    @TestBean
    private JwtDecoder jwtDecoder;

    static JwtDecoder jwtDecoder() {
        return TokenDePrueba.decoder();
    }

    private static String token(String oid) {
        return TokenDePrueba.bearer(Map.of("oid", oid, "scp", "orders.write"));
    }

    @Test
    void usuarioBloqueado_423_yNoCreaPedido() throws Exception {
        String bearer = token("oid-ana");
        doThrow(new UsuarioBloqueadoException("oid-ana", Instant.parse("2026-11-26T16:40:00Z")))
                .when(fraudStatusClient).verificarNoBloqueado("oid-ana", bearer);

        mockMvc.perform(post("/orders").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error").value("USER_BLOCKED"))
                .andExpect(jsonPath("$.until").value("2026-11-26T16:40:00Z"));

        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishOrderCreated(any());
        verify(orderKafkaPublisher, never()).publicar(any());
    }

    @Test
    void otroUsuarioNoBloqueado_200() throws Exception {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(5L);
            return o;
        });

        mockMvc.perform(post("/orders").header("Authorization", token("oid-beto"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(fraudStatusClient).verificarNoBloqueado(org.mockito.ArgumentMatchers.eq("oid-beto"), any());
    }
}
