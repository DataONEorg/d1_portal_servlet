<%@page language="java"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.SubjectList"%>
<%@page import="org.dataone.service.types.v1.Person"%>
<%@ include file="setup.jsp"%>
<html>
<head>
<title>DataONE Portal Registration</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
</head>
<body>

<div id="main">
	<h1>DataONE Account Management</h1>
	Logged in as: <%=subject.getValue() %>
	<br/>
	<a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a>
	<br/>
	<a href="<%=request.getContextPath()%>/identity?action=logout">Logout</a>

</div>

<div id="register">
	<h1>Account Details</h1>
	<form action="<%=request.getContextPath()%>/identity" method="POST">
		Given Name: <input type="text" name="givenName" value="">
		<br/>
		Family Name: <input type="text" name="familyName">
		<br/>
		Email: <input type="text" name="email">
		<br/>
		<input type="hidden" name="action" value="TBD">
		<input type="button" value="Register" onclick="form.action.value='registerAccount'; form.submit();">
		<input type="button" value="Update" onclick="form.action.value='updateAccount'; form.submit();">
		<input type="button" value="Verify" onclick="form.action.value='verifyAccount'; form.submit();">
	</form>
</div>

<div id="accounts">
	<h1>Equivalent Identities</h1>
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

<div id="groups">
	<h1>Group Management</h1>
</div>

</body>
</html>