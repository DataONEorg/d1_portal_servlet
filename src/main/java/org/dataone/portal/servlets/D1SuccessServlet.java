/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For 
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 * 
 * $Id$
 */

package org.dataone.portal.servlets;

import org.cilogon.portal.CILogonService;
import org.cilogon.portal.servlets.PortalAbstractServlet;
import org.cilogon.portal.util.PortalCredentials;
import org.cilogon.util.exceptions.CILogonException;
import org.dataone.portal.PortalCertificateManager;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static edu.uiuc.ncsa.security.util.pkcs.CertUtil.toPEM;
import static edu.uiuc.ncsa.security.util.pkcs.KeyUtil.toPKCS1PEM;

/**
 * <p>Created by Jeff Gaynor<br>
 * on Jul 31, 2010 at  3:29:09 PM
 */
public class D1SuccessServlet extends PortalAbstractServlet {
	
	protected int maxAttempts = 10;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		// how many times should we wait for the transaction store to sync?
		String maxAttemptsString = config.getServletContext().getInitParameter("org.dataone.transactionStore.maxAttempts");
		if (maxAttemptsString != null) {
			maxAttempts = Integer.parseInt(maxAttemptsString);
		}
	}
	
    protected void doIt(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        String identifier = clearCookie(request, response);
        if (identifier == null) {
            throw new ServletException("Error: No identifier for this delegation request was found. ");
        }
        CILogonService cis = new CILogonService(getPortalEnvironment());
    	PortalCredentials credential = null;
    	int attempts = 0;
    	warn("trying credental lookup n times: "  + maxAttempts);
    	while (credential == null) {
	        try {
	        	credential = cis.getCredential(identifier);
	        } catch (CILogonException e) {
				// sleep and try again, for a while until failing
	        	warn(attempts + " - Error getting transaction, trying again. " + e.getMessage());
	        	Thread.sleep(500);
	        	attempts++;
	        	if (attempts > maxAttempts) {
	        		throw e;
	        	}
	        	// reset for the loop
	        	credential = null;
			}
    	}
        
        // put the cookie for D1
    	PortalCertificateManager.getInstance().setCookie(identifier, response);
    	
    	// find where we should end up
    	String target = (String) request.getSession().getAttribute("target");
    	if (target != null) {
    		// remove from the session once we use it
    		request.getSession().removeAttribute("target");
    		// send the redirect
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
