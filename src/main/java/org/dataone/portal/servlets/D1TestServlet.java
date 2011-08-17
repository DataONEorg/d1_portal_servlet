package org.dataone.portal.servlets;

import org.cilogon.portal.CILogonService;
import org.cilogon.portal.servlets.PortalAbstractServlet;
import org.cilogon.portal.util.PortalCredentials;
import org.cilogon.util.CILogon;
import org.dataone.portal.PortalCertificateManager;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;

import static edu.uiuc.ncsa.csd.security.CertUtil.toPEM;
import static edu.uiuc.ncsa.csd.security.KeyUtil.toPKCS1PEM;

/**
 * <p>Created by Ben Leinfelder<br>
 */
public class D1TestServlet extends PortalAbstractServlet {
    protected void doIt(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Throwable {
        
        
        // get the certificate
    	X509Certificate certificate = PortalCertificateManager.getInstance().getCertificate(httpServletRequest);
    	
        httpServletResponse.setContentType("text/html");
        PrintWriter pw = httpServletResponse.getWriter();
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
                "<p>This is the D1 certificate for your cookie.</p>\n" +
                "<ul>\n" +
                "    <li><a href=\"javascript:unhide('showCert');\">Show/Hide certificate</a></li>\n" +
                "    <div id=\"showCert\" class=\"unhidden\">\n" +
                "        <p><pre>" + toPEM(certificate) + "</pre>\n" +
                "    </div>\n" +
                "    <li><a href=\"javascript:unhide('showKey');\">Show/Hide private key</a></li>\n" +
                "    <div id=\"showKey\" class=\"hidden\">\n" +
                "        <p><pre>" + "NOTHING" + "</pre>\n" +
                "    </div>\n" +
                "\n" +
                "</ul>\n" +
                "<form name=\"input\" action=" + httpServletRequest.getContextPath() + "/ method=\"get\">\n" +
                "   <input type=\"submit\" value=\"Return to portal\" />\n" +
                "</form>" +
                "</body>\n" +
                "</html>";
        pw.println(y);
        pw.flush();
    }


}
