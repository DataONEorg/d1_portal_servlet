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
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.IOUtils;
import org.dataone.client.auth.CertificateManager;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.portal.TokenGenerator;
import org.dataone.portal.oauth.OAuthHelper;
import org.dataone.service.types.v1.SubjectInfo;

import com.nimbusds.jose.JOSEException;

/**
 * Simple servlet for handling ORCID auth
 */
public class TokenServlet extends HttpServlet {
	
	public void init(ServletConfig config) throws ServletException {
		
		// initialize the oauth helper
		OAuthHelper.getInstance().init(config);
		
	}
	
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
			IOException {
		
		// handle the requests
		String token = null;
		try {
			token = this.getCertificateToken(request, response);	
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (token == null) {
			try {
				token = this.getOAuthToken(request, response);	
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (token == null) {
			token = "";
		}
		
		// write the JWT token
		ServletOutputStream out = response.getOutputStream();
		IOUtils.write(token, out);

	}
	
	private String getCertificateToken(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    	String token = null;
    	
    	// generate a token for this user based on information in the request
    	try {        	
    		X509Certificate certificate = PortalCertificateManager.getInstance().getCertificate(request);
    		if (certificate != null) {
        		String userId = CertificateManager.getInstance().getSubjectDN(certificate);        		
        		String fullName = null;
        		SubjectInfo subjectInfo = CertificateManager.getInstance().getSubjectInfo(certificate);
        		if (subjectInfo != null) {
        			fullName = subjectInfo.getPerson(0).getFamilyName();
        			if (subjectInfo.getPerson(0).getGivenNameList() != null && subjectInfo.getPerson(0).getGivenNameList().size() > 0) {
        				fullName = subjectInfo.getPerson(0).getGivenName(0) + fullName;
        			}
        		}
    			token = TokenGenerator.getInstance().getJWT(userId, fullName);
    			
    			// make sure we keep the cookie on the reponse
        		Cookie cookie = PortalCertificateManager.getInstance().getCookie(request);
        		String identifier = cookie.getValue();
				PortalCertificateManager.getInstance().setCookie(identifier, response);

    		}
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServletException(e);
		}
    	
    	return token;
    
	}
	
	private String getOAuthToken(HttpServletRequest request, HttpServletResponse response) throws IOException, JOSEException, ParseException {
		
		// look up the token
		HttpSession session = request.getSession();
		Map<String, Object> sessionMap = OAuthHelper.getInstance().getMap(session.getId());
		String accessToken = (String) sessionMap.get("accessToken");
		String orcid = (String) sessionMap.get("orcid");
		String name = (String) sessionMap.get("name");
		
		String jwt = null;
		if (accessToken != null) {
			jwt = TokenGenerator.getInstance().getJWT(orcid, name);
		}
		
		return jwt;
	
	}

}
