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

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.configuration.ConfigurationException;
import org.dataone.configuration.Settings;
import org.dataone.portal.session.SessionHelper;
import org.dataone.service.types.v1.SubjectInfo;

/**
 * Simple servlet for handling ORCID auth
 */
public class LdapServlet extends HttpServlet {
	
	private AuthLdap auth = null;
	
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
		
		
		
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// where should we end up with after authentication?
		String target = request.getParameter("target");
		
		// get session to save information to
		HttpSession session = request.getSession();

		// authenticate
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		boolean authenticated = auth.authenticate(username, password);
		
		if (authenticated) {
			SubjectInfo info = auth.getSubjectInfo(username);
			String fullName = info.getPerson(0).getGivenName(0) + " " + info.getPerson(0).getFamilyName();
			session.setAttribute("userId", username);
			session.setAttribute("name", fullName);
			session.setAttribute("accessToken", "valueNotUsed");
			// save for later
			SessionHelper.getInstance().saveSession(session);
			// send to target
			response.sendRedirect(target);
			return;
		}
		
		// otherwise an error
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unable to authenticate user: " + username);
		

	}
	


}
