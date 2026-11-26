package cl.duoc.pedidos360.orders_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP hacia ms-pago para consultar si el usuario está bloqueado por el antifraude.
 * En la EC2 los servicios están en el mismo docker-compose: MS_PAGO_URL=http://ms-pago:8087.
 * Timeout corto: si ms-pago tarda, se aplica fail-open (ver FraudStatusClient).
 */
@Configuration
public class MsPagoClientConfig {

    @Bean
    public RestClient msPagoRestClient(@Value("${ms-pago.url:http://localhost:8087}") String url,
                                       @Value("${ms-pago.timeout-ms:1500}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
