package cl.duoc.pedidos360.orders_service.support;

import cl.duoc.pedidos360.orders_service.config.SecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Tokens de prueba firmados con una llave RSA local (sin red).
 * Hace el papel de Azure: el decoder de los tests usa la llave pública y el mismo validador de producción.
 */
public final class TokenDePrueba {

    public static final String TENANT = "e5372bf0-c5e3-4286-887c-79069f209c1f";
    public static final String CLIENT_ID = "42b7d432-638e-4b04-9368-9616b322e672";
    public static final String EMISOR = SecurityConfig.emisor(TENANT);

    /** Llave "de Azure" y otra de un atacante (firma inválida) */
    public static final KeyPair LLAVE_AZURE = generarLlave();
    public static final KeyPair LLAVE_FALSA = generarLlave();

    private TokenDePrueba() {
    }

    /** Decoder con la llave local + validadores de producción (emisor, audiencia, expiración) */
    public static JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) LLAVE_AZURE.getPublic()).build();
        decoder.setJwtValidator(SecurityConfig.validadorToken(EMISOR, SecurityConfig.audiencias(CLIENT_ID)));
        return decoder;
    }

    /** "Bearer ..." válido (emisor y audiencia correctos, expira en 10 min) con los claims dados */
    public static String bearer(Map<String, Object> claims) {
        return "Bearer " + firmar(LLAVE_AZURE, EMISOR, "api://" + CLIENT_ID, Instant.now().plusSeconds(600), claims);
    }

    /** Firma un JWT RS256 como lo haría Azure */
    public static String firmar(KeyPair llave, String emisor, String audiencia, Instant expira, Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                    .issuer(emisor)
                    .audience(audiencia)
                    .subject("usuario-test")
                    .issueTime(Date.from(expira.minusSeconds(3600)))
                    .expirationTime(Date.from(expira));
            claims.forEach(b::claim);
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), b.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) llave.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generarLlave() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
