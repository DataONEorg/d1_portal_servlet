<%@page language="java"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<title>DataONE Portal Registration</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
</head>
<body>
<a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a>

<form action="<%=request.getContextPath()%>/identity" method="POST">
	<input type="hidden" name="action" value="registerAccount">
	Given Name: <input type="text" name="givenName">
	<br/>
	Family Name: <input type="text" name="familyName">
	<br/>
	Email: <input type="text" name="email">
	<br/>
	<input type="submit" value="Register">
	<input type="button" value="Update" onclick="form.action.value='updateAccount'; form.submit();">
</form>
</body>
</html>