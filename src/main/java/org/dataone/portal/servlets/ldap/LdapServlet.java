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

package org.dataone.portal.servlets.ldap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.auth.CertificateManager;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.configuration.Settings;
import org.dataone.portal.session.SessionHelper;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;

/**
 * Simple servlet for handling ORCID auth
 */
public class LdapServlet extends HttpServlet {
	
	private static Log log = LogFactory.getLog(LdapServlet.class);

	private AuthLdap auth = null;
	
	private Base64 b64 = new Base64();
	
	public void init(ServletConfig config) throws ServletException {
		
		// augment the properties with configured portal properties file
		String propertiesFile = config.getServletContext().getInitParameter("portal.properties.file");
		if (propertiesFile != null) {
			try {
				Settings.augmentConfiguration(propertiesFile);
			} catch (ConfigurationException e) {
				// report the exception
				throw new ServletException(e);
			}
		}
		
		// initialize the auth ldap instance
		try {
			auth = new AuthLdap();
		} catch (InstantiationException e) {
			throw new ServletException(e);
		}
		
		// initialize the session helper
		SessionHelper.getInstance().init(config);
		
		// set up certificate manager to act as CN
		String certificateLocation = Settings.getConfiguration().getString("D1Client.certificate.directory") 
				+ File.separator + Settings.getConfiguration().getString("D1Client.certificate.filename");
		CertificateManager.getInstance().setCertificateLocation(certificateLocation);

	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
	IOException {
		handleRequest(request, response);
	}
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,
	IOException {
		handleRequest(request, response);
	}
	
	private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// where should we end up with after authentication?
		String target = request.getParameter("target");
		
		// get session to save information to
		HttpSession session = request.getSession();

		// get credentials from request - either header or parameters
		String username = null;
		String password = null;
		
		// authenticate from header
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Basic")) {
	        // Authorization: Basic base64credentials
	        String base64Credentials = authorization.substring("Basic".length()).trim();
	        String credentials = new String(b64.decode(base64Credentials), Charset.forName("UTF-8"));
	        // credentials = username:password
	        String[] values = credentials.split(":", 2);
	        username = values[0];
	        password = values[1];
	        
		} else {
			
			// or check the request parameters
			username = request.getParameter("username");
			password = request.getParameter("password");
		}
		
		boolean authenticated;
		
		if((username != null && !username.isEmpty()) && (password != null && !password.isEmpty())) { 		
			// test authentication against LDAP
			authenticated = auth.authenticate(username, password);
		} else {
			log.warn("Unable to authenticate LDAP user: " + username + ". Missing username or password.");
			//Go back to the target with an error URL parameter
			response.sendRedirect(target + "?error=Unable%20to%20authenticate%20LDAP%20user");
			return;
		}
		
		if (authenticated) {
			SubjectInfo info = auth.getSubjectInfo(username);
			String fullName = info.getPerson(0).getGivenName(0) + " " + info.getPerson(0).getFamilyName();
			session.setAttribute("userId", username);
			session.setAttribute("name", fullName);
			session.setAttribute("accessToken", "valueNotUsed");
			
			// save session for later (token retrieval)
			SessionHelper.getInstance().saveSession(session);
			
			// register them with the CN?
			try {
				try {
					SubjectInfo registeredInfo = D1Client.getCN().getSubjectInfo(null, info.getPerson(0).getSubject());
				} catch (NotFound nf) {
					// so register them
					D1Client.getCN().registerAccount(null, info.getPerson(0));
				}
			} catch (BaseException be) {
				// oh well, didn't register it, or something went wrong
				log.warn(be.getMessage(), be);
			}
			
			// send to target location
			response.sendRedirect(target);
			return;
		}
		
		// Go back to the target with an error URL parameter
		log.warn("Unable to authenticate LDAP user: " + username);
		response.sendRedirect(target + "?error=Unable%20to%20authenticate%20LDAP%20user");
	}
	


}
