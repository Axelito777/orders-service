package cl.duoc.pedidos360.orders_service.controller;

import cl.duoc.pedidos360.orders_service.config.SecurityConfig;
import cl.duoc.pedidos360.orders_service.support.TokenDePrueba;
import cl.duoc.pedidos360.orders_service.repository.OrderRepository;
import cl.duoc.pedidos360.orders_service.service.FraudStatusClient;
import cl.duoc.pedidos360.orders_service.event.OrderEventPublisher;
import cl.duoc.pedidos360.orders_service.event.OrderKafkaPublisher;
import cl.duoc.pedidos360.orders_service.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seguridad de OrderControllerSecurityTest sin red:
 * - jwt(): reglas de scope (se salta el decoder, solo arma el usuario con el conversor real).
 * - Tokens firmados con la llave RSA de prueba: validación real (firma, emisor, audiencia, expiración).
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderEventPublisher eventPublisher;

    @MockitoBean
    private FraudStatusClient fraudStatusClient;

    @MockitoBean
    private OrderKafkaPublisher orderKafkaPublisher;

    /** Reemplaza el decoder de Azure (JWKS por internet) por uno con la llave local + mismos validadores */
    @TestBean
    private JwtDecoder jwtDecoder;

    static JwtDecoder jwtDecoder() {
        return TokenDePrueba.decoder();
    }

    /** Petición a un endpoint protegido y claims que la autorizan (para las pruebas de validación) */
    private static MockHttpServletRequestBuilder peticionValida() {
        return get("/orders");
    }

    private static final Map<String, Object> CLAIMS_VALIDOS = Map.of("oid", "oid-test", "scp", "orders.read");

    // ---------- Reglas por ruta ----------

    @Test
    void sinToken_401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scopeRead_listar_200() throws Exception {
        mockMvc.perform(get("/orders").with(conScp("orders.read")))
                .andExpect(status().isOk());
    }

    @Test
    void scopeRead_porId_200() throws Exception {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(Order.builder().id(1L).build()));
        mockMvc.perform(get("/orders/1").with(conScp("orders.read")))
                .andExpect(status().isOk());
    }

    @Test
    void scopeRead_noPuedeCrear_403() throws Exception {
        mockMvc.perform(post("/orders").with(conScp("orders.read"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloScopeWrite_noPuedeListar_403() throws Exception {
        mockMvc.perform(get("/orders").with(conScp("orders.write")))
                .andExpect(status().isForbidden());
    }

    @Test
    void scopeDeOtroServicio_403() throws Exception {
        mockMvc.perform(get("/orders/1").with(conScp("carrito.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void scopeWrite_pasaLaSeguridad_yValidaBody_400() throws Exception {
        // Con orders.write la seguridad deja pasar; el 400 viene de @Valid (body vacío)
        mockMvc.perform(post("/orders").with(conScp("orders.write"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }


    // ---------- Validación real del token (firma, emisor, audiencia, expiración) ----------

    @Test
    void tokenValido_audienciaApi_2xx() throws Exception {
        mockMvc.perform(conToken(TokenDePrueba.LLAVE_AZURE, TokenDePrueba.EMISOR, "api://" + TokenDePrueba.CLIENT_ID, Instant.now().plusSeconds(600)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void tokenValido_audienciaSinPrefijo_2xx() throws Exception {
        mockMvc.perform(conToken(TokenDePrueba.LLAVE_AZURE, TokenDePrueba.EMISOR, TokenDePrueba.CLIENT_ID, Instant.now().plusSeconds(600)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void tokenExpirado_401() throws Exception {
        // 5 min en el pasado: supera los 60 s de tolerancia de reloj
        mockMvc.perform(conToken(TokenDePrueba.LLAVE_AZURE, TokenDePrueba.EMISOR, TokenDePrueba.CLIENT_ID, Instant.now().minusSeconds(300)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtraAudiencia_401() throws Exception {
        mockMvc.perform(conToken(TokenDePrueba.LLAVE_AZURE, TokenDePrueba.EMISOR, "api://otra-app", Instant.now().plusSeconds(600)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtroEmisor_401() throws Exception {
        mockMvc.perform(conToken(TokenDePrueba.LLAVE_AZURE, "https://login.microsoftonline.com/otro-tenant/v2.0",
                        TokenDePrueba.CLIENT_ID, Instant.now().plusSeconds(600)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenConFirmaFalsa_401() throws Exception {
        mockMvc.perform(conToken(TokenDePrueba.LLAVE_FALSA, TokenDePrueba.EMISOR, TokenDePrueba.CLIENT_ID, Instant.now().plusSeconds(600)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenBasura_401() throws Exception {
        mockMvc.perform(peticionValida().header("Authorization", "Bearer no-es-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private MockHttpServletRequestBuilder conToken(java.security.KeyPair llave, String emisor, String audiencia, Instant expira) {
        return peticionValida().header("Authorization",
                "Bearer " + TokenDePrueba.firmar(llave, emisor, audiencia, expira, CLAIMS_VALIDOS));
    }

    /** Usuario autenticado con el claim scp, convertido con el mismo conversor de producción */
    private static RequestPostProcessor conScp(String scp) {
        return jwt().jwt(j -> j.claim("scp", scp)).authorities(SecurityConfig.convertidorAutoridades());
    }
}
