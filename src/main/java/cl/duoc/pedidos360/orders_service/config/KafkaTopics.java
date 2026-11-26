package cl.duoc.pedidos360.orders_service.config;

/**
 * Tópicos de Kafka que usa este servicio (los crea kafka-infra/create-topics.sh).
 */
public final class KafkaTopics {

    /** Historial de pedidos; lo consume reportes-service (grupo reports-group) */
    public static final String ORDER_EVENTS = "order-events";

    private KafkaTopics() {
    }
}
