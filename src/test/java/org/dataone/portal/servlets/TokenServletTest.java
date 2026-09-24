package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.TokenGenerator;
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
    private TokenServlet servlet;

    @BeforeEach
    public void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        out = new ByteArrayServletOutputStream();
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(out);
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
}
