<%@page language="java"%>
<%@page contentType="text/html; charset=UTF-8" %>
<%@page import="org.dataone.service.types.v1.Person"%>
<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%><%@page language="java"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Session"%>
<%@page import="org.dataone.client.auth.CertificateManager"%>
<%@page import="java.security.PrivateKey"%>
<%@page import="org.dataone.portal.PortalCertificateManager"%>
<%@page import="java.security.cert.X509Certificate"%>
<%

// get the certificate, if we have it
X509Certificate certificate = PortalCertificateManager.getInstance().getCertificate(request);
PrivateKey key = PortalCertificateManager.getInstance().getPrivateKey(request);

// if we don't have a certificate, then we aren't logged in
if (certificate == null || key == null) {
	response.sendRedirect(request.getContextPath());
	return;
}
// carry on if we have a certificate
String subjectDN = CertificateManager.getInstance().getSubjectDN(certificate);

// set in the D1client/CertMan
CertificateManager.getInstance().registerCertificate(subjectDN, certificate, key);

// pass this subject in as the Session so that the certificate can be found later in the process
Session d1Session = new Session();
Subject subject = new Subject();
subject.setValue(subjectDN);
d1Session.setSubject(subject);

// look up the details about the person represented by the subject, if possible
Person person = null;
try {
	SubjectInfo subjectInfo = D1Client.getCN().getSubjectInfo(d1Session, subject);
	person = subjectInfo.getPerson(0);
} catch (Exception e) {
	// ignore this for now...
}

%>