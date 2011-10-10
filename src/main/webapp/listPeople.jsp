<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Person"%>
<%
	String query = request.getParameter("query");

	// look up the subjects
	SubjectInfo subjectInfo = D1Client.getCN().listSubjects(null, query, 0, -1);
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