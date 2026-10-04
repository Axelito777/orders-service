package cl.duoc.pedidos360.orders_service.event;

import cl.duoc.pedidos360.orders_service.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDERS_DIRECT_EXCHANGE,
                RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                event);

        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDERS_TOPIC_EXCHANGE,
                RabbitConfig.ORDER_CREATED_ROUTING_KEY,
                event);
    }
}
