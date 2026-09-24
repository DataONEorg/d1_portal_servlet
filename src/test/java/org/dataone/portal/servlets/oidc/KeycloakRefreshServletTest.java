package org.dataone.portal.servlets.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.TestKeycloak;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.token.Tokens;

/**
 * Tests for {@link KeycloakRefreshServlet}, with the token endpoint replaced by a stub.
 */
public class KeycloakRefreshServletTest {

    /** Records the token request and answers with new tokens or a configured error */
    private static class StubRefreshServlet extends KeycloakRefreshServlet {
        private static final long serialVersionUID = 1L;
        final KeycloakProvider provider;
        TokenErrorResponse tokenError;
        TokenRequest tokenRequest;

        StubRefreshServlet(KeycloakProvider provider) {
            this.provider = provider;
        }

        @Override
        protected KeycloakProvider getProvider() {
            return provider;
        }

        @Override
        protected TokenResponse sendTokenRequest(TokenRequest request) {
            tokenRequest = request;
            if (tokenError != null) {
                return tokenError;
            }
            return new AccessTokenResponse(new Tokens(
                new BearerAccessToken("new-access-token", 300, null),
                new RefreshToken("new-refresh-token")));
        }
    }

    private StubRefreshServlet servlet;
    private HttpServletResponse response;
    private StringWriter body;

    @BeforeEach
    public void setUp() throws Exception {
        servlet = new StubRefreshServlet(new TestKeycloak().provider());
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
    }

    private static HttpServletRequest post(String json) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(json)));
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(String key) throws Exception {
        return (Map<String, Object>) JSONObjectUtils.parse(body.toString()).get(key);
    }

    @Test
    public void testRefresh_returnsNewTokens() throws Exception {
        servlet.doPost(post("{\"refresh_token\": \"old-refresh-token\", \"scope\": \"openid\"}"),
                       response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertEquals("Token refresh successful",
                     JSONObjectUtils.parse(body.toString()).get("message"));
        assertEquals("new-access-token", json("token").get("access_token"));
        assertEquals("new-refresh-token", json("token").get("refresh_token"));

        RefreshTokenGrant grant = (RefreshTokenGrant) servlet.tokenRequest.getAuthorizationGrant();
        assertEquals("old-refresh-token", grant.getRefreshToken().getValue());
        assertEquals("openid", servlet.tokenRequest.getScope().toString());
        ClientSecretBasic auth = (ClientSecretBasic) servlet.tokenRequest.getClientAuthentication();
        assertEquals(TestKeycloak.CLIENT_ID, auth.getClientID().getValue());
        assertEquals(TestKeycloak.CLIENT_SECRET, auth.getClientSecret().getValue());
    }

    @Test
    public void testRefresh_withoutScope() throws Exception {
        servlet.doPost(post("{\"refresh_token\": \"old-refresh-token\"}"), response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertNull(servlet.tokenRequest.getScope());
    }

    @Test
    public void testRefresh_missingRefreshTokenIs401() throws Exception {
        servlet.doPost(post("{\"scope\": \"openid\"}"), response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("Invalid token or header", json("error").get("message"));
        assertEquals("Missing refresh_token", json("error").get("details"));
        assertNull(servlet.tokenRequest);
    }

    @Test
    public void testRefresh_expiredRefreshTokenIs401() throws Exception {
        servlet.tokenError = new TokenErrorResponse(OAuth2Error.INVALID_GRANT);

        servlet.doPost(post("{\"refresh_token\": \"expired\"}"), response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("Invalid or expired refresh token", json("error").get("message"));
    }

    @Test
    public void testRefresh_rejectedClientIs401() throws Exception {
        servlet.tokenError = new TokenErrorResponse(OAuth2Error.INVALID_CLIENT);

        servlet.doPost(post("{\"refresh_token\": \"old-refresh-token\"}"), response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("OIDC client authentication failed", json("error").get("message"));
    }

    @Test
    public void testRefresh_malformedBodyIs400() throws Exception {
        servlet.doPost(post("refresh_token=old"), response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertTrue(((String) json("error").get("details")).contains("JSON object"));
    }

    @Test
    public void testRefresh_unavailableWhenNotConfigured() throws Exception {
        StubRefreshServlet unconfigured = new StubRefreshServlet(KeycloakProvider.disabled());

        unconfigured.doPost(post("{\"refresh_token\": \"old-refresh-token\"}"), response);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
