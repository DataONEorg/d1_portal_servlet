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
	%>	
			<option value="<%=p.getSubject().getValue()%>">
				<%=p.getFamilyName()%> (<%=p.getSubject().getValue()%>)
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