package org.dataone.portal.servlets.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.TestKeycloak;
import org.dataone.portal.servlets.FakeHttpSession;
import org.dataone.portal.session.PortalSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

/**
 * Tests for {@link KeycloakOidcServlet} (/login and /authorize), with a fake Keycloak realm and
 * the token endpoint replaced by a stub.
 */
public class KeycloakOidcServletTest {

    private static final String TARGET = "https://search.dataone.org/";

    /** Uses the fake realm and answers token requests with tokens signed by its key */
    private static class StubKeycloakOidcServlet extends KeycloakOidcServlet {
        private static final long serialVersionUID = 1L;
        final TestKeycloak keycloak;
        final KeycloakProvider provider;
        /** claims for the ID token returned at the token endpoint */
        JWTClaimsSet idTokenClaims;
        /** if set, the token endpoint answers with this error */
        TokenErrorResponse tokenError;
        TokenRequest tokenRequest;
        String registered;

        StubKeycloakOidcServlet(TestKeycloak keycloak, KeycloakProvider provider) {
            this.keycloak = keycloak;
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
            try {
                return new OIDCTokenResponse(new OIDCTokens(
                    keycloak.sign(idTokenClaims),
                    new BearerAccessToken("keycloak-access-token", 300, null),
                    new RefreshToken("keycloak-refresh-token")));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected void registerAccount(String userId, String givenName, String familyName) {
            registered = userId + "|" + givenName + "|" + familyName;
        }
    }

    private TestKeycloak keycloak;
    private StubKeycloakOidcServlet servlet;
    private FakeHttpSession httpSession;
    private HttpServletResponse response;
    private StringWriter body;

    @BeforeEach
    public void setUp() throws Exception {
        keycloak = new TestKeycloak();
        servlet = new StubKeycloakOidcServlet(keycloak, keycloak.provider());
        httpSession = new FakeHttpSession("session-before-login");
        response = newResponse();
    }

    private HttpServletResponse newResponse() throws Exception {
        HttpServletResponse newResponse = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(newResponse.getWriter()).thenReturn(new PrintWriter(body, true));
        return newResponse;
    }

    private HttpServletRequest loginRequest(String target) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn(KeycloakOidcServlet.LOGIN_PATH);
        when(request.getParameter("target")).thenReturn(target);
        when(request.getSession(true)).thenReturn(httpSession);
        return request;
    }

    private HttpServletRequest authorizeRequest(String query) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn(KeycloakOidcServlet.AUTHORIZE_PATH);
        when(request.getQueryString()).thenReturn(query);
        when(request.getSession(false)).thenReturn(httpSession);
        when(request.changeSessionId()).thenAnswer(invocation -> {
            httpSession.setId("session-after-login");
            return "session-after-login";
        });
        return request;
    }

    private static Map<String, String> queryParams(String location) {
        Map<String, String> params = new HashMap<String, String>();
        for (String pair : URI.create(location).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    /** Run the login step and return the parameters sent to Keycloak */
    private Map<String, String> login(String target) throws Exception {
        HttpServletResponse loginResponse = mock(HttpServletResponse.class);
        servlet.doGet(loginRequest(target), loginResponse);
        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(loginResponse).sendRedirect(location.capture());
        assertTrue(location.getValue().startsWith(TestKeycloak.AUTHORIZATION_ENDPOINT + "?"));
        return queryParams(location.getValue());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error() throws Exception {
        return (Map<String, Object>) JSONObjectUtils.parse(body.toString()).get("error");
    }

    @Test
    public void testDoGet_unavailableWhenNotConfigured() throws Exception {
        StubKeycloakOidcServlet unconfigured =
            new StubKeycloakOidcServlet(keycloak, KeycloakProvider.disabled());

        unconfigured.doGet(loginRequest(TARGET), response);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertEquals("OIDC login is not configured", error().get("message"));
    }

    @Test
    public void testDoGet_unknownPathIsNotFound() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/oidc");

        servlet.doGet(request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testLogin_redirectsToKeycloakWithStateNonceAndPkce() throws Exception {
        Map<String, String> params = login(TARGET);

        assertEquals("code", params.get("response_type"));
        assertEquals(TestKeycloak.CLIENT_ID, params.get("client_id"));
        assertEquals(TestKeycloak.REDIRECT_URI, params.get("redirect_uri"));
        assertEquals("openid profile email", params.get("scope"));
        assertEquals(params.get("state"), httpSession.getAttribute(PortalSession.OAUTH_STATE));
        assertNotEquals("session-before-login", params.get("state"));
        assertEquals(params.get("nonce"), httpSession.getAttribute(PortalSession.OIDC_NONCE));
        assertEquals("S256", params.get("code_challenge_method"));
        CodeVerifier verifier =
            new CodeVerifier((String) httpSession.getAttribute(PortalSession.PKCE_VERIFIER));
        assertEquals(CodeChallenge.compute(CodeChallengeMethod.S256, verifier).getValue(),
                     params.get("code_challenge"));
        assertEquals(TARGET, httpSession.getAttribute(PortalSession.TARGET));
    }

    @Test
    public void testLogin_rejectsTargetOutsideAllowlist() throws Exception {
        servlet.doGet(loginRequest("https://evil.example/"), response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("Invalid target", error().get("details"));
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void testAuthorize_withTargetStoresLoginAndRedirects() throws Exception {
        Map<String, String> params = login(TARGET);
        String verifier = (String) httpSession.getAttribute(PortalSession.PKCE_VERIFIER);
        servlet.idTokenClaims = keycloak.idTokenClaims(params.get("nonce")).build();

        servlet.doGet(authorizeRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).sendRedirect(TARGET);
        AuthorizationCodeGrant grant =
            (AuthorizationCodeGrant) servlet.tokenRequest.getAuthorizationGrant();
        assertEquals("the-code", grant.getAuthorizationCode().getValue());
        assertEquals(verifier, grant.getCodeVerifier().getValue());
        assertEquals(URI.create(TestKeycloak.REDIRECT_URI), grant.getRedirectionURI());
        assertEquals("session-after-login", httpSession.getId());
        assertEquals(PortalSession.SOURCE_KEYCLOAK,
                     httpSession.getAttribute(PortalSession.AUTH_SOURCE));
        assertEquals(TestKeycloak.ORCID, httpSession.getAttribute(PortalSession.USER_ID));
        assertEquals("Test User", httpSession.getAttribute(PortalSession.NAME));
        assertEquals("keycloak-access-token",
                     httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
        assertEquals("keycloak-refresh-token",
                     httpSession.getAttribute(PortalSession.REFRESH_TOKEN));
        assertNotNull(httpSession.getAttribute(PortalSession.ID_TOKEN));
        assertNull(httpSession.getAttribute(PortalSession.OAUTH_STATE));
        assertNull(httpSession.getAttribute(PortalSession.OIDC_NONCE));
        assertNull(httpSession.getAttribute(PortalSession.PKCE_VERIFIER));
        assertEquals(TestKeycloak.ORCID + "|Test|User", servlet.registered);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testAuthorize_withoutTargetReturnsDataoneAuthTokenPayload() throws Exception {
        Map<String, String> params = login(null);
        servlet.idTokenClaims = keycloak.idTokenClaims(params.get("nonce")).build();

        servlet.doGet(authorizeRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        Map<String, Object> payload = JSONObjectUtils.parse(body.toString());
        assertEquals("Success", payload.get("message"));
        Map<String, Object> token = (Map<String, Object>) payload.get("token");
        assertEquals("keycloak-access-token", token.get("access_token"));
        assertEquals("keycloak-refresh-token", token.get("refresh_token"));
        assertEquals(TestKeycloak.ORCID, httpSession.getAttribute(PortalSession.USER_ID));
    }

    @Test
    public void testAuthorize_rejectsMismatchedState() throws Exception {
        login(TARGET);

        servlet.doGet(authorizeRequest("code=the-code&state=forged"), response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("Invalid or expired login request", error().get("details"));
        assertNull(servlet.tokenRequest, "the code must not be exchanged");
    }

    @Test
    public void testAuthorize_wrongNonceWithTargetRedirectsWithError() throws Exception {
        Map<String, String> params = login(TARGET);
        servlet.idTokenClaims = keycloak.idTokenClaims("some-other-nonce").build();

        servlet.doGet(authorizeRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).sendRedirect(TARGET + "?error=login_failed");
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }

    @Test
    public void testAuthorize_wrongNonceWithoutTargetIs401() throws Exception {
        Map<String, String> params = login(null);
        servlet.idTokenClaims = keycloak.idTokenClaims("some-other-nonce").build();

        servlet.doGet(authorizeRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("Token validation failed", error().get("message"));
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }

    @Test
    public void testAuthorize_rejectsIdTokenWithoutSubjectClaim() throws Exception {
        Map<String, String> params = login(TARGET);
        servlet.idTokenClaims =
            keycloak.idTokenClaims(params.get("nonce")).claim("orcid", null).build();

        servlet.doGet(authorizeRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).sendRedirect(TARGET + "?error=login_failed");
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }

    @Test
    public void testAuthorize_keycloakErrorRedirectsWithError() throws Exception {
        Map<String, String> params = login(TARGET);

        servlet.doGet(authorizeRequest("error=access_denied&state=" + params.get("state")),
                      response);

        verify(response).sendRedirect(TARGET + "?error=login_failed");
        assertNull(servlet.tokenRequest);
    }

    @Test
    public void testAuthorize_rejectedCodeWithoutTargetIs401() throws Exception {
        Map<String, String> params = login(null);
        servlet.tokenError = new TokenErrorResponse(OAuth2Error.INVALID_GRANT);

        servlet.doGet(authorizeRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertEquals("Authorization failed", error().get("message"));
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }
}
