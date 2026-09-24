package org.dataone.portal.servlets.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
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

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.servlets.FakeHttpSession;
import org.dataone.portal.session.PortalSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link OrcidOAuthServlet}, with the ORCID token exchange and CN account registration
 * replaced by a stub subclass.
 */
public class OrcidOAuthServletTest {

    private static final String SESSION_ID = "session-before-login";
    private static final String TARGET = "https://search.dataone.org/";
    private static final String ORCID = "0000-0000-0000-0001";

    /** Records calls instead of contacting ORCID or the CN */
    private static class StubOrcidOAuthServlet extends OrcidOAuthServlet {
        String codeRequested;
        String registeredOrcid;

        @Override
        protected OrcidToken requestAccessToken(String code) {
            codeRequested = code;
            OrcidToken token = new OrcidToken();
            token.accessToken = "orcid-access-token";
            token.expiresIn = 3600L;
            token.scope = "/authenticate";
            token.orcid = ORCID;
            token.name = "Test User";
            return token;
        }

        @Override
        protected void registerAccount(String orcid, String name) {
            registeredOrcid = orcid;
        }
    }

    private StubOrcidOAuthServlet servlet;
    private FakeHttpSession httpSession;
    private HttpServletResponse response;

    @BeforeEach
    public void setUp() throws Exception {
        servlet = new StubOrcidOAuthServlet();
        servlet.init(mock(ServletConfig.class));
        httpSession = new FakeHttpSession(SESSION_ID);
        response = mock(HttpServletResponse.class);
    }

    private HttpServletRequest startRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("action")).thenReturn("start");
        when(request.getParameter("target")).thenReturn(TARGET);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("cn.example.org");
        when(request.getServerPort()).thenReturn(443);
        when(request.getRequestURI()).thenReturn("/portal/oauth");
        when(request.getSession(true)).thenReturn(httpSession);
        return request;
    }

    private HttpServletRequest callbackRequest(String state) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        // Oltu reads the callback parameters through getParameterMap()
        Map<String, String[]> params = new HashMap<String, String[]>();
        params.put("code", new String[] {"the-auth-code"});
        params.put("state", new String[] {state});
        when(request.getParameterMap()).thenReturn(params);
        when(request.getParameter("code")).thenReturn("the-auth-code");
        when(request.getParameter("state")).thenReturn(state);
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

    /** Run the start step and return the state sent to ORCID */
    private String start() throws Exception {
        servlet.doGet(startRequest(), response);
        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(location.capture());
        return queryParams(location.getValue()).get("state");
    }

    @Test
    public void testStart_redirectsToOrcidWithRandomState() throws Exception {
        servlet.doGet(startRequest(), response);

        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(location.capture());
        Map<String, String> params = queryParams(location.getValue());

        assertTrue(location.getValue().startsWith("https://sandbox.orcid.org/oauth/authorize?"));
        assertEquals("APP-TEST-CLIENT-ID", params.get("client_id"));
        assertEquals("https://cn.example.org/portal/oauth", params.get("redirect_uri"));
        assertNotNull(params.get("state"));
        assertNotEquals(SESSION_ID, params.get("state"), "the session id must not be sent to ORCID");
        assertEquals(params.get("state"), httpSession.getAttribute(PortalSession.OAUTH_STATE));
        assertEquals(TARGET, httpSession.getAttribute(PortalSession.TARGET));
    }

    @Test
    public void testCallback_storesLoginAndRedirectsToTarget() throws Exception {
        String state = start();
        HttpServletResponse callbackResponse = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest(state), callbackResponse);

        assertEquals("the-auth-code", servlet.codeRequested);
        assertEquals("session-after-login", httpSession.getId(), "session id should change on login");
        assertEquals("orcid-access-token", httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
        assertEquals("http://orcid.org/" + ORCID, httpSession.getAttribute(PortalSession.USER_ID));
        assertEquals("Test User", httpSession.getAttribute(PortalSession.NAME));
        assertNull(httpSession.getAttribute(PortalSession.OAUTH_STATE), "state is single use");
        assertEquals("http://orcid.org/" + ORCID, servlet.registeredOrcid);
        verify(callbackResponse).sendRedirect(TARGET);
    }

    @Test
    public void testCallback_rejectsMismatchedState() throws Exception {
        start();
        HttpServletResponse callbackResponse = mock(HttpServletResponse.class);

        servlet.doGet(callbackRequest("forged-state"), callbackResponse);

        verify(callbackResponse).sendError(HttpServletResponse.SC_BAD_REQUEST,
                                           "Invalid or expired login request");
        assertNull(servlet.codeRequested, "the code must not be exchanged");
        assertNull(httpSession.getAttribute(PortalSession.ACCESS_TOKEN));
    }

    @Test
    public void testCallback_rejectsMissingSession() throws Exception {
        HttpServletRequest request = callbackRequest("any-state");
        when(request.getSession(false)).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(anyInt(), anyString());
        verify(response, never()).sendRedirect(anyString());
        assertNull(servlet.codeRequested);
    }
}
