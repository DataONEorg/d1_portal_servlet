<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Group"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Person"%>
<%@page import="java.util.Map"%>
<%@page import="java.util.HashMap"%>

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

	// the display names
	Map<Subject, String> displayNames = new HashMap<Subject, String>();
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		for (Person p: subjectInfo.getPersonList()) {
			String displayName = p.getFamilyName();
			if (p.getGivenNameList() != null && !p.getGivenNameList().isEmpty()) {
				displayName = p.getGivenName(0) + " " + displayName;
			}
			displayName += " (" + p.getSubject().getValue() + ")";
			displayNames.put(p.getSubject(), displayName);
		}
	}
	if (subjectInfo != null && subjectInfo.getGroupList() != null) {
		for (Group g: subjectInfo.getGroupList()) {
			String displayName = g.getGroupName();
			displayName += " (" + g.getSubject().getValue() + ")";
			displayNames.put(g.getSubject(), displayName);
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
		<!-- do not show group as a member of itself 
		<option value="<%=group.getSubject().getValue()%>">
			<%=group.getGroupName()%> (<%=group.getSubject().getValue()%>)
		</option>
		-->
	<%
	
		for (Subject s: group.getHasMemberList()) {
			// lookup the member's name
			String memberName = displayNames.get(s);
	%>	
			<option value="<%=s.getValue()%>">
				<%=memberName%>
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
		<!-- do not show member in group list
		<option value="<%=person.getSubject().getValue()%>">
			<%=person.getFamilyName()%> (<%=person.getSubject().getValue()%>)
		</option>				
		-->
	<%
	
		// include the groups they are a member of
		for (Subject s: person.getIsMemberOfList()) {
			// lookup the group's name
			String groupName = displayNames.get(s);
	%>	
			<option value="<%=s.getValue()%>">
				<%=groupName%>
			</option>				
	<%
		}
	
	}
%>