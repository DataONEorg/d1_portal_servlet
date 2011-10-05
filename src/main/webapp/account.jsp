<%@page language="java"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.SubjectList"%>
<%@page import="org.dataone.service.types.v1.Person"%><html>
<head>
<title>DataONE Portal Registration</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
</head>
<body>
<a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a>

<div id="register">
	<form action="<%=request.getContextPath()%>/identity" method="POST">
		Given Name: <input type="text" name="givenName">
		<br/>
		Family Name: <input type="text" name="familyName">
		<br/>
		Email: <input type="text" name="email">
		<br/>
		<input type="hidden" name="action" value="TBD">
		<input type="button" value="Register" onclick="form.action.value='registerAccount'; form.submit();">
		<input type="button" value="Update" onclick="form.action.value='updateAccount'; form.submit();">
	</form>
</div>

<div id="accounts">
	<h1>Equivalent Identities</h1>
	Logged in as: (name here)
	<form action="<%=request.getContextPath()%>/identity" method="POST">
		<select name="subject">
		<%
			SubjectList subjectList = D1Client.getCN().listSubjects(null, null, 0, -1);
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
		</select>
		<input type="hidden" name="action" value="TBD">
		<input type="button" value="Map as Me" onclick="form.action.value='mapIdentity'; form.submit();">
		<input type="button" value="Confirm Mapping" onclick="form.action.value='confirmMapIdentity'; form.submit();">
	</form>

</div>

</body>
</html>