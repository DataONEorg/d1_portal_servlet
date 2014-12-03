<%@page language="java"%>
<%@page contentType="text/html; charset=UTF-8" %>
<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.v1.itk.D1Client"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Person"%>

<%
	// it will be a subject
	String subjectParam = request.getParameter("subject");
	Subject subject = new Subject();
	subject.setValue(subjectParam);
	
	// look up the subject info
	SubjectInfo subjectInfo = null;
	try {
		subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
	} catch (Exception e) {
		// ignore for now -- happens when there is no account
		%>
			Not Found	
		<%
		return;
	}
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		Person person = subjectInfo.getPerson(0);
	%>
	<table>
		<tr>
			<td class="label">Given Name</td>
			<td><input type="text" readonly="readonly" size="60" name="givenName" value="<%=person != null ? person.getGivenName(0) : "" %>"></td>
		</tr>
		<tr>
			<td class="label">Family Name</td>
			<td><input type="text" readonly="readonly" size="60" name="familyName" value="<%=person != null ? person.getFamilyName() : "" %>"></td>
		</tr>
		<tr>
			<td class="label">Email</td>
			<td><input type="text" readonly="readonly" size="60" name="email" value="<%=(person != null && person.getEmailList() != null && person.getEmailList().size() > 0) ? person.getEmail(0) : "" %>"></td>
		</tr>
	</table>
	<%
	}
%>