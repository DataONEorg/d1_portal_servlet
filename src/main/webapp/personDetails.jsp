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
	SubjectInfo subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		Person person = subjectInfo.getPerson(0);
	%>
	<table>
		<tr>
			<td>Given Name</td>
			<td><input type="text" size="60" name="givenName" value="<%=person != null ? person.getGivenName(0) : "" %>"></td>
		</tr>
		<tr>
			<td>Family Name</td>
			<td><input type="text" size="60" name="familyName" value="<%=person != null ? person.getFamilyName() : "" %>"></td>
		</tr>
		<tr>
			<td>Email</td>
			<td><input type="text" size="60" name="email" value="<%=person != null ? person.getEmail(0) : "" %>"></td>
		</tr>
	</table>
	<%
	}
%>