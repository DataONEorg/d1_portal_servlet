<%@page language="java"%>
<%@page contentType="text/html; charset=UTF-8" %>
<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Person"%>
<%
	String query = request.getParameter("query");
	String status = request.getParameter("status");

	// look up the subjects
	SubjectInfo subjectInfo = D1Client.getCN().listSubjects(null, query, status, 0, -1);
	if (subjectInfo != null && subjectInfo.getPersonList() != null && subjectInfo.getPersonList().size() > 0) {
		for (Person p: subjectInfo.getPersonList()) {
			String displayName = p.getFamilyName();
			if (p.getGivenNameList() != null && !p.getGivenNameList().isEmpty()) {
				displayName = p.getGivenName(0) + " " + displayName;
			}
			displayName += " (" + p.getSubject().getValue() + ")";
	%>	
			<option value="<%=p.getSubject().getValue()%>">
				<%=displayName%>
			</option>				
	<%
		}
	} else {
	%>
		<option value="NONE">
			None Found
		</option>
	<%
	}
%>