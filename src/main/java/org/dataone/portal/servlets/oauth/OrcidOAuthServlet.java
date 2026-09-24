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

package org.dataone.portal.servlets.oauth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.configuration.Settings;
import org.dataone.portal.servlets.RedirectTargets;
import org.dataone.portal.session.PortalSession;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;

/**
 * Simple servlet for handling ORCID auth
 */
public class OrcidOAuthServlet extends HttpServlet {
	
	private static Log log = LogFactory.getLog(OrcidOAuthServlet.class);

	
	private static String AUTHORIZATION_LOCATION = null;
	private static String TOKEN_LOCATION = null;
	private static String CLIENT_ID = null;
	private static String CLIENT_SECRET = null;
	private static String ORCID_PREFIX = null;

	/**
	 * The parts of ORCID's access token response that the portal uses
	 */
	protected static class OrcidToken {
		String accessToken;
		Long expiresIn;
		String scope;
		String orcid;
		String name;
	}

	public void init(ServletConfig config) throws ServletException {
		
		// init the properties
		AUTHORIZATION_LOCATION = Settings.getConfiguration().getString("orcid.authorization.location");
		TOKEN_LOCATION = Settings.getConfiguration().getString("orcid.token.location");
		CLIENT_ID = Settings.getConfiguration().getString("orcid.client.id");
		CLIENT_SECRET = Settings.getConfiguration().getString("orcid.client.secret");
		ORCID_PREFIX = Settings.getConfiguration().getString("orcid.prefix");

	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// handle the requests
		String action = request.getParameter("action");
		try {
			if (action != null) {
				if (action.equals("start")) {
					this.handleStart(request, response);
				}
			}
			else {
				this.handleCallback(request, response);
			}
			
		} catch (Exception e) {
			log.error("ORCID login failed (action=" + action + ")", e);
			if (response.isCommitted()) {
				return;
			}
			// send the browser back to where it started, if we know where that was
			PortalSession session = PortalSession.find(request);
			String target = session == null ? null : session.getTarget();
			if (action == null && RedirectTargets.isAllowed(target)) {
				response.sendRedirect(withParameter(target, "error", "login_failed"));
			} else {
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Login failed");
			}
		}

	}
	
	private void handleStart(HttpServletRequest request, HttpServletResponse response) throws OAuthSystemException, IOException {
		
		// we just come back here
		StringBuffer redirectUrl = HttpUtils.getRequestURL(request);
		
		// where should we end up afterward?
		String target = request.getParameter("target");
		if (target != null && !RedirectTargets.isAllowed(target)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid target");
			return;
		}
		
		// remember for the callback
		PortalSession session = PortalSession.create(request);
		session.setTarget(target);
		
		OAuthClientRequest oauthRequest = OAuthClientRequest
				   .authorizationLocation(AUTHORIZATION_LOCATION)
				   .setClientId(CLIENT_ID)
				   .setRedirectURI(redirectUrl.toString())
				   .setResponseType(ResponseType.CODE.toString())
				   .setScope("/authenticate")
				   .setState(session.newOAuthState())
				   .setParameter("show_login", "true")
				   .buildQueryMessage();
		
		// direct them to the authorization location
		response.sendRedirect(oauthRequest.getLocationUri());
		
	}
	
	private void handleCallback(HttpServletRequest request, HttpServletResponse response) throws OAuthProblemException, OAuthSystemException, IOException {
		
		// get the auth code from the callback
		OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
		String code = oar.getCode();
		
		// the callback must come back to the session that started the login, with its state
		PortalSession session = PortalSession.find(request);
		if (session == null || !session.consumeOAuthState(oar.getState())) {
			log.warn("Rejecting ORCID callback with no session or a mismatched state");
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or expired login request");
			return;
		}
		
		OrcidToken token = requestAccessToken(code);
		
		// include prefix
		String orcid = ORCID_PREFIX + token.orcid;
		String name = token.name;
		
		// prevent session fixation: the logged-in session gets a new id
		request.changeSessionId();
		session.setAccessToken(token.accessToken);
		session.setUserId(orcid);
		session.setName(name);
		// optional attributes for portal
		session.setExpiresIn(token.expiresIn);
		session.setScope(token.scope);
		session.setOrcid(orcid);
		
		registerAccount(orcid, name);
		
		String target = session.getTarget();
		if (target != null) {
			// redirect to target (checked in handleStart)
			response.sendRedirect(target);
		} else {
			response.setContentType("text/plain; charset=UTF-8");
			response.getWriter().println("Login complete.");
		}

	}
	
	/**
	 * Add a query parameter to a URL
	 */
	static String withParameter(String url, String name, String value) {
		String separator = url.contains("?") ? "&" : "?";
		return url + separator + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
	
	/**
	 * Exchange an authorization code for an ORCID access token
	 */
	protected OrcidToken requestAccessToken(String code) throws OAuthSystemException, OAuthProblemException {
		
		// get the access token
		OAuthClientRequest clientRequest = OAuthClientRequest
                .tokenLocation(TOKEN_LOCATION)
                .setGrantType(GrantType.AUTHORIZATION_CODE)
                .setClientId(CLIENT_ID)
                .setClientSecret(CLIENT_SECRET)
                .setCode(code)
                .buildBodyMessage();
		
		//create OAuth client that uses custom http client under the hood
        OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
        
        // retrieve the access token
		clientRequest.setHeader("Accept", "application/json");
        OAuthJSONAccessTokenResponse oAuthResponse = oAuthClient.accessToken(clientRequest, "POST");
		 
        OrcidToken token = new OrcidToken();
        token.accessToken = oAuthResponse.getAccessToken();
        token.expiresIn = oAuthResponse.getExpiresIn();
		token.scope = oAuthResponse.getScope();
		
		// details about this person
		token.orcid = oAuthResponse.getParam("orcid");
		token.name = oAuthResponse.getParam("name");
		return token;
	}
	
	/**
	 * Register the ORCID subject with the CN if it isn't already registered
	 */
	protected void registerAccount(String orcid, String name) {
		
		// attempt to register them with the CN
		try {
			
			Subject subject = new Subject();
			subject.setValue(orcid);
			Person person = new Person();
			person.setSubject(subject);
			
			// rudimentary parsing of name if possible
			String givenName = null;
			String familyName = name;
			if (name != null &&name.contains(" ")) {
				givenName = name.split(" ", 2)[0];
				familyName = name.split(" ", 2)[1];
			}
			person.addGivenName(givenName);
			person.setFamilyName(familyName);
			try {
				SubjectInfo registeredInfo = D1Client.getCN().getSubjectInfo(null, subject);
			} catch (NotFound nf) {
				// so register them
				D1Client.getCN().registerAccount(null, person);
			}
		} catch (BaseException be) {
			// oh well, didn't register it, or something went wrong
			log.warn(be.getMessage(), be);
		}
	}

}
