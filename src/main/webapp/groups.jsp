<%@page import="org.dataone.service.types.v1.SubjectList"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Group"%>
<%
	String query = request.getParameter("query");
	
	// look up the subjects
	SubjectList subjectList = D1Client.getCN().listSubjects(null, query, 0, -1);
	if (subjectList != null && subjectList.getGroupList() != null) {
		for (Group g: subjectList.getGroupList()) {
	%>
			<option value="<%=g.getSubject().getValue()%>">
				<%=g.getGroupName()%> (<%=g.getSubject().getValue()%>)
			</option>				
	<%
		}
	}
%>