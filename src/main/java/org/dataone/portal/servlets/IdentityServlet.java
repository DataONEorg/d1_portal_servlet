package org.dataone.portal.servlets;

import org.cilogon.portal.CILogonService;
import org.cilogon.portal.servlets.PortalAbstractServlet;
import org.cilogon.portal.util.PortalCredentials;
import org.cilogon.util.CILogon;
import org.dataone.client.D1Client;
import org.dataone.client.auth.CertificateManager;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.service.exceptions.IdentifierNotUnique;
import org.dataone.service.exceptions.InvalidCredentials;
import org.dataone.service.exceptions.InvalidRequest;
import org.dataone.service.exceptions.InvalidToken;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import static edu.uiuc.ncsa.csd.security.CertUtil.toPEM;
import static edu.uiuc.ncsa.csd.security.KeyUtil.toPKCS1PEM;

/**
 * <p>Created by Ben Leinfelder<br>
 */
public class IdentityServlet extends HttpServlet {
	
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    	throws ServletException, IOException {
    	
    }
	public void doPost(HttpServletRequest request, HttpServletResponse response)
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
    	String action = request.getParameterValues("action")[0];
    	String msg = null;
    	
    	try {
    		
	    	if (action.equalsIgnoreCase("registerAccount")) {
	    		// gather the information needed for this method
		    	Person person = new Person();
		    	String familyName = request.getParameterValues("familyName")[0];
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
		    	String familyName = request.getParameterValues("familyName")[0];
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
		    	String subjectParam = request.getParameterValues("subject")[0];
				Subject subjectToVerify = new Subject();
				subjectToVerify.setValue(subjectParam);
				// verify
				D1Client.getCN().verifyAccount(session, subjectToVerify);
				msg = "Account verified: " + subjectToVerify.getValue();
	    	}
	    	
		} catch (Exception e) {
			// print to response while debugging
			e.printStackTrace(response.getWriter());
		}
		
		// write the response
        response.setContentType("text/html");
        PrintWriter pw = response.getWriter();
        String y = "<html>\n" +
                "<body>\n" +
                "<h1>Results</h1>\n" +
                "<p>" + msg + "</p>\n" +
                "</body>\n" +
                "</html>";
        pw.println(y);
        pw.flush();
    }
}
