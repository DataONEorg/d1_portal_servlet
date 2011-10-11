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
<link type="text/css" href="jquery/jqueryui/css/smoothness/jquery-ui-1.8.16.custom.css" rel="Stylesheet" />	
<script src="jquery/jquery-1.6.4.min.js"></script>
<script src="jquery/jqueryui/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript">

function makeAjaxCall(url, formId, divId, callback) {
	$('#' + divId).load(
		url, //url
		$("#" + formId).serialize(), //data
		function(response, status, xhr) {
			if (status == "error") {
				var msg = "Sorry but there was an error: ";
				$("#error").html(msg + xhr.status + " " + xhr.statusText);
			}
			// call the callback
			if (callback) {
				setTimeout(callback, 0);
			}
		}
	);
}
function listPeople() {
	makeAjaxCall("listPeople.jsp", "equivalentIdentitiesForm", "subject");
}
// the groups
function listGroups() {
	makeAjaxCall("listGroups.jsp", "editGroupForm", "groupName", "listCurrentMembers()");
}
// the current members
function listCurrentMembers() {
	// clear the selections
	$("#potentialMembers option:selected").attr("selected", false);
	// get the current membership
	makeAjaxCall("subjectInfo.jsp", "editGroupForm", "currentMembers");
}
// all potential members
function listPotentialMembers() {
	makeAjaxCall("listPeople.jsp", "editGroupForm", "potentialMembers");
}
function addGroupMembers() {
	$('#editGroupForm [name="action"]').val('addGroupMembers');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'editGroupForm', 
			'result', 
			'listCurrentMembers()');
	$("#result").dialog('open');
}
function removeGroupMembers() {
	$('#editGroupForm [name="action"]').val('removeGroupMembers');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'editGroupForm', 
			'result', 
			'listCurrentMembers()');
	$("#result").dialog('open');
}
function createGroup() {
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'createGroupForm', 
			'result',
			'listGroups()');
	$("#result").dialog('open');
}
//the current equivalent ids
function listExistingEquivalentIdentities() {
	// get the current ones
	makeAjaxCall("subjectInfo.jsp", "existingEquivalentIdentitiesForm", "existingEquivalentIdentities");
}
// map the identities
function mapIdentity() {
	$('#equivalentIdentitiesForm [name="action"]').val('mapIdentity');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'equivalentIdentitiesForm', 
			'result');
	$("#result").dialog('open');
}
// confirm the identity mapping, refresh to show the results
function confirmMapIdentity() {
	$('#equivalentIdentitiesForm [name="action"]').val('confirmMapIdentity');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'equivalentIdentitiesForm', 
			'result',
			'listExistingEquivalentIdentities()');
	$("#result").dialog('open');
}
// show unverified accounts
function listUnverifiedAccounts() {
	// populate the subject list
	makeAjaxCall("listPeople.jsp", "verifyAccountForm", "unverifiedSubject", "showUnverifiedAccount()");
}
//show unverified accounts
function verifyAccount() {
	// complete the call
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			"verifyAccountForm", 
			"result",
			"listUnverifiedAccounts()");
	$("#result").dialog('open');
}
function showUnverifiedAccount() {
	// load the person details into the page
	makeAjaxCall("personDetails.jsp", "verifyAccountForm", "unverifiedAccountDetails");
}
function initTabs() {
	$(function() {
		$("#tabs").tabs();
		$("#tabs").tabs("add", "#myAccount", "My Account");
		$("#tabs").tabs("add", "#myIdentities", "My Identities");
		$("#tabs").tabs("add", "#groupManagement", "Group Management");
		$("#tabs").tabs("add", "#accountVerification", "Verification");
		
	});
}
function initDialogs() {
	// make the result section a dialog (popup)
	$("#result").dialog(
			{	autoOpen: false,
				title: "Results",
				width: 450
			}
		);
}

function init() {
	// equivalent identities
	listPeople();
	listExistingEquivalentIdentities();

	//  groups
	listGroups();
	listPotentialMembers();

	// unverified accounts
	listUnverifiedAccounts();
	
	// showing popups
	initDialogs();
	
	// make the tabs
	initTabs();
	
}

</script>
</head>
<body onload="init()">

<h1>DataONE Identity Management</h1>

<!-- load AJAX results here -->
<div id="result"></div>
<div id="error"></div>

<div id="tabs">
	<!-- place holder for tabs -->
	<ul></ul>

<div id="myAccount">
	<h2>Account Details</h2>

	<form action="<%=request.getContextPath()%>/identity" method="POST" id="accountForm">
		<table>
			<tr>
				<td>Logged in as</td>
				<td>
					<input type="text" size="60" readonly="readonly" name="displaySubject" value="<%=subject.getValue() %>">
				</td>
				<td>
					(<a href="<%=request.getContextPath()%>/identity?action=logout&target=<%=request.getContextPath()%>">Logout</a>)
					<!-- <a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a> -->
				</td>
			</tr>
			<tr>
				<td>Given Name</td>
				<td><input type="text" size="60" name="givenName" value="<%=person != null ? person.getGivenName(0) : "" %>"></td>
				<td></td>
			</tr>
			<tr>
				<td>Family Name</td>
				<td><input type="text" size="60" name="familyName" value="<%=person != null ? person.getFamilyName() : "" %>"></td>
				<td></td>
			</tr>
			<tr>
				<td>Email</td>
				<td><input type="text" size="60" name="email" value="<%=person != null ? person.getEmail(0) : "" %>"></td>
				<td></td>
			</tr>
			<tr>
				<td colspan="2" align="right">
					<input type="hidden" name="subject" value="<%=subject.getValue() %>"/>
					<input type="hidden" name="target" value="<%=request.getContextPath()%>/account.jsp"/>
					<input type="hidden" name="action" value="TBD"/>
					<%
					// only show the register button when it makes sense
					if (person == null) {
					%>
						<input type="button" value="Register" onclick="form.action.value='registerAccount'; form.submit();">
					<%
					} else {
					%>
						<input type="button" value="Update" onclick="form.action.value='updateAccount'; form.submit();">
					<%
					}
					%>
				</td>
				<td></td>
			</tr>
		</table>
	</form>
</div>

<div id="myIdentities">
	<h2>Equivalent Identities</h2>

	<form action="" method="POST" id="existingEquivalentIdentitiesForm">
		<table>
			<tr>
				<td>Logged in as</td>
				<td>
					<input type="text" size="60" readonly="readonly" name="displaySubject" value="<%=subject.getValue() %>">
				</td>
			</tr>
			<tr>
				<td>Existing</td>
				<td>
					<input type="hidden" name="subject" value="<%=subject.getValue() %>"/>
					<select disabled="disabled" name="existingEquivalentIdentities" size="5" id="existingEquivalentIdentities">
					</select>
				</td>
			</tr>
		</table>
	</form>

	<form action="<%=request.getContextPath()%>/identity" method="POST" id="equivalentIdentitiesForm">
		<table>
			<tr>
				<td>Search</td>
				<td><input type="text" name="query" onkeyup="listPeople()"></td>
			</tr>
			<tr>
				<td>Select</td>
				<td>
					<select name="subject" size="5" id="subject">
					</select>
				</td>
			</tr>
			<tr>
				<td colspan="2" align="right">
					<input type="hidden" name="action" value="TBD">
					<input type="button" value="Map as Me" onclick="mapIdentity();">
					<input type="button" value="Confirm Mapping" onclick="confirmMapIdentity();">
				</td>
			</tr>
		</table>
	</form>
</div>

<div id="groupManagement">
	<h2>Create Group</h2>
	<!-- create a group -->
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="createGroupForm">
		<table>
			<tr>
				<td>Group Name</td>
				<td><input type="text" name="groupName" size="50"></td>
			</tr>
			<tr>
				<td colspan="2" align="right">
					<!--  <input type="hidden" name="target" value="<%=request.getContextPath()%>/account.jsp"/> -->
					<input type="hidden" name="action" value="createGroup">
					<input type="button" value="Create" onclick="createGroup();">
				</td>
			</tr>
		</table>
	</form>

	<h2>Edit Group</h2>

	<!-- edit a group -->
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="editGroupForm">
		<table>
			<tr>
				<td>Group Name</td>
				<td>
					<select name="groupName" id="groupName" onchange="listCurrentMembers()" style="width: 100%">
						<option>None Selected</option>
					</select>
					<input type="hidden" name="action" value="TBD">
				</td>
			</tr>
			<tr>
				<td>Current members</td>
				<td>
					<select name="members" size="5" id="currentMembers" multiple="multiple" style="width: 100%"></select>
				</td>
			</tr>
			<tr>
				<td colspan="2" align="right">
					<input type="button" value="Remove selected" onclick="removeGroupMembers();">
				</td>
			</tr>
			<tr>
				<td>Search</td>
				<td><input type="text" name="query" onkeyup="listPotentialMembers()"></td>
			</tr>
			<tr>
				<td>Potential Members</td>
				<td>
					<select name="members" size="5" id="potentialMembers" multiple="multiple" style="width: 100%"></select>
				</td>
			</tr>
			<tr>
				<td colspan="2" align="right">
					<input type="button" value="Add selected" onclick="addGroupMembers();">
				</td>
			</tr>
		</table>
	</form>
</div>

<div id="accountVerification">
	<h2>Account Verification</h2>

	<form action="<%=request.getContextPath()%>/identity" method="POST" id="verifyAccountForm">
		<table>
			<tr>
				<td>Unverified Accounts</td>
				<td>
					<select name="subject" id="unverifiedSubject" onchange="showUnverifiedAccount()" style="width: 100%">
						<option>None Selected</option>
					</select>
				</td>
			</tr>
			<tr>
				<td colspan="2">
					<div id="unverifiedAccountDetails"></div>
				</td>
			</tr>
			<tr>
				<td colspan="2" align="right">
					<input type="hidden" name="action" value="verifyAccount"/>
					<input type="hidden" name="status" value="unverified"/>
					<input type="button" value="Verify" onclick="verifyAccount();">
				</td>
			</tr>
		</table>
	</form>
</div>

<!-- end tabs -->
</div>

</body>
</html>