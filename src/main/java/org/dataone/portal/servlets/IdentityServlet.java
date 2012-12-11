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
import java.io.PrintWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cilogon.portal.util.PortalCredentials;
import org.dataone.client.D1Client;
import org.dataone.client.auth.CertificateManager;
import org.dataone.configuration.Settings;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.types.v1.SubjectList;

/**
 * <p>Created by Ben Leinfelder<br>
 */
public class IdentityServlet extends HttpServlet {
	
	private static Log log = LogFactory.getLog(IdentityServlet.class);
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		// set the CN URL based on the context param
		String cnURL = config.getServletContext().getInitParameter("D1Client.CN_URL");
		if (cnURL != null) {
			Settings.getConfiguration().setProperty("D1Client.CN_URL", cnURL);
		}
		// point to the hazelcast config
		String hzConfig = config.getServletContext().getInitParameter("hazelcast.config");
		if (hzConfig != null) {
			System.setProperty("hazelcast.config", hzConfig);
			//Settings.getConfiguration().setProperty("hazelcast.config", hzConfig);
		}
	}
	
	
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    	throws ServletException, IOException {
    	// use the same method
    	handleRequest(request, response);	
    }
    public void doPost(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
    	// use the same method
    	handleRequest(request, response);
    }
	public void handleRequest(HttpServletRequest request, HttpServletResponse response)
    	throws ServletException, IOException {
		
		Session session = null;
		Subject subject = null;
		X509Certificate certificate = null;

		log.debug("request characterEncoding: " + request.getCharacterEncoding());
    	
		// get the certificate, if we have it
		try {
	    	certificate = PortalCertificateManager.getInstance().getCertificate(request);
	    	PrivateKey key = PortalCertificateManager.getInstance().getPrivateKey(request);
	    	String subjectDN = CertificateManager.getInstance().getSubjectDN(certificate);
	    	
			// set in the D1client/CertMan
	    	CertificateManager.getInstance().registerCertificate(subjectDN , certificate, key);
	    	
	    	// pass this subject in as the Session so that the certificate can be found later in the process
			session = new Session();
			subject = new Subject();
			subject.setValue(subjectDN);
			session.setSubject(subject);
		} catch (Exception e) {
			// some actions do not require the client certificate be found via cookie
			log.warn("Could not find some parameters -- this may present problems for some actions");
		}

		// process the action accordingly
    	String action = request.getParameter("action");
    	String msg = null;
    	
    	try {
    		
	    	if (action.equalsIgnoreCase("registerAccount")) {
	    		// gather the information needed for this method
		    	Person person = new Person();
		    	String familyName = request.getParameter("familyName");
				log.debug("familyName: " + familyName);
		    	String givenName = request.getParameterValues("givenName")[0];
				log.debug("givenName: " + givenName);
		    	String email = request.getParameterValues("email")[0];
				log.debug("email: " + email);
				person.setFamilyName(familyName);
				person.addEmail(email);
				person.addGivenName(givenName);
				person.setSubject(subject);
				// register
				Subject retSubject = D1Client.getCN().registerAccount(session, person);
				msg = "Account registered: " + retSubject.getValue();
	    	}
	    	if (action.equalsIgnoreCase("updateAccount")) {
	    		// gather the information needed for this method
		    	Person person = new Person();
		    	String familyName = request.getParameter("familyName");
				log.debug("familyName: " + familyName);
		    	String givenName = request.getParameterValues("givenName")[0];
				log.debug("givenName: " + givenName);
		    	String email = request.getParameterValues("email")[0];
				log.debug("email: " + email);
				person.setFamilyName(familyName);
				person.addEmail(email);
				person.addGivenName(givenName);
				person.setSubject(subject);
				// update
				Subject retSubject = D1Client.getCN().updateAccount(session, person);
				msg = "Account updated: " + retSubject.getValue();
	    	}
	    	if (action.equalsIgnoreCase("verifyAccount")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToVerify = new Subject();
				subjectToVerify.setValue(subjectParam);
				// verify
				D1Client.getCN().verifyAccount(session, subjectToVerify);
				msg = "Account verified: " + subjectToVerify.getValue();
	    	}
	    	if (action.equalsIgnoreCase("requestMapIdentity")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToMap = new Subject();
				subjectToMap.setValue(subjectParam);
				// verify
				D1Client.getCN().requestMapIdentity(session, subjectToMap);
				msg = "Account map requested for: " + subjectToMap.getValue();
	    	}
	    	if (action.equalsIgnoreCase("confirmMapIdentity")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToMap = new Subject();
				subjectToMap.setValue(subjectParam);
				// confirm
				D1Client.getCN().confirmMapIdentity(session, subjectToMap);
				msg = "Account map confirmed for: " + subjectToMap.getValue();
	    	}
	    	if (action.equalsIgnoreCase("denyMapIdentity")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToMap = new Subject();
				subjectToMap.setValue(subjectParam);
				// deny
				D1Client.getCN().denyMapIdentity(session, subjectToMap);
				msg = "Account map denied for: " + subjectToMap.getValue();
	    	}
	    	if (action.equalsIgnoreCase("removeMapIdentity")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToMap = new Subject();
				subjectToMap.setValue(subjectParam);
				// remove
				D1Client.getCN().removeMapIdentity(session, subjectToMap);
				msg = "Account mapping removed for: " + subjectToMap.getValue();
	    	}
	    	if (action.equalsIgnoreCase("createGroup")) {
	    		// gather the information needed for this method
		    	String groupNameParam = request.getParameter("groupName");
		    	Subject groupName = new Subject();
		    	groupName.setValue(groupNameParam);
		    	Group group = new Group();
		    	group.setSubject(groupName);
		    	group.setGroupName(groupNameParam);
				Subject retSubject = D1Client.getCN().createGroup(session, group);
				msg = "Group created: " + retSubject.getValue();
	    	}
	    	if (action.equalsIgnoreCase("addGroupMembers")) {
	    		// gather the information needed for this method
	    		String groupNameParam = request.getParameter("groupName");
		    	Subject groupName = new Subject();
		    	groupName.setValue(groupNameParam);
	    		String[] membersParam = request.getParameterValues("members");
				SubjectList members = new SubjectList();
	    		for (String m: membersParam) {
	    			Subject memberSubject = new Subject();
	    			memberSubject.setValue(m);
					members.addSubject(memberSubject);
	    		}
	    		// look up existing group to add members to
	    		SubjectInfo groupInfo = D1Client.getCN().getSubjectInfo(session, groupName);
	    		Group group = groupInfo.getGroup(0);
	    		group.getHasMemberList().addAll(members.getSubjectList());
				boolean result = D1Client.getCN().updateGroup(session, group);
				msg = "Members added to group: " + groupName.getValue();
	    	}
	    	if (action.equalsIgnoreCase("removeGroupMembers")) {
	    		// gather the information needed for this method
	    		String groupNameParam = request.getParameter("groupName");
		    	Subject groupName = new Subject();
		    	groupName.setValue(groupNameParam);
	    		String[] membersParam = request.getParameterValues("members");
				SubjectList members = new SubjectList();
	    		for (String m: membersParam) {
	    			Subject memberSubject = new Subject();
	    			memberSubject.setValue(m);
					members.addSubject(memberSubject);
	    		}
	    		// look up existing group to remove members from
	    		SubjectInfo groupInfo = D1Client.getCN().getSubjectInfo(session, groupName);
	    		Group group = groupInfo.getGroup(0);
	    		group.getHasMemberList().removeAll(members.getSubjectList());
				boolean result = D1Client.getCN().updateGroup(session, group);
				msg = "Members removed from group: " + groupName.getValue();
	    	}
	    	
	    	if (action.equalsIgnoreCase("getToken")) {
	    		// we need to return the token so other apps can use it to validate authentication
	    		Cookie cookie = PortalCertificateManager.getInstance().getCookie(request);
	    		if (cookie != null) {
	    			msg = cookie.getValue();
	    		}
	    	}
	    	
	    	if (action.equalsIgnoreCase("getSubject")) {
	    		String token = request.getParameter("token");
	    		if (token != null) {
	    			// look up via the token, not the cookie
	    			certificate = PortalCertificateManager.getInstance().getCredentials(token).getX509Certificate();
	    		}
	    		// tell them who is logged in with that certificate
	    		if (certificate != null) {
	    			msg = CertificateManager.getInstance().getSubjectDN(certificate);
	    		}
	    	}
	    	
	    	if (action.equalsIgnoreCase("isAuthenticated")) {
    			msg = Boolean.FALSE.toString();
	    		// check for the certificate by token
	    		String token = request.getParameter("token");
	    		PortalCredentials credentials = PortalCertificateManager.getInstance().getCredentials(token);
	    		if (credentials != null && credentials.getX509Certificate() != null) {
	    			msg = Boolean.TRUE.toString();
	    		}
	    	}
	    	
	    	if (action.equalsIgnoreCase("logout")) {
	    		// remove the cookie for D1
	        	PortalCertificateManager.getInstance().removeCookie(response);
				msg = "Logout successful for: " + subject.getValue();
	    	}
	    	
		} catch (Exception e) {
			// print to response while debugging
			e.printStackTrace(response.getWriter());
		}
		
		// is there a non-empty target URL to redirect to?
		String target = request.getParameter("target");
		if (target != null && target.length() > 0) {
			response.sendRedirect(target);
			return;
		}
		
		// write the response
        response.setContentType("text/html");
        PrintWriter pw = response.getWriter();
        // just print the plain text for AJAX
        pw.println(msg);
        pw.flush();
    }
}
