package org.dataone.portal.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;

import org.dataone.configuration.Settings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        String token = token(keycloak.accessTokenClaims().claim("orcid", null).build());

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
        KeycloakProvider unconfigured = KeycloakProvider.disabled();

        assertFalse(unconfigured.isEnabled());
        assertFalse(unconfigured.isLoginConfigured());
        assertFalse(unconfigured.isIssuedBy("anything"));
    }

    @Test
    public void testValidateAccessToken_rejectsOtherAuthorizedParty() throws Exception {
        String token = token(keycloak.accessTokenClaims().claim("azp", "some-other-client").build());

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testValidateAccessToken_rejectsOverlongToken() {
        String token = "a".repeat(KeycloakProvider.MAX_TOKEN_LENGTH + 1);

        assertThrows(BadJOSEException.class, () -> provider.validateAccessToken(token));
    }

    @Test
    public void testFromSettings_readsDataoneAuthSecretsFile(@TempDir Path tempDir) throws Exception {
        Path secrets = tempDir.resolve("client_secrets.json");
        Files.writeString(secrets, "{\"client_id\": \"file-client\", "
            + "\"client_secret\": \"file-secret\", "
            + "\"server_metadata_url\": \"https://auth.example.org/realms/dataone/"
            + ".well-known/openid-configuration\"}");
        Settings.getConfiguration().setProperty(KeycloakProvider.SECRETS_FILE, secrets.toString());
        Settings.getConfiguration().setProperty(KeycloakProvider.REDIRECT_URI,
                                                TestKeycloak.REDIRECT_URI);
        try {
            KeycloakProvider fromFile = KeycloakProvider.fromSettings();

            assertTrue(fromFile.isEnabled(), "a metadata URL alone enables Keycloak");
            assertTrue(fromFile.isLoginConfigured());
            assertEquals("file-client", fromFile.getClientID().getValue());
            assertEquals("file-secret", fromFile.getClientSecret());
            assertEquals(Arrays.asList("file-client"), fromFile.getExchangeAudiences());
        } finally {
            Settings.getConfiguration().clearProperty(KeycloakProvider.SECRETS_FILE);
            Settings.getConfiguration().clearProperty(KeycloakProvider.REDIRECT_URI);
        }
    }

    @Test
    public void testFromSettings_settingsOverrideSecretsFile(@TempDir Path tempDir) throws Exception {
        Path secrets = tempDir.resolve("client_secrets.json");
        Files.writeString(secrets, "{\"client_id\": \"file-client\", "
            + "\"client_secret\": \"file-secret\"}");
        Settings.getConfiguration().setProperty(KeycloakProvider.SECRETS_FILE, secrets.toString());
        Settings.getConfiguration().setProperty(KeycloakProvider.CLIENT_ID, "settings-client");
        try {
            KeycloakProvider provider = KeycloakProvider.fromSettings();

            assertEquals("settings-client", provider.getClientID().getValue());
            assertEquals("file-secret", provider.getClientSecret());
            assertFalse(provider.isEnabled(), "no issuer or metadata URL is configured");
        } finally {
            Settings.getConfiguration().clearProperty(KeycloakProvider.SECRETS_FILE);
            Settings.getConfiguration().clearProperty(KeycloakProvider.CLIENT_ID);
        }
    }
}
