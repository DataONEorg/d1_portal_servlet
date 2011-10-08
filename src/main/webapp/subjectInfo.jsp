<%@page import="org.dataone.service.types.v1.SubjectList"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Group"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Person"%>

<%
	// it will be either a subject or a groupName
	String subjectParam = request.getParameter("subject");
	if (subjectParam == null || subjectParam.length() == 0) {
		subjectParam = request.getParameter("groupName");
	}
	Subject subject = new Subject();
	subject.setValue(subjectParam);
	
	// look up the subjects
	SubjectList subjectList = D1Client.getCN().getSubjectInfo(null, subject);
	if (subjectList != null && subjectList.getGroupList() != null) {
		// include the Groups
		for (Group g: subjectList.getGroupList()) {
	%>
			<option value="<%=g.getSubject().getValue()%>">
				<%=g.getGroupName()%> (<%=g.getSubject().getValue()%>)
			</option>				
	<%
			// add the group members
			if (g.getHasMemberList() != null) {
				for (Subject groupMember: g.getHasMemberList()) {
				%>
					<option value="<%=groupMember.getValue()%>">
						(<%=groupMember.getValue()%>)
					</option>				
				<%
				}
			}
		}
	}
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