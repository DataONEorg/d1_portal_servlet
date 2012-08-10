<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Group"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Person"%>

<%
	// it will be either a subject or a groupName
	boolean isGroup = false;
	String subjectParam = request.getParameter("subject");
	if (subjectParam == null || subjectParam.length() == 0) {
		subjectParam = request.getParameter("groupName");
		isGroup = true;
	}
	Subject subject = new Subject();
	subject.setValue(subjectParam);
	
	// look up the subjects
	SubjectInfo subjectInfo = null;
	try {
		subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
	} catch (Exception e) {
		// ignore for now -- happens when account is not registered
		%>
			<option value="NONE">
				None Found
			</option>	
		<%
		return;
	}
	
	if (subjectInfo != null && isGroup) {
		// include the Groups
		boolean isFirst = true;
		for (Group g: subjectInfo.getGroupList()) {
			// omit the first entry which is this group
			if (isGroup && isFirst) {
				isFirst = false;
				continue;
			}
	%>
			<option value="<%=g.getSubject().getValue()%>">
				<%=g.getGroupName()%> (<%=g.getSubject().getValue()%>)
			</option>		
	<%
		}
	}

	if (isGroup && subjectInfo != null && subjectInfo.getGroupList() != null) {
		// include the Groups
		Group group = null;
		for (Group g: subjectInfo.getGroupList()) {
			// this is the group we care about
			if (g.getSubject().equals(subject)) {
				group = g;
				break;
			}
		}
	%>
		<option value="<%=group.getSubject().getValue()%>">
			<%=group.getGroupName()%> (<%=group.getSubject().getValue()%>)
		</option>	
	<%
	
		for (Subject s: group.getHasMemberList()) {
			// TODO: lookup the member's name
			String memberName = s.getValue();
	%>	
			<option value="<%=s.getValue()%>">
				<%=memberName%> (<%=s.getValue()%>)
			</option>				
	<%
		}
	
	}
	// use the person
	if (!isGroup && subjectInfo != null && subjectInfo.getPersonList() != null) {

		// get the person we are talking about
		Person person = null;
		for (Person p: subjectInfo.getPersonList()) {
			
			if (p.getSubject().equals(subject)) {
				person = p;
				break;
			}
		}
	%>	
		<option value="<%=person.getSubject().getValue()%>">
			<%=person.getFamilyName()%> (<%=person.getSubject().getValue()%>)
		</option>				
	<%
	
		// include the groups they are a member of
		for (Subject s: person.getIsMemberOfList()) {
			// TODO: lookup the group's name
			String groupName = s.getValue();
	%>	
			<option value="<%=s.getValue()%>">
				<%=groupName%> (<%=s.getValue()%>)
			</option>				
	<%
		}
	
	}
%>