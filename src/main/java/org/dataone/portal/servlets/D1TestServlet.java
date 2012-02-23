package org.dataone.portal.servlets;

import static edu.uiuc.ncsa.security.util.pkcs.CertUtil.toPEM;

import java.io.PrintWriter;
import java.security.cert.X509Certificate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.cilogon.portal.servlets.PortalAbstractServlet;
import org.dataone.portal.PortalCertificateManager;

/**
 * <p>Created by Ben Leinfelder<br>
 */
public class D1TestServlet extends PortalAbstractServlet {
    protected void doIt(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Throwable {
        
        // get the certificate, if we have it
    	X509Certificate certificate = PortalCertificateManager.getInstance().getCertificate(httpServletRequest);
    	String subject = "NONE FOUND";
    	if (certificate != null) {
    		subject = certificate.getSubjectDN().toString();
    	}
    	
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
                "<h1>Authentication information</h1>\n" +
                "<p>This is the DataONE certificate associated with your session.</p>\n" +
                "<ul>\n" +
                "    <li><a href=\"javascript:unhide('showKey');\">Show/Hide subject</a></li>\n" +
                "    <div id=\"showKey\" class=\"unhidden\">\n" +
                "        <p><pre>" + subject + "</pre>\n" +
                "    </div>\n";
                
        		if (certificate != null) {
	                y += "    <li><a href=\"javascript:unhide('showCert');\">Show/Hide certificate</a></li>\n" +
	                "    <div id=\"showCert\" class=\"hidden\">\n" +
	                "        <p><pre>" + toPEM(certificate) + "</pre>\n" +
	                "    </div>\n";
        		}
                
                y += "\n" +
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
