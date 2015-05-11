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

package org.dataone.portal.servlets.myproxy;

import static edu.uiuc.ncsa.security.util.pkcs.CertUtil.toPEM;

import java.io.File;
import java.io.PrintWriter;
import java.security.cert.X509Certificate;

import javax.naming.ldap.Rdn;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.client.auth.CertificateManager;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.configuration.Settings;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;

import edu.uiuc.ncsa.myproxy.oa4mp.client.Asset;
import edu.uiuc.ncsa.myproxy.oa4mp.client.AssetResponse;
import edu.uiuc.ncsa.myproxy.oa4mp.client.servlet.ClientServlet;
import edu.uiuc.ncsa.security.core.exceptions.GeneralException;
import edu.uiuc.ncsa.security.servlet.JSPUtil;

/**
 * <p>Created by Jeff Gaynor<br>
 * on Jul 31, 2010 at  3:29:09 PM
 */
public class D1SuccessServlet extends ClientServlet {
	
	protected int maxAttempts = 10;
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		// how many times should we wait for the transaction store to sync?
		String maxAttemptsString = config.getServletContext().getInitParameter("org.dataone.assetStore.maxAttempts");
		if (maxAttemptsString != null) {
			maxAttempts = Integer.parseInt(maxAttemptsString);
		}
		
		// set up certificate manager to act as CN
		String certificateLocation = Settings.getConfiguration().getString("D1Client.certificate.directory") 
				+ File.separator + Settings.getConfiguration().getString("D1Client.certificate.filename");
		CertificateManager.getInstance().setCertificateLocation(certificateLocation);
	}
	
    protected void doIt(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        String identifier = clearCookie(request, response);
        if (identifier == null) {
            throw new ServletException("Error: No identifier for this delegation request was found. ");
        }
        info("2.a. Getting token and verifier.");
        String token = request.getParameter(TOKEN_KEY);
        String verifier = request.getParameter(VERIFIER_KEY);
        if (token == null || verifier == null) {
            warn("2.a. The token is " + (token==null?"null":token) + " and the verifier is " + (verifier==null?"null":verifier));
            GeneralException ge = new GeneralException("Error: This servlet requires parameters for the token and verifier. It cannot be called directly.");
            request.setAttribute("exception", ge);
            JSPUtil.handleException(ge, request, response, "/pages/client-error.jsp");
            return;
            //throw ge;
        }
        info("2.a Token and verifier found.");
        X509Certificate cert = null;
        AssetResponse assetResponse = null;

        try {
            info("2.a. Getting the cert(s) from the service");
            assetResponse = getOA4MPService().getCert(token, verifier);
            X509Certificate[] certificates = assetResponse.getX509Certificates();
            // update the asset to include the returned certificate
            Asset asset = getOA4MPService().getEnvironment().getAssetStore().get(identifier);
            asset.setCertificates(certificates);
            getOA4MPService().getEnvironment().getAssetStore().save(asset);
            cert = certificates[0];
        } catch (Throwable t) {
            warn("2.a. Exception from the server: " + t.getCause().getMessage());
            error("Exception while trying to get cert. message:" + t.getMessage());
            request.setAttribute("exception", t);
            JSPUtil.handleException(t, request, response, "/pages/client-error.jsp");
            return;
            //throw t;
        }
        
        // put the cookie for D1
    	PortalCertificateManager.getInstance().setCookie(identifier, response);
    			
    	// register them with the CN
		try {
			Person person = new Person();
			Subject subject = new Subject();
			String dn = CertificateManager.getInstance().getSubjectDN(cert);
			subject.setValue(dn);
			person.setSubject(subject);
			Rdn rdn = new Rdn(dn);
			String cn = rdn.toAttributes().get("cn").get().toString();
			String firstName = cn.split(" ")[0];
			String familyName = cn.split(" ")[1];
			person.addGivenName(firstName);
			person.setFamilyName(familyName);
			try {
				SubjectInfo registeredInfo = D1Client.getCN().getSubjectInfo(null, person.getSubject());
			} catch (NotFound nf) {
				// so register them
				D1Client.getCN().registerAccount(null, person);
			}
		} catch (Exception e) {
			// oh well, didn't register it, or something went wrong
			//log.warn(be.getMessage(), be);
		}
    	
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
                "        <p><pre>" + cert.getSubjectDN().toString() + "</pre>\n" +
                "    </div>\n" +
                "    <li><a href=\"javascript:unhide('showCert');\">Show/Hide certificate</a></li>\n" +
                "    <div id=\"showCert\" class=\"hidden\">\n" +
                "        <p><pre>" + toPEM(cert) + "</pre>\n" +
                "    </div>\n" +
                "    <li><a href=\"javascript:unhide('showKey');\">Show/Hide private key</a></li>\n" +
                "    <div id=\"showKey\" class=\"hidden\">\n" +
                "        <p><pre>" + "hidden for security" + "</pre>\n" +
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
