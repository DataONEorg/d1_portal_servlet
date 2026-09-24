package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.TokenGenerator;
import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.TestKeycloak;
import org.dataone.portal.session.PortalSession;
import org.dataone.service.types.v1.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TokenServlet}. {@link TokenGenerator} runs for real with the test key and cert
 * configured in src/test/resources/org/dataone/configuration/portal.properties.
 */
public class TokenServletTest {

    private static final String USER_ID = "http://orcid.org/0000-0000-0000-0001";
    private static final String NAME = "Test User";

    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayServletOutputStream out;
    private StringWriter body;
    private TokenServlet servlet;

    @BeforeEach
    public void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        out = new ByteArrayServletOutputStream();
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(out);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        servlet = new TokenServlet();
    }

    @Test
    public void testDoGet_returnsJWTForLoggedInSession() throws Exception {
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        httpSession.setAttribute(PortalSession.ACCESS_TOKEN, "orcid-access-token");
        httpSession.setAttribute(PortalSession.USER_ID, USER_ID);
        httpSession.setAttribute(PortalSession.NAME, NAME);
        when(request.getSession(false)).thenReturn(httpSession);

        servlet.doGet(request, response);

        String token = out.getContent();
        Session session = TokenGenerator.getInstance().getSession(token);
        assertNotNull(session, "the returned token should verify with the test cert");
        assertEquals(USER_ID, session.getSubject().getValue());
    }

    @Test
    public void testDoGet_returnsEmptyBodyWithoutAccessToken() throws Exception {
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        httpSession.setAttribute(PortalSession.TARGET, "https://search.dataone.org/");
        when(request.getSession(false)).thenReturn(httpSession);

        servlet.doGet(request, response);

        assertEquals("", out.getContent());
    }

    @Test
    public void testDoGet_returnsEmptyBodyWithoutCreatingSession() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        servlet.doGet(request, response);

        assertEquals("", out.getContent());
        verify(request, never()).getSession();
        verify(request, never()).getSession(true);
    }

    /** A TokenServlet using the fake Keycloak realm */
    private static TokenServlet tokenServlet(KeycloakProvider provider) {
        return new TokenServlet() {
            @Override
            protected KeycloakProvider getProvider() {
                return provider;
            }
        };
    }

    @Test
    public void testDoGet_exchangesKeycloakAccessToken() throws Exception {
        TestKeycloak keycloak = new TestKeycloak();
        String accessToken = keycloak.sign(keycloak.accessTokenClaims().build()).serialize();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + accessToken);

        tokenServlet(keycloak.provider()).doGet(request, response);

        Session session = TokenGenerator.getInstance().getSession(out.getContent());
        assertNotNull(session, "the returned DataONE JWT should verify with the test cert");
        assertEquals(TestKeycloak.ORCID, session.getSubject().getValue());
        verify(request, never()).getSession(false);
    }

    @Test
    public void testDoGet_rejectsExpiredKeycloakAccessToken() throws Exception {
        TestKeycloak keycloak = new TestKeycloak();
        String accessToken = keycloak.sign(keycloak.accessTokenClaims()
            .expirationTime(new Date(System.currentTimeMillis() - 60 * 60 * 1000)).build())
            .serialize();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + accessToken);

        tokenServlet(keycloak.provider()).doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        assertEquals("", out.getContent());
        assertTrue(body.toString().startsWith("{\"error\":{\"message\":\"Token validation failed\""),
                   body.toString());
    }

    @Test
    public void testDoGet_rejectsKeycloakIdToken() throws Exception {
        TestKeycloak keycloak = new TestKeycloak();
        String idToken = keycloak.sign(keycloak.idTokenClaims("nonce").build()).serialize();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + idToken);

        tokenServlet(keycloak.provider()).doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testDoGet_ignoresOtherBearerTokensAndUsesSession() throws Exception {
        TestKeycloak keycloak = new TestKeycloak();
        // e.g. a client sending its current DataONE JWT while renewing
        String dataoneJwt = TokenGenerator.getInstance().getJWT("someone-else", "Someone Else");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + dataoneJwt);
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        httpSession.setAttribute(PortalSession.ACCESS_TOKEN, "orcid-access-token");
        httpSession.setAttribute(PortalSession.USER_ID, USER_ID);
        httpSession.setAttribute(PortalSession.NAME, NAME);
        when(request.getSession(false)).thenReturn(httpSession);

        tokenServlet(keycloak.provider()).doGet(request, response);

        Session session = TokenGenerator.getInstance().getSession(out.getContent());
        assertEquals(USER_ID, session.getSubject().getValue());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testDoGet_ignoresBearerTokensWhenKeycloakIsNotConfigured() throws Exception {
        TestKeycloak keycloak = new TestKeycloak();
        String accessToken = keycloak.sign(keycloak.accessTokenClaims().build()).serialize();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + accessToken);
        when(request.getSession(false)).thenReturn(null);

        tokenServlet(KeycloakProvider.disabled())
            .doGet(request, response);

        assertEquals("", out.getContent());
        verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    public void testDoGet_rejectsOverlongBearerToken() throws Exception {
        when(request.getHeader("Authorization"))
            .thenReturn("Bearer " + "a".repeat(KeycloakProvider.MAX_TOKEN_LENGTH + 1));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertTrue(body.toString().contains("Token exceeds maximum allowed length"));
    }
}
