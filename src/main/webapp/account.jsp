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
<script src="jquery/jquery-1.6.4.min.js"></script>
<script type="text/javascript">

function makeAjaxCall(url, formId, divId) {
	$('#' + divId).load(
		url, //url
		$("#" + formId).serialize(), //data
		function(response, status, xhr) {
			if (status == "error") {
				var msg = "Sorry but there was an error: ";
				$("#error").html(msg + xhr.status + " " + xhr.statusText);
			}
		}
	);
}
function listSubjects() {
	makeAjaxCall("subjects.jsp", "equivalentIdentities", "subject");
}

function init() {
	// list all the subjects
	listSubjects();
}

</script>
</head>
<body onload="init()">

<!-- load AJAX results here -->
<div id="result"></div>
<div id="error"></div>

<div id="register">
	<h1>DataONE Account Details</h1>

	<form action="<%=request.getContextPath()%>/identity" method="POST">
		<table>
			<tr>
				<td>Logged in as</td>
				<td>
					<input type="text" size="60" readonly="readonly" name="displaySubject" value="<%=subject.getValue() %>">
					(<a href="<%=request.getContextPath()%>/identity?action=logout">Logout</a>)
					<!-- <a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a> -->
				</td>
			</tr>
			<tr>
				<td>Given Name</td>
				<td><input type="text" name="givenName" value="<%=person != null ? person.getGivenName(0) : "" %>"></td>
			</tr>
			<tr>
				<td>Family Name</td>
				<td><input type="text" name="familyName" value="<%=person != null ? person.getFamilyName() : "" %>"></td>
			</tr>
			<tr>
				<td>Email</td>
				<td><input type="text" name="email" value="<%=person != null ? person.getEmail(0) : "" %>"></td>
			</tr>
			<tr>
				<td colspan="2">
					<input type="hidden" name="subject" value="<%=subject.getValue() %>">
					<input type="hidden" name="action" value="TBD">
					<%
					// only show the register button when it makes sense
					if (person == null) {
					%>
						<input type="button" value="Register" onclick="form.action.value='registerAccount'; form.submit();">
					<%
					} else {
					%>
						<input type="button" value="Update" onclick="form.action.value='updateAccount'; form.submit();">
						<input type="button" value="Verify" onclick="form.action.value='verifyAccount'; form.submit();">
					<%
					}
					%>
				</td>
			</tr>
		</table>
	</form>
</div>

<div id="accounts">
	<h1>Equivalent Identities</h1>
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="equivalentIdentities">
		<table>
			<tr>
				<td>Search</td>
				<td><input type="text" name="query" onkeyup="listSubjects()"></td>
			<tr>
				<td>Select</td>
				<td>
					<select name="subject" size="5" id="subject">
					</select>
				</td>
			</tr>
			<tr>
				<td colspan="2">
					<input type="hidden" name="action" value="TBD">
					<input type="button" value="Map as Me" onclick="form.action.value='mapIdentity'; form.submit();">
					<input type="button" value="Confirm Mapping" onclick="form.action.value='confirmMapIdentity'; form.submit();">
				</td>
			</tr>
		</table>
	</form>
</div>

<div id="groups">
	<h1>Group Management</h1>
</div>

</body>
</html>