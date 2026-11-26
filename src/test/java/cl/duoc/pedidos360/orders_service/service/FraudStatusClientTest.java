package cl.duoc.pedidos360.orders_service.service;

import cl.duoc.pedidos360.orders_service.config.MsPagoClientConfig;
import cl.duoc.pedidos360.orders_service.exception.UsuarioBloqueadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Consulta de bloqueo a ms-pago sin red (MockRestServiceServer) y política fail-open */
class FraudStatusClientTest {

    private static final String BEARER = "Bearer token-del-usuario";
    private static final String URL = "http://ms-pago:8087/payments/fraud-status/";

    private MockRestServiceServer server;
    private FraudStatusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ms-pago:8087");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FraudStatusClient(builder.build());
    }

    @Test
    void usuarioBloqueado_lanza423ConUntil_yReenviaElToken() {
        server.expect(requestTo(URL + "oid-ana"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", BEARER))
                .andRespond(withSuccess("{\"blocked\":true,\"until\":\"2026-11-26T16:40:00Z\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verificarNoBloqueado("oid-ana", BEARER))
                .isInstanceOf(UsuarioBloqueadoException.class)
                .extracting("until").isEqualTo(Instant.parse("2026-11-26T16:40:00Z"));
        server.verify();
    }

    @Test
    void usuarioNoBloqueado_dejaPasar() {
        server.expect(requestTo(URL + "oid-ana"))
                .andRespond(withSuccess("{\"blocked\":false,\"until\":null}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> client.verificarNoBloqueado("oid-ana", BEARER)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void otroUsuario_noSeVeAfectado() {
        server.expect(requestTo(URL + "oid-ana"))
                .andRespond(withSuccess("{\"blocked\":true,\"until\":\"2026-11-26T16:40:00Z\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "oid-beto"))
                .andRespond(withSuccess("{\"blocked\":false,\"until\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verificarNoBloqueado("oid-ana", BEARER))
                .isInstanceOf(UsuarioBloqueadoException.class);
        assertThatCode(() -> client.verificarNoBloqueado("oid-beto", BEARER)).doesNotThrowAnyException();
    }

    @Test
    void msPagoConError500_failOpen() {
        server.expect(requestTo(URL + "oid-ana")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> client.verificarNoBloqueado("oid-ana", BEARER)).doesNotThrowAnyException();
    }

    @Test
    void msPagoRechazaElToken403_failOpen() {
        server.expect(requestTo(URL + "oid-ana")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatCode(() -> client.verificarNoBloqueado("oid-ana", BEARER)).doesNotThrowAnyException();
    }

    /** ms-pago apagado de verdad (puerto sin nada escuchando): responde rápido y deja pasar */
    @Test
    void msPagoNoResponde_failOpen() {
        FraudStatusClient real = new FraudStatusClient(new MsPagoClientConfig().msPagoRestClient("http://localhost:1", 500));

        assertThatCode(() -> real.verificarNoBloqueado("oid-ana", BEARER)).doesNotThrowAnyException();
    }

    @Test
    void sinUsuarioOSinToken_noConsulta() {
        assertThatCode(() -> client.verificarNoBloqueado(null, BEARER)).doesNotThrowAnyException();
        assertThatCode(() -> client.verificarNoBloqueado("oid-ana", null)).doesNotThrowAnyException();
        server.verify(); // no se esperaba ninguna llamada
    }
}
