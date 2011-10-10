<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
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
	SubjectInfo subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
	if (subjectInfo != null && subjectInfo.getGroupList() != null) {
		// include the Groups
		for (Group g: subjectInfo.getGroupList()) {
	%>
			<!-- do not show the group we came from -->
			<!-- 
			<option value="<%=g.getSubject().getValue()%>">
				<%=g.getGroupName()%> (<%=g.getSubject().getValue()%>)
			</option>		
			-->		
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
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		for (Person p: subjectInfo.getPersonList()) {
	%>	
			<option value="<%=p.getSubject().getValue()%>">
				<%=p.getFamilyName()%> (<%=p.getSubject().getValue()%>)
			</option>				
	<%
			// add the groups we are a member of
			if (p.getIsMemberOfList() != null) {
				for (Subject g: p.getIsMemberOfList()) {
				%>
					<option value="<%=g.getValue()%>">
						(<%=g.getValue()%>)
					</option>				
				<%
				}
			}
		}
	}
%>