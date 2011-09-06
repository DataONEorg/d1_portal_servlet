package org.dataone.portal.servlets;

import org.cilogon.portal.CILogonService;
import org.cilogon.portal.servlets.PortalAbstractServlet;
import org.cilogon.portal.util.PortalCredentials;
import org.dataone.portal.PortalCertificateManager;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static edu.uiuc.ncsa.csd.security.CertUtil.toPEM;
import static edu.uiuc.ncsa.csd.security.KeyUtil.toPKCS1PEM;

/**
 * <p>Created by Jeff Gaynor<br>
 * on Jul 31, 2010 at  3:29:09 PM
 */
public class D1SuccessServlet extends PortalAbstractServlet {
    protected void doIt(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        String identifier = clearCookie(request, response);
        if (identifier == null) {
            throw new ServletException("Error: No identifier for this delegation request was found. ");
        }
        CILogonService cis = new CILogonService(getPortalEnvironment());
        PortalCredentials credential = cis.getCredential(identifier);
        
        // put the cookie for D1
    	PortalCertificateManager.getInstance().setCookie(identifier, response);
    	
    	// find where we should end up
    	String target = (String) request.getSession().getAttribute("target");
    	if (target != null) {
    		response.sendRedirect(target);
    		return;
    	}
    		
    	// otherwise show us information
        response.setContentType("text/html");
        PrintWriter pw = response.getWriter();
        /* Put the key and certificate in the result, but allow them to be initially hidden. */
        String y = "<html>\n" +
                "<style type=\"text/css\">\n" +
                ".hidden { display: none; }\n" +
                ".unhidden { display: block; }\n" +
                "</style>\n" +
                "<script type=\"text/javascript\">\n" +
                "function unhide(divID) {\n" +
                "    var item = document.getElementById(divID);\n" +
                "    if (item) {\n" +
                "        item.className=(item.className=='hidden')?'unhidden':'hidden';\n" +
                "    }\n" +
                "}\n" +
                "</script>\n" +
                "<body>\n" +
                "<h1>Success!</h1>\n" +
                "<p>You have successfully requested a DataONE certificate. It will be accessible for 18 hours using your cookie.</p>\n" +
                "<ul>\n" +
                "    <li><a href=\"javascript:unhide('showSubject');\">Show/Hide subject</a></li>\n" +
                "    <div id=\"showSubject\" class=\"unhidden\">\n" +
                "        <p><pre>" + credential.getX509Certificate().getSubjectDN().toString() + "</pre>\n" +
                "    </div>\n" +
                "    <li><a href=\"javascript:unhide('showCert');\">Show/Hide certificate</a></li>\n" +
                "    <div id=\"showCert\" class=\"hidden\">\n" +
                "        <p><pre>" + toPEM(credential.getX509Certificate()) + "</pre>\n" +
                "    </div>\n" +
                "    <li><a href=\"javascript:unhide('showKey');\">Show/Hide private key</a></li>\n" +
                "    <div id=\"showKey\" class=\"hidden\">\n" +
                "        <p><pre>" + toPKCS1PEM(credential.getPrivateKey()) + "</pre>\n" +
                "    </div>\n" +
                "\n" +
                "</ul>\n" +
                "<a href=" + request.getContextPath() + ">" +
                "Return to portal" +
                "</a> or " +
                "<a href=" + target + ">" +
                "Continue to target" +
                "</a>" +
                "</body>\n" +
                "</html>";
        pw.println(y);
        pw.flush();
    }


}
