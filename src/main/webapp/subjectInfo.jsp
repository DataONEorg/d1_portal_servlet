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
	SubjectInfo subjectInfo = D1Client.getCN().getSubjectInfo(null, subject);
	if (subjectInfo != null && subjectInfo.getGroupList() != null) {
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
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		boolean isFirst = true;
		for (Person p: subjectInfo.getPersonList()) {
			// omit the first entry which is this group
			if (!isGroup && isFirst) {
				isFirst = false;
				continue;
			}
	%>	
			<option value="<%=p.getSubject().getValue()%>">
				<%=p.getFamilyName()%> (<%=p.getSubject().getValue()%>)
			</option>				
	<%
		}
	}
%>