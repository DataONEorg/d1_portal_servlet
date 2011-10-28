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
<link type="text/css" href="portal.css" rel="Stylesheet" />	
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
	// construct the full DN here. yikes!
	var groupName = $('#createGroupForm [name="groupName"]').val();
	$('#createGroupForm [name="groupName"]').val('CN=' + groupName + ',DC=cilogon,DC=org');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'createGroupForm', 
			'result',
			'listGroups()');
	$('#createGroupForm [name="groupName"]').val(groupName);
	$("#result").dialog('open');
}
//the current equivalent ids
function listExistingEquivalentIdentities() {
	// get the current equivalent identities
	makeAjaxCall("subjectInfo.jsp", "identityLookupForm", "existingEquivalentIdentities");
}
//the current pending equivalent ids
function listPendingEquivalentIdentities() {
	// get the current pendingequivalent identities
	makeAjaxCall("pendingSubjectInfo.jsp", "identityLookupForm", "pendingEquivalentIdentities");
}
// map the identities
function requestMapIdentity() {
	$('#equivalentIdentitiesForm [name="action"]').val('requestMapIdentity');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'equivalentIdentitiesForm', 
			'result');
	$("#result").dialog('open');
}
// confirm the identity mapping, refresh to show the results
function confirmMapIdentity() {
	$('#pendingEquivalentIdentitiesForm [name="action"]').val('confirmMapIdentity');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'pendingEquivalentIdentitiesForm', 
			'result',
			'listExistingEquivalentIdentities();listPendingEquivalentIdentities()');
	$("#result").dialog('open');
}
//deny the identity mapping, refresh to show the results
function denyMapIdentity() {
	$('#pendingEquivalentIdentitiesForm [name="action"]').val('denyMapIdentity');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'pendingEquivalentIdentitiesForm', 
			'result',
			'listPendingEquivalentIdentities()');
	$("#result").dialog('open');
}
//remove the identity mapping, refresh to show the results
function removeMapIdentity() {
	$('#existingEquivalentIdentitiesForm [name="action"]').val('removeMapIdentity');
	makeAjaxCall(
			'<%=request.getContextPath()%>/identity', 
			'existingEquivalentIdentitiesForm', 
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
		// do we have a person registered yet?
		var isRegistered = <%=person != null ? true : false %>;
		if (!isRegistered) {
			// disable the other tabs until we are registered
			$("#tabs").tabs( "option", "disabled", [1, 2, 3] );
		}
		// TODO: figure out admins
		var isAdmin = false;
		if (!isAdmin) {
			// disable the verification tab for non-admins
			$("#tabs").tabs( "option", "disabled", [3] );
		}
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
	listPendingEquivalentIdentities();

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

<!-- dataone logo header -->
<div class="logoheader">
	<h1></h1>
</div>

<!-- load AJAX results here -->
<div id="result"></div>
<div id="error"></div>

<div id="tabs">
	<!-- place holder for tabs -->
	<ul></ul>

<div id="myAccount">
	<h2>Account Details</h2>
	<p>
	Please enter biographical details for this identity. 
	Your identity provider may have provided some of the information, depending on that institution's policy.
	You are free to edit this information to keep our records current.
	</p>
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="accountForm">
		<table>
			<tr>
				<td class="label">Logged in as</td>
				<td>
					<input type="text" size="60" readonly="readonly" name="displaySubject" value="<%=subject.getValue() %>">
				</td>
				<td>
					(<a href="<%=request.getContextPath()%>/identity?action=logout&target=<%=request.getContextPath()%>">Logout</a>)
					<!-- <a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a> -->
				</td>
			</tr>
			<tr>
				<td class="label">Given Name</td>
				<td><input type="text" size="60" name="givenName" value="<%=person != null ? person.getGivenName(0) : "" %>"></td>
				<td></td>
			</tr>
			<tr>
				<td class="label">Family Name*</td>
				<td><input type="text" size="60" name="familyName" value="<%=person != null ? person.getFamilyName() : "" %>"></td>
				<td></td>
			</tr>
			<tr>
				<td class="label">Email*</td>
				<td><input type="text" size="60" name="email" value="<%=person != null ? person.getEmail(0) : "" %>"></td>
				<td></td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
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
	<p>
	Equivalent identities allow us to maintain continuity in our access control rules as institutional
	affiliations shift over time. A new identity can be "mapped" to an older identity so that the new identity 
	is granted the same access privileges as the older identity; and vice versa.
	</p>

	<!-- use this form for ajax lookups -->
	<form id="identityLookupForm">
		<input type="hidden" name="subject" value="<%=subject.getValue() %>">
	</form>

	<!-- existing -->
	<form action="" method="POST" id="existingEquivalentIdentitiesForm">
		<table>
			<tr>
				<td class="label">Logged in as</td>
				<td>
					<input type="text" size="60" readonly="readonly" name="displaySubject" value="<%=subject.getValue() %>">
				</td>
			</tr>
			<tr>
				<td class="label">Existing</td>
				<td>
					<select name="subject" size="5" id="existingEquivalentIdentities" style="width : 100%;">
					</select>
				</td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
					<input type="hidden" name="action" value="removeMapIdentity">
					<input type="button" value="Remove Mapping" onclick="removeMapIdentity();">
				</td>
			</tr>
		</table>
	</form>

	<!-- PENDING -->
	<p>
	Identity mapping is a 2-step process: a request by Identity A to map as Identity B must be confirmed by Identity B.
	Pending requests for you that were initiated by <em>other</em> accounts are below.
	</p>
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="pendingEquivalentIdentitiesForm">
		<table>
			<tr>
				<td class="label">Pending</td>
				<td>
					<select name="subject" size="5" id="pendingEquivalentIdentities" style="width : 100%;">
					</select>
				</td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
					<input type="hidden" name="action" value="TBD">
					<input type="button" value="Confirm Mapping" onclick="confirmMapIdentity();">
					<input type="button" value="Deny Mapping" onclick="denyMapIdentity();">
				</td>
			</tr>
		</table>
	</form>

	<!-- ADD -->
	<p>
	You may request that your current identity be mapped to one below.
	A request must be confirmed by the other identity before it is active.
	</p>
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="equivalentIdentitiesForm">
		<table>
			<tr>
				<td class="label">Search</td>
				<td><input type="text" name="query" onkeyup="listPeople()"></td>
			</tr>
			<tr>
				<td class="label">Available</td>
				<td>
					<select name="subject" size="5" id="subject" style="width : 100%;">
					</select>
				</td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
					<input type="hidden" name="action" value="requestMapIdentity">
					<input type="button" value="Map as Me" onclick="requestMapIdentity();">
				</td>
			</tr>
		</table>
	</form>

</div>

<div id="groupManagement">
	<h2>Create Group</h2>
	<p>
	Groups allow us to define access control rules for one identity (the group) that apply to all the members.
	</p>
	<!-- create a group -->
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="createGroupForm">
		<table>
			<tr>
				<td class="label">Group Name</td>
				<td><input type="text" name="groupName" size="50"></td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
					<!--  <input type="hidden" name="target" value="<%=request.getContextPath()%>/account.jsp"/> -->
					<input type="hidden" name="action" value="createGroup">
					<input type="button" value="Create" onclick="createGroup();">
				</td>
			</tr>
		</table>
	</form>

	<h2>Edit Group</h2>

	<!-- edit a group -->
	<p>
	The account that created the group can add and remove members from it. 
	Eventually, we will define rules that allow other accounts to edit the group.
	</p>
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="editGroupForm">
		<table>
			<tr>
				<td class="label">Group Name</td>
				<td>
					<select name="groupName" id="groupName" onchange="listCurrentMembers()" style="width: 100%">
						<option>None Selected</option>
					</select>
					<input type="hidden" name="action" value="TBD">
				</td>
			</tr>
			<tr>
				<td class="label">Current members</td>
				<td>
					<select name="members" size="5" id="currentMembers" multiple="multiple" style="width: 100%"></select>
				</td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
					<input type="button" value="Remove selected" onclick="removeGroupMembers();">
				</td>
			</tr>
			<tr>
				<td class="label">Search</td>
				<td><input type="text" name="query" onkeyup="listPotentialMembers()"></td>
			</tr>
			<tr>
				<td class="label">Potential Members</td>
				<td>
					<select name="members" size="5" id="potentialMembers" multiple="multiple" style="width: 100%"></select>
				</td>
			</tr>
			<tr>
				<td class="label"></td>
				<td align="right">
					<input type="button" value="Add selected" onclick="addGroupMembers();">
				</td>
			</tr>
		</table>
	</form>
</div>

<div id="accountVerification">
	<h2>Account Verification</h2>
	<p>
	New account details should be verified by an administrator before they become active in DataONE.
	This is particularly true of Google and ProtectNetwork identities that provide no assurances that someone
	is who they claim to be. 
	Accounts from institutions with which DataONE has an established trust relationship may not require verification.
	</p>
	<form action="<%=request.getContextPath()%>/identity" method="POST" id="verifyAccountForm">
		<table>
			<tr>
				<td class="label">Unverified Accounts</td>
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
				<td class="label"></td>
				<td align="right">
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