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
import java.text.ParseException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.portal.TokenGenerator;
import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.session.PortalSession;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Simple servlet for handling ORCID auth
 */
public class TokenServlet extends HttpServlet {
	
	private static Log log = LogFactory.getLog(TokenServlet.class);
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// a Keycloak access token in the Authorization header is exchanged for a DataONE JWT
		String bearer = getBearerToken(request);
		KeycloakProvider provider = getProvider();
		if (bearer != null && provider.isIssuedBy(bearer)) {
			exchangeAccessToken(provider, bearer, response);
			return;
		}
		
		// otherwise issue a token for the logged-in session, if any; other bearer tokens (such
		// as DataONE JWTs) are ignored here, as before
		String token = null;
		try {
			token = this.getSessionToken(request, response);	
		} catch (Exception e) {
			log.error("Could not create a token for the session", e);
		}
		if (token == null) {
			token = "";
		}
		
		// write the JWT token
		ServletOutputStream out = response.getOutputStream();
		IOUtils.write(token, out);

	}
	
	/**
	 * @return the Keycloak provider; tests override this
	 */
	protected KeycloakProvider getProvider() {
		return KeycloakProvider.getInstance();
	}
	
	/**
	 * @return the token from an "Authorization: Bearer" header, or null
	 */
	private static String getBearerToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		String token = header.substring(7).trim();
		return token.isEmpty() ? null : token;
	}
	
	/**
	 * Validate a Keycloak access token and write a DataONE JWT for its subject, or answer 401 if
	 * the token isn't valid.
	 */
	private void exchangeAccessToken(KeycloakProvider provider, String accessToken,
			HttpServletResponse response) throws IOException {
		String jwt;
		try {
			JWTClaimsSet claims = provider.validateAccessToken(accessToken);
			jwt = TokenGenerator.getInstance().getJWT(provider.getSubject(claims),
					KeycloakProvider.getName(claims));
		} catch (Exception e) {
			log.info("Rejecting Keycloak access token: " + e.getMessage());
			response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		ServletOutputStream out = response.getOutputStream();
		IOUtils.write(jwt, out);
	}
	
	private String getSessionToken(HttpServletRequest request, HttpServletResponse response) throws IOException, JOSEException, ParseException {
		
		// look up the login, without creating a session for anonymous callers
		PortalSession session = PortalSession.find(request);
		
		String jwt = null;
		if (session != null && session.isLoggedIn()) {
			jwt = TokenGenerator.getInstance().getJWT(session.getUserId(), session.getName());
		}
		
		return jwt;
	
	}

}
