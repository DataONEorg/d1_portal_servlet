
<%@page import="org.dataone.service.types.v1.Person"%>
<%@page import="org.dataone.service.types.v1.SubjectList"%>
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
String subjectDN = CertificateManager.getInstance().getSubjectDN(certificate);

// set in the D1client/CertMan
CertificateManager.getInstance().registerCertificate(subjectDN, certificate, key);

// pass this subject in as the Session so that the certificate can be found later in the process
Session d1Session = new Session();
Subject subject = new Subject();
subject.setValue(subjectDN);
d1Session.setSubject(subject);

// look up the details about the person represented by the subject
SubjectList subjectInfo = D1Client.getCN().getSubjectInfo(d1Session, subject);
Person person = subjectInfo.getPerson(0);

%>