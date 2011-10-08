<%@page import="org.dataone.service.types.v1.SubjectList"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Person"%>
<%
	String query = request.getParameter("query");
	
	// look up the subjects
	SubjectList subjectList = D1Client.getCN().listSubjects(null, query, 0, -1);
	if (subjectList != null && subjectList.getPersonList() != null) {
		for (Person p: subjectList.getPersonList()) {
	%>
			<option value="<%=p.getSubject().getValue()%>">
				<%=p.getFamilyName()%> (<%=p.getSubject().getValue()%>)
			</option>				
	<%
		}
	}
%>