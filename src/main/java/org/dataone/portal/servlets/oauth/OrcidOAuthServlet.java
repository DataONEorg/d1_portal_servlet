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
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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
import org.dataone.portal.session.SessionHelper;
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

	public void init(ServletConfig config) throws ServletException {
		
		// for persisting session information across requests, callbacks and multiple servers
		SessionHelper.getInstance().init(config);
		
		// init the properties
		AUTHORIZATION_LOCATION = Settings.getConfiguration().getString("orcid.authorization.location");
		TOKEN_LOCATION = Settings.getConfiguration().getString("orcid.token.location");
		CLIENT_ID = Settings.getConfiguration().getString("orcid.client.id");
		CLIENT_SECRET = Settings.getConfiguration().getString("orcid.client.secret");

	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// handle the requests
		try {
			String action = request.getParameter("action");
			if (action != null) {
				if (action.equals("start")) {
					this.handleStart(request, response);
				}
			}
			else {
				this.handleCallback(request, response);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private void handleStart(HttpServletRequest request, HttpServletResponse response) throws OAuthSystemException, IOException {
		
		// we just come back here
		StringBuffer redirectUrl = HttpUtils.getRequestURL(request);
		
		// remember for the callback
		HttpSession session = request.getSession();
		// where should we end up with afterward?
		String target = request.getParameter("target");
		session.setAttribute("target", target);
		SessionHelper.getInstance().saveSession(session);
		
		OAuthClientRequest oauthRequest = OAuthClientRequest
				   .authorizationLocation(AUTHORIZATION_LOCATION)
				   .setClientId(CLIENT_ID)
				   .setRedirectURI(redirectUrl.toString())
				   .setResponseType(ResponseType.CODE.toString())
				   .setScope("/authenticate")
				   .setState(session.getId())
				   .buildQueryMessage();
		
		// direct them to the authorization location
		response.sendRedirect(oauthRequest.getLocationUri());
		
	}
	
	private void handleCallback(HttpServletRequest request, HttpServletResponse response) throws OAuthProblemException, OAuthSystemException, IOException {
		
		// get the auth code from the callback
		OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
		String code = oar.getCode();
		String sessionId = oar.getState();
		
		// get the access token
		OAuthClientRequest clientRequest = OAuthClientRequest
                .tokenLocation(TOKEN_LOCATION)
                .setGrantType(GrantType.AUTHORIZATION_CODE)
                .setClientId(CLIENT_ID)
                .setClientSecret(CLIENT_SECRET)
                .setCode(code)
                .buildBodyMessage();
		
		String body = clientRequest.getBody();
		System.out.println("body=" + body);
		
		//create OAuth client that uses custom http client under the hood
        OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
        
        // retrieve the access token
		clientRequest.setHeader("Accept", "application/json");
        OAuthJSONAccessTokenResponse oAuthResponse = oAuthClient.accessToken(clientRequest, "POST");
		 
        String accessToken = oAuthResponse.getAccessToken();
        Long expiresIn = oAuthResponse.getExpiresIn();
		String scope = oAuthResponse.getScope();
		
		// details about this person
		String orcid = oAuthResponse.getParam("orcid");
		String name = oAuthResponse.getParam("name");
		
		Map<String, Object> sessionMap = SessionHelper.getInstance().getMap(sessionId);
		sessionMap.put("accessToken", accessToken);
		sessionMap.put("userId", orcid);
		sessionMap.put("name", name);
		// optional attributes for portal
		sessionMap.put("expiresIn", expiresIn);
		sessionMap.put("scope", scope);
		sessionMap.put("orcid", orcid);
		SessionHelper.getInstance().saveMap(sessionId, sessionMap);
		
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
		
		String target = (String) sessionMap.get("target");
		if (target != null) {
			// redirect to target
			response.sendRedirect(target);
		} else {
			// redirect to token context base?
			response.sendRedirect(this.getServletContext().getResource("/").toString());
		}

	}

}
