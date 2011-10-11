<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Person"%>

<%
	// it will be a subject
	String subjectParam = request.getParameter("subject");
	Subject subject = new Subject();
	subject.setValue(subjectParam);
	
	// look up the subject info
	SubjectInfo subjectInfo = D1Client.getCN().getPendingMapIdentity(null, subject);
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		for (Person p: subjectInfo.getPersonList()) {
	%>
			<option value="<%=p.getSubject().getValue()%>">
				<%=p.getFamilyName()%> (<%=p.getSubject().getValue()%>)
			</option>	
	<%
		}
	}
		
%>