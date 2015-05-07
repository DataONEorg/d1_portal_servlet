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

package org.dataone.portal.servlets.orcid;

import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.dataone.portal.oauth.OAuthHelper;

/**
 * Simple servlet for handling ORCID auth
 */
public class OrcidOAuthServlet extends HttpServlet {
	
	private static final String AUTHORIZATION_LOCATION = "https://sandbox.orcid.org/oauth/authorize";
	private static final String TOKEN_LOCATION = "https://api.sandbox.orcid.org/oauth/token";
	private static final String REDIRECT_URI = "https://cn-sandbox-2.test.dataone.org/portal/oauth";
	private static final String CLIENT_ID = "APP-YLSPZFL1W1JVKOXX";
	private static final String CLIENT_SECRET = "6cb791cc-8cfd-413c-8717-2be3bffa75e8";

	public void init(ServletConfig config) throws ServletException {
		
		OAuthHelper.getInstance().init(config);
		
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
		
		// remember for the callback
		HttpSession session = request.getSession();
		// where should we end up with afterward?
		String target = request.getParameter("target");
		session.setAttribute("target", target);
		OAuthHelper.getInstance().saveSession(session);
		
		OAuthClientRequest oauthRequest = OAuthClientRequest
				   .authorizationLocation(AUTHORIZATION_LOCATION)
				   .setClientId(CLIENT_ID)
				   .setRedirectURI(REDIRECT_URI)
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
                //.setRedirectURI(REDIRECT_URI + "?action=token")
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
		
		Map<String, Object> sessionMap = OAuthHelper.getInstance().getMap(sessionId);
		sessionMap.put("accessToken", accessToken);
		sessionMap.put("expiresIn", expiresIn);
		sessionMap.put("scope", scope);
		sessionMap.put("orcid", orcid);
		sessionMap.put("name", name);
		OAuthHelper.getInstance().saveMap(sessionId, sessionMap);
		
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
