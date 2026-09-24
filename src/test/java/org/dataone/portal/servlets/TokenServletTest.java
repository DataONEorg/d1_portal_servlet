package org.dataone.portal.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dataone.portal.TokenGenerator;
import org.dataone.portal.session.SessionHelper;
import org.dataone.service.types.v1.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests for {@link TokenServlet}. The Hazelcast-backed {@link SessionHelper} is replaced with a
 * mock; {@link TokenGenerator} runs for real with the test key and cert configured in
 * src/test/resources/org/dataone/configuration/portal.properties.
 */
public class TokenServletTest {

    private static final String SESSION_ID = "test-session-id";
    private static final String USER_ID = "http://orcid.org/0000-0000-0000-0001";
    private static final String NAME = "Test User";

    private MockedStatic<SessionHelper> sessionHelperStatic;
    private SessionHelper sessionHelper;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayServletOutputStream out;
    private TokenServlet servlet;

    @BeforeEach
    public void setUp() throws Exception {
        sessionHelper = mock(SessionHelper.class);
        sessionHelperStatic = Mockito.mockStatic(SessionHelper.class);
        sessionHelperStatic.when(SessionHelper::getInstance).thenReturn(sessionHelper);

        HttpSession httpSession = mock(HttpSession.class);
        when(httpSession.getId()).thenReturn(SESSION_ID);
        request = mock(HttpServletRequest.class);
        when(request.getSession()).thenReturn(httpSession);

        out = new ByteArrayServletOutputStream();
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(out);

        servlet = new TokenServlet();
        servlet.init(mock(ServletConfig.class));
    }

    @AfterEach
    public void tearDown() {
        sessionHelperStatic.close();
    }

    @Test
    public void testDoGet_returnsJWTForLoggedInSession() throws Exception {
        Map<String, Object> sessionMap = new HashMap<String, Object>();
        sessionMap.put("accessToken", "orcid-access-token");
        sessionMap.put("userId", USER_ID);
        sessionMap.put("name", NAME);
        when(sessionHelper.getMap(SESSION_ID)).thenReturn(sessionMap);

        servlet.doGet(request, response);

        String token = out.getContent();
        Session session = TokenGenerator.getInstance().getSession(token);
        assertNotNull(session, "the returned token should verify with the test cert");
        assertEquals(USER_ID, session.getSubject().getValue());
    }

    @Test
    public void testDoGet_returnsEmptyBodyWithoutAccessToken() throws Exception {
        Map<String, Object> sessionMap = new HashMap<String, Object>();
        sessionMap.put("target", "https://search.dataone.org/");
        when(sessionHelper.getMap(SESSION_ID)).thenReturn(sessionMap);

        servlet.doGet(request, response);

        assertEquals("", out.getContent());
    }

    @Test
    public void testDoGet_returnsEmptyBodyForUnknownSession() throws Exception {
        when(sessionHelper.getMap(SESSION_ID)).thenReturn(null);

        servlet.doGet(request, response);

        assertEquals("", out.getContent());
    }
}
