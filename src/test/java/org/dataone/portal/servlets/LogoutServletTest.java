package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;

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
}
