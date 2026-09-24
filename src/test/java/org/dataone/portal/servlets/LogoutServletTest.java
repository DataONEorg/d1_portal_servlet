package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.ServletConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dataone.portal.session.SessionHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests for {@link LogoutServlet}, with the Hazelcast-backed {@link SessionHelper} replaced by a
 * mock.
 */
public class LogoutServletTest {

    private static final String SESSION_ID = "test-session-id";
    private static final String TARGET = "https://search.dataone.org/";

    private MockedStatic<SessionHelper> sessionHelperStatic;
    private SessionHelper sessionHelper;
    private LogoutServlet servlet;

    @BeforeEach
    public void setUp() throws Exception {
        sessionHelper = mock(SessionHelper.class);
        sessionHelperStatic = Mockito.mockStatic(SessionHelper.class);
        sessionHelperStatic.when(SessionHelper::getInstance).thenReturn(sessionHelper);

        servlet = new LogoutServlet();
        servlet.init(mock(ServletConfig.class));
    }

    @AfterEach
    public void tearDown() {
        sessionHelperStatic.close();
    }

    @Test
    public void testDoGet_removesSessionAndCookieAndRedirects() throws Exception {
        HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getId()).thenReturn(SESSION_ID);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(httpSession);
        when(request.getParameter("target")).thenReturn(TARGET);
        HttpServletResponse response = mock(HttpServletResponse.class);

        servlet.doGet(request, response);

        verify(sessionHelper).removeSession(SESSION_ID);

        // the portal certificate cookie is expired immediately
        ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
        verify(response).addCookie(cookie.capture());
        assertEquals(0, cookie.getValue().getMaxAge());
        assertEquals("/", cookie.getValue().getPath());

        verify(response).sendRedirect(TARGET);
    }
}
