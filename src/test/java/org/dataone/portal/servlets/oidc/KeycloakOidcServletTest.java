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

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.pkce.CodeChallenge;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

/**
 * Tests for {@link KeycloakOidcServlet}, with a fake Keycloak realm and the token endpoint call
 * replaced by a stub.
 */
public class KeycloakOidcServletTest {

    private static final String TARGET = "https://search.dataone.org/";

    /** Uses the fake realm and answers token requests with tokens signed by its key */
    private static class StubKeycloakOidcServlet extends KeycloakOidcServlet {
        final TestKeycloak keycloak;
        final KeycloakProvider provider;
        /** claims for the ID token returned at the token endpoint; null means use the nonce */
        JWTClaimsSet idTokenClaims;
        AuthorizationCode codeRequested;
        CodeVerifier verifierSent;
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
        protected OIDCTokens requestTokens(KeycloakProvider provider, AuthorizationCode code,
                                           CodeVerifier verifier) throws Exception {
            codeRequested = code;
            verifierSent = verifier;
            return new OIDCTokens(keycloak.sign(idTokenClaims),
                                  new BearerAccessToken("keycloak-access-token", 300, null),
                                  new RefreshToken("keycloak-refresh-token"));
        }

        @Override
        protected void registerAccount(String userId, String givenName, String familyName) {
            registered = userId + "|" + givenName + "|" + familyName;
        }
    }

    private TestKeycloak keycloak;
    private StubKeycloakOidcServlet servlet;
    private FakeHttpSession httpSession;

    @BeforeEach
    public void setUp() throws Exception {
        keycloak = new TestKeycloak();
        servlet = new StubKeycloakOidcServlet(keycloak, keycloak.provider());
        httpSession = new FakeHttpSession("session-before-login");
    }

    private HttpServletRequest startRequest(String target) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("action")).thenReturn("start");
        when(request.getParameter("target")).thenReturn(target);
        when(request.getSession(true)).thenReturn(httpSession);
        return request;
    }

    private HttpServletRequest callbackRequest(String query) {
        HttpServletRequest request = mock(HttpServletRequest.class);
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

    /** Run the start step and return the parameters sent to Keycloak */
    private Map<String, String> start(String target) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        servlet.doGet(startRequest(target), response);
        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(location.capture());
        assertTrue(location.getValue().startsWith(TestKeycloak.AUTHORIZATION_ENDPOINT + "?"));
        return queryParams(location.getValue());
    }

    @Test
    public void testDoGet_unavailableWhenNotConfigured() throws Exception {
        KeycloakProvider unconfigured =
            new KeycloakProvider(null, "d1-confidential", null, null, null, null, null);
        StubKeycloakOidcServlet unconfiguredServlet =
            new StubKeycloakOidcServlet(keycloak, unconfigured);
        HttpServletResponse response = mock(HttpServletResponse.class);

        unconfiguredServlet.doGet(startRequest(TARGET), response);

        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                                   "Keycloak login is not configured");
    }

    @Test
    public void testStart_redirectsToKeycloakWithStateNonceAndPkce() throws Exception {
        Map<String, String> params = start(TARGET);

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
    public void testStart_rejectsTargetOutsideAllowlist() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(startRequest("https://evil.example/"), response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid target");
        verify(response, never()).sendRedirect(anyString());
    }

    @Test
    public void testCallback_storesLoginAndRedirectsToTarget() throws Exception {
        Map<String, String> params = start(TARGET);
        String verifier = (String) httpSession.getAttribute(PortalSession.PKCE_VERIFIER);
        servlet.idTokenClaims = keycloak.idTokenClaims(params.get("nonce")).build();
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).sendRedirect(TARGET);
        assertEquals("the-code", servlet.codeRequested.getValue());
        assertEquals(verifier, servlet.verifierSent.getValue());
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
    public void testCallback_rejectsMismatchedState() throws Exception {
        start(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest("code=the-code&state=forged"), response);

        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST,
                                   "Invalid or expired login request");
        assertNull(servlet.codeRequested, "the code must not be exchanged");
    }

    @Test
    public void testCallback_rejectsWrongNonce() throws Exception {
        Map<String, String> params = start(TARGET);
        servlet.idTokenClaims = keycloak.idTokenClaims("some-other-nonce").build();
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).sendRedirect(TARGET + "?error=login_failed");
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }

    @Test
    public void testCallback_rejectsIdTokenWithoutSubjectClaim() throws Exception {
        Map<String, String> params = start(TARGET);
        servlet.idTokenClaims = keycloak.idTokenClaims(params.get("nonce"))
            .claim("preferred_username", null).build();
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest("code=the-code&state=" + params.get("state")), response);

        verify(response).sendRedirect(TARGET + "?error=login_failed");
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }

    @Test
    public void testCallback_keycloakErrorRedirectsToTargetWithError() throws Exception {
        Map<String, String> params = start(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest("error=access_denied&state=" + params.get("state")),
                      response);

        verify(response).sendRedirect(TARGET + "?error=login_failed");
        assertNull(servlet.codeRequested);
    }
}
