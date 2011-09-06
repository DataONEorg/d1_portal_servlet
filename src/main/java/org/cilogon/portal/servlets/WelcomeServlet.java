package org.cilogon.portal.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * <p>Created by Jeff Gaynor<br>
 * on Aug 11, 2010 at  10:15:29 AM
 */
public class WelcomeServlet extends PortalAbstractServlet {
    protected void doIt(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Throwable {
        PrintWriter writer = httpServletResponse.getWriter();
        httpServletResponse.setContentType("text/html");
        writer.println("<html><head><title>DataONE Authentication</title></head>");
        writer.println(" <body><h1>DataONE Authentication</h1>");
        // Next line is the important one. Just set the context path and point this to the the startRequest servlet
        writer.println("<a href=\"" + httpServletRequest.getContextPath() + "/startRequest\">");
        writer.println("Login");
        writer.println("</a></body></html>");
    }
}
