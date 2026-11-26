package cl.duoc.pedidos360.orders_service.event;

import cl.duoc.pedidos360.orders_service.config.KafkaTopics;
import cl.duoc.pedidos360.orders_service.model.Order;
import cl.duoc.pedidos360.orders_service.model.OrderItem;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Publicación del pedido en order-events: contenido del evento y tolerancia a fallas de Kafka */
class OrderKafkaPublisherTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 26, 15, 30, 0);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OrderKafkaEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final OrderKafkaPublisher publisher = new OrderKafkaPublisher(kafkaTemplate);

    private static Order pedido() {
        Order order = Order.builder()
                .id(42L).usuarioId("oid-1").total(199980.0)
                .status(Order.OrderStatus.CREATED).createdAt(FECHA)
                .items(List.of(OrderItem.builder().productId(7L).productName("Casio")
                        .quantity(2).unitPrice(99990.0).build()))
                .build();
        order.getItems().forEach(i -> i.setOrder(order));
        return order;
    }

    @Test
    void publicaEnOrderEventsConKeyOrderIdYDatosDelPedido() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publicar(pedido());

        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_EVENTS), eq("42"), argThat(e ->
                e.getOrderId() == 42L
                        && "oid-1".equals(e.getUserId())
                        && e.getTotal() == 199980.0
                        && FECHA.equals(e.getFecha())
                        && "CREATED".equals(e.getEstado())
                        && e.getItems().size() == 1
                        && e.getItems().get(0).getProductId() == 7L
                        && e.getItems().get(0).getQuantity() == 2));
    }

    @Test
    void siSendLanzaExcepcion_noSePropaga() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new KafkaException("Kafka caído"));

        assertThatCode(() -> publisher.publicar(pedido())).doesNotThrowAnyException();
    }

    @Test
    void siElBrokerNoConfirma_noSePropaga() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("sin ack")));

        assertThatCode(() -> publisher.publicar(pedido())).doesNotThrowAnyException();
    }

    /** Productor real apuntando a un puerto sin broker: espera max.block.ms y sigue sin lanzar */
    @Test
    void kafkaQueNoResponde_noFallaYRespetaMaxBlock() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
        DefaultKafkaProducerFactory<String, OrderKafkaEvent> pf = new DefaultKafkaProducerFactory<>(props);
        OrderKafkaPublisher real = new OrderKafkaPublisher(new KafkaTemplate<>(pf));
        try {
            long inicio = System.currentTimeMillis();
            assertThatCode(() -> real.publicar(pedido())).doesNotThrowAnyException();
            assertThat(System.currentTimeMillis() - inicio).isLessThan(5000);
        } finally {
            pf.destroy();
        }
    }

    /** La fecha viaja como LocalDateTime ISO sin zona (sin Z ni offset): reportes no cambia de huso */
    @Test
    void fechaSeSerializaIsoSinZona() {
        try (JacksonJsonSerializer<OrderKafkaEvent> serializer = new JacksonJsonSerializer<>()) {
            String json = new String(serializer.serialize(KafkaTopics.ORDER_EVENTS,
                    OrderKafkaPublisher.aEvento(pedido())), StandardCharsets.UTF_8);
            assertThat(json).contains("\"fecha\":\"2026-11-26T15:30:00\"");
            assertThat(json).contains("\"userId\":\"oid-1\"");
        }
    }
}
