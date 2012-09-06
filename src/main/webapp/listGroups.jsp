<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Group"%>
<%
	String query = request.getParameter("query");
	
	// look up the subjects
	SubjectInfo subjectInfo = D1Client.getCN().listSubjects(null, query, null, 0, -1);
	if (subjectInfo != null && subjectInfo.getGroupList() != null) {
		for (Group g: subjectInfo.getGroupList()) {
			String displayName = g.getGroupName();
			displayName += " (" + g.getSubject().getValue() + ")";
	%>
			<option value="<%=g.getSubject().getValue()%>">
				<%=displayName%>
			</option>				
	<%
		}
	}
%>