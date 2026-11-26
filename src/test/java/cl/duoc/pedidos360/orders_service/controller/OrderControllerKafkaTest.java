package cl.duoc.pedidos360.orders_service.controller;

import cl.duoc.pedidos360.orders_service.config.SecurityConfig;
import cl.duoc.pedidos360.orders_service.event.OrderCreatedEvent;
import cl.duoc.pedidos360.orders_service.event.OrderEventPublisher;
import cl.duoc.pedidos360.orders_service.event.OrderKafkaEvent;
import cl.duoc.pedidos360.orders_service.event.OrderKafkaPublisher;
import cl.duoc.pedidos360.orders_service.model.Order;
import cl.duoc.pedidos360.orders_service.repository.OrderRepository;
import cl.duoc.pedidos360.orders_service.service.FraudStatusClient;
import cl.duoc.pedidos360.orders_service.support.TokenDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /orders con Kafka caído: la compra responde 200 y RabbitMQ (stock y notificaciones) se publica igual.
 * Usa el OrderKafkaPublisher real con un KafkaTemplate que falla.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderKafkaPublisher.class})
class OrderControllerKafkaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderEventPublisher eventPublisher;

    @MockitoBean
    private FraudStatusClient fraudStatusClient;

    @MockitoBean
    private KafkaTemplate<String, OrderKafkaEvent> kafkaTemplate;

    @TestBean
    private JwtDecoder jwtDecoder;

    static JwtDecoder jwtDecoder() {
        return TokenDePrueba.decoder();
    }

    @Test
    void kafkaCaido_laCompraIgualResponde200() throws Exception {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(10L);
            return o;
        });
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new KafkaException("Kafka caído"));

        mockMvc.perform(post("/orders")
                        .with(jwt().jwt(j -> j.claim("scp", "orders.write"))
                                .authorities(SecurityConfig.convertidorAutoridades()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":1,"productName":"Casio","quantity":2,"unitPrice":99990}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(eventPublisher).publishOrderCreated(any(OrderCreatedEvent.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }
}
