package org.dataone.portal.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.client.D1Client;
import org.dataone.client.auth.CertificateManager;
import org.dataone.configuration.Settings;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectList;

/**
 * <p>Created by Ben Leinfelder<br>
 */
public class IdentityServlet extends HttpServlet {
	
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		// set the CN URL based on the context param
		String cnURL = config.getServletContext().getInitParameter("D1Client.CN_URL");
		if (cnURL != null) {
			Settings.getConfiguration().setProperty("D1Client.CN_URL", cnURL);
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
		
		 // get the certificate, if we have it
    	X509Certificate certificate = PortalCertificateManager.getInstance().getCertificate(request);
    	PrivateKey key = PortalCertificateManager.getInstance().getPrivateKey(request);
    	String subjectDN = CertificateManager.getInstance().getSubjectDN(certificate);
    	
		// set in the D1client/CertMan
    	CertificateManager.getInstance().registerCertificate(subjectDN , certificate, key);
    	
    	// pass this subject in as the Session so that the certificate can be found later in the process
		Session session = new Session();
		Subject subject = new Subject();
		subject.setValue(subjectDN);
		session.setSubject(subject);

		// process the action accordingly
    	String action = request.getParameter("action");
    	String msg = null;
    	
    	try {
    		
	    	if (action.equalsIgnoreCase("registerAccount")) {
	    		// gather the information needed for this method
		    	Person person = new Person();
		    	String familyName = request.getParameter("familyName");
		    	String givenName = request.getParameterValues("givenName")[0];
		    	String email = request.getParameterValues("email")[0];
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
		    	String givenName = request.getParameterValues("givenName")[0];
		    	String email = request.getParameterValues("email")[0];
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
	    	if (action.equalsIgnoreCase("mapIdentity")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToMap = new Subject();
				subjectToMap.setValue(subjectParam);
				// verify
				D1Client.getCN().mapIdentity(session, subjectToMap);
				msg = "Account map requested for: " + subjectToMap.getValue();
	    	}
	    	if (action.equalsIgnoreCase("confirmMapIdentity")) {
	    		// gather the information needed for this method
		    	String subjectParam = request.getParameter("subject");
				Subject subjectToMap = new Subject();
				subjectToMap.setValue(subjectParam);
				// verify
				D1Client.getCN().confirmMapIdentity(session, subjectToMap);
				msg = "Account map confirmed for: " + subjectToMap.getValue();
	    	}
	    	if (action.equalsIgnoreCase("createGroup")) {
	    		// gather the information needed for this method
		    	String groupNameParam = request.getParameter("groupName");
		    	Subject groupName = new Subject();
		    	groupName.setValue(groupNameParam);
				Subject retSubject = D1Client.getCN().createGroup(session, groupName);
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
	    			Person member = null;
	    			// look up the person details from the group?
	    			SubjectList memberInfo = D1Client.getCN().getSubjectInfo(session, memberSubject);
	    			member = memberInfo.getPerson(0);
					members.addPerson(member);
	    		}
				boolean result = D1Client.getCN().addGroupMembers(session, groupName, members);
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
	    			Person member = null;
	    			// look up the person details from the group?
	    			SubjectList memberInfo = D1Client.getCN().getSubjectInfo(session, memberSubject);
	    			member = memberInfo.getPerson(0);
					members.addPerson(member);
	    		}
				boolean result = D1Client.getCN().removeGroupMembers(session, groupName, members);
				msg = "Members removed from group: " + groupName.getValue();
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
        String html = "<html>\n" +
                "<body>\n" +
                "<h1>Results</h1>\n" +
                "<p>" + msg + "</p>" +
                "</body>\n" +
                "</html>";
        //pw.println(html);
        // just print the plain text for AJAX
        pw.println(msg);
        pw.flush();
    }
}
