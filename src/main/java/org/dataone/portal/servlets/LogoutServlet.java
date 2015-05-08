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

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.dataone.portal.PortalCertificateManager;
import org.dataone.portal.session.SessionHelper;

/**
 * Simple servlet for handling ORCID auth
 */
public class LogoutServlet extends HttpServlet {
	
	public void init(ServletConfig config) throws ServletException {
		
		// initialize the session helper
		SessionHelper.getInstance().init(config);
		
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// handle the request
		HttpSession session = request.getSession();
		String target = request.getParameter("target");
		
		// get rid of session
		SessionHelper.getInstance().removeSession(session.getId());
		
		// get rid of portal session
		PortalCertificateManager.getInstance().removeCookie(response);
		
		// return to where they came
		response.sendRedirect(target);

	}
	
}
