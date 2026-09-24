package org.dataone.portal.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Tests for {@link KeycloakProvider} access token validation.
 */
public class KeycloakProviderTest {

    private TestKeycloak keycloak;
    private KeycloakProvider provider;

    @BeforeEach
    public void setUp() throws Exception {
        keycloak = new TestKeycloak();
        provider = keycloak.provider();
    }

    private String token(JWTClaimsSet claims) throws Exception {
        return keycloak.sign(claims).serialize();
    }

    @Test
    public void testValidateAccessToken_acceptsRealmAccessToken() throws Exception {
        JWTClaimsSet claims =
            provider.validateAccessToken(token(keycloak.accessTokenClaims().build()));

        assertEquals(TestKeycloak.ORCID, provider.getSubject(claims));
        assertEquals("Test User", KeycloakProvider.getName(claims));
    }

    @Test
    public void testValidateAccessToken_rejectsExpiredToken() throws Exception {
        String token = token(keycloak.accessTokenClaims()
            .expirationTime(new Date(System.currentTimeMillis() - 10 * 60 * 1000)).build());

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testValidateAccessToken_rejectsOtherIssuer() throws Exception {
        String token = token(keycloak.accessTokenClaims()
            .issuer("https://evil.example/realms/dataone").build());

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testValidateAccessToken_rejectsOtherAudience() throws Exception {
        String token = token(keycloak.accessTokenClaims().audience("some-other-client").build());

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testValidateAccessToken_rejectsIdToken() throws Exception {
        String token = token(keycloak.idTokenClaims("nonce").build());

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testValidateAccessToken_rejectsTokenWithoutSubjectClaim() throws Exception {
        String token = token(keycloak.accessTokenClaims().claim("preferred_username", null).build());

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testValidateAccessToken_rejectsUntrustedSignature() throws Exception {
        String token = token(keycloak.accessTokenClaims().build());

        assertThrows(BadJOSEException.class,
                     () -> keycloak.providerTrustingOtherKey().validateAccessToken(token));
    }

    @Test
    public void testIsIssuedBy() throws Exception {
        assertTrue(provider.isIssuedBy(token(keycloak.accessTokenClaims().build())));
        assertFalse(provider.isIssuedBy(
            token(keycloak.accessTokenClaims().issuer("https://cn.example.org").build())));
        assertFalse(provider.isIssuedBy("not-a-jwt"));
    }

    @Test
    public void testDisabledWithoutIssuer() {
        KeycloakProvider unconfigured =
            new KeycloakProvider(null, "d1-confidential", null, null, null, null, null);

        assertFalse(unconfigured.isEnabled());
        assertFalse(unconfigured.isLoginConfigured());
        assertFalse(unconfigured.isIssuedBy("anything"));
    }
}
