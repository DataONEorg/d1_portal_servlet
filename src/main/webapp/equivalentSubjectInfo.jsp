<%@page import="org.dataone.service.types.v1.SubjectInfo"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.Subject"%>
<%@page import="org.dataone.service.types.v1.Person"%>
<%@page import="java.util.HashMap"%>
<%@page import="java.util.Map"%>

<%
	// it will be a subject
	String subjectParam = request.getParameter("subject");
	Subject subject = new Subject();
	subject.setValue(subjectParam);
	
	// look up the subject info
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
	// find the person we want to list equivalent identites for
	if (subjectInfo != null && subjectInfo.getPersonList() != null) {
		
		// the display names
		Map<Subject, String> displayNames = new HashMap<Subject, String>();
		for (Person p: subjectInfo.getPersonList()) {
			String displayName = p.getFamilyName();
			if (p.getGivenNameList() != null && !p.getGivenNameList().isEmpty()) {
				displayName = p.getGivenName(0) + " " + displayName;
			}
			displayName += " (" + p.getSubject().getValue() + ")";
			displayNames.put(p.getSubject(), displayName);
		}
		
		Person person = null;
		for (Person p: subjectInfo.getPersonList()) {
			if (p.getSubject().equals(subject)) {
				person = p;
				break;
			}
		}
		if (person.getEquivalentIdentityList() != null && !person.getEquivalentIdentityList().isEmpty()) {
			for (Subject s: person.getEquivalentIdentityList()) {
				// get the name for display
				String equivalentName = displayNames.get(s);
		%>
				<option value="<%=s.getValue()%>">
					<%=equivalentName%>
				</option>	
		<%
			}
		}
	}
%>