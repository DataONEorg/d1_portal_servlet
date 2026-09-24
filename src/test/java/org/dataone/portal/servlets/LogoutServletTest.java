package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.TestKeycloak;
import org.dataone.portal.session.PortalSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link LogoutServlet}.
 */
public class LogoutServletTest {

    private static final String TARGET = "https://search.dataone.org/";

    @Test
    public void testDoGet_invalidatesSessionAndRedirects() throws Exception {
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(httpSession);
        when(request.getParameter("target")).thenReturn(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new LogoutServlet().doGet(request, response);

        assertTrue(httpSession.isInvalidated());

        verify(response).sendRedirect(TARGET);
    }

    @Test
    public void testDoGet_redirectsWhenThereIsNoSession() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getParameter("target")).thenReturn(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        new LogoutServlet().doGet(request, response);

        verify(response).sendRedirect(TARGET);
    }

    @Test
    public void testDoGet_rejectsTargetOutsideAllowlistButStillLogsOut() throws Exception {
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(httpSession);
        when(request.getParameter("target")).thenReturn("https://evil.example/");
        HttpServletResponse response = mock(HttpServletResponse.class);

        new LogoutServlet().doGet(request, response);

        assertTrue(httpSession.isInvalidated());
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid target");
        verify(response, never()).sendRedirect(anyString());
    }

    /** A LogoutServlet using the given Keycloak provider */
    private static LogoutServlet logoutServlet(KeycloakProvider provider) {
        return new LogoutServlet() {
            @Override
            protected KeycloakProvider getProvider() {
                return provider;
            }
        };
    }

    @Test
    public void testDoGet_keycloakLoginEndsKeycloakSessionToo() throws Exception {
        TestKeycloak keycloak = new TestKeycloak();
        String idToken = keycloak.sign(keycloak.idTokenClaims("nonce").build()).serialize();
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        httpSession.setAttribute(PortalSession.AUTH_SOURCE, PortalSession.SOURCE_KEYCLOAK);
        httpSession.setAttribute(PortalSession.ID_TOKEN, idToken);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(httpSession);
        when(request.getParameter("target")).thenReturn(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        logoutServlet(keycloak.provider()).doGet(request, response);

        assertTrue(httpSession.isInvalidated());
        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(location.capture());
        URI uri = URI.create(location.getValue());
        assertEquals(TestKeycloak.END_SESSION_ENDPOINT,
                     uri.getScheme() + "://" + uri.getHost() + uri.getPath());
        assertTrue(uri.getQuery().contains("id_token_hint=" + idToken));
        assertTrue(uri.getQuery().contains("post_logout_redirect_uri=" + TARGET));
    }

    @Test
    public void testDoGet_keycloakLoginWithoutKeycloakConfigRedirectsToTarget() throws Exception {
        FakeHttpSession httpSession = new FakeHttpSession("test-session-id");
        httpSession.setAttribute(PortalSession.AUTH_SOURCE, PortalSession.SOURCE_KEYCLOAK);
        httpSession.setAttribute(PortalSession.ID_TOKEN, "an-id-token");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(httpSession);
        when(request.getParameter("target")).thenReturn(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        logoutServlet(new KeycloakProvider(null, "d1-confidential", null, null, null, null, null))
            .doGet(request, response);

        verify(response).sendRedirect(TARGET);
    }
}
