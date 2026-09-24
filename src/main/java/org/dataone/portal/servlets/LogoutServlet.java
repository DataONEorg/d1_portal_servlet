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
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.session.PortalSession;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.openid.connect.sdk.LogoutRequest;

/**
 * Ends the portal login session and redirects to the target URL. For Keycloak logins, the
 * browser is sent to Keycloak's end-session endpoint first, so the Keycloak single sign-on
 * session ends too, and Keycloak then redirects to the target. The target must be registered as
 * a post-logout redirect URI on the Keycloak client.
 */
public class LogoutServlet extends HttpServlet {
	
	private static Log log = LogFactory.getLog(LogoutServlet.class);
	
	/**
	 * @return the Keycloak provider; tests override this
	 */
	protected KeycloakProvider getProvider() {
		return KeycloakProvider.getInstance();
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// handle the request
		String target = request.getParameter("target");
		
		// get rid of session, remembering what Keycloak needs to end its session too
		PortalSession session = PortalSession.find(request);
		boolean keycloakLogin = false;
		String idToken = null;
		if (session != null) {
			keycloakLogin = PortalSession.SOURCE_KEYCLOAK.equals(session.getAuthSource());
			idToken = session.getIdToken();
			session.invalidate();
		}
		
		if (target != null && !RedirectTargets.isAllowed(target)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid target");
			return;
		}
		
		if (keycloakLogin) {
			URI keycloakLogout = getKeycloakLogoutURI(idToken, target);
			if (keycloakLogout != null) {
				response.sendRedirect(keycloakLogout.toString());
				return;
			}
		}
		
		// return to where they came
		if (target == null) {
			response.setContentType("text/plain; charset=UTF-8");
			response.getWriter().println("Logged out.");
		} else {
			response.sendRedirect(target);
		}

	}
	
	/**
	 * @return Keycloak's end-session URL for this login, or null if it can't be built
	 */
	private URI getKeycloakLogoutURI(String idToken, String target) {
		KeycloakProvider provider = getProvider();
		if (!provider.isEnabled() || idToken == null) {
			return null;
		}
		try {
			URI endSession = provider.getMetadata().getEndSessionEndpointURI();
			if (endSession == null) {
				return null;
			}
			return new LogoutRequest(endSession, JWTParser.parse(idToken),
					target == null ? null : URI.create(target), null).toURI();
		} catch (Exception e) {
			log.warn("Could not build the Keycloak logout URL; ending the portal session only", e);
			return null;
		}
	}
	
}
