<%@page language="java"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<title>DataONE Portal Authentication</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<link type="text/css" href="jquery/jqueryui/css/smoothness/jquery-ui-1.8.16.custom.css" rel="Stylesheet" />
<link type="text/css" href="portal.css" rel="Stylesheet" />	
<script src="jquery/jquery-1.6.4.min.js"></script>
<script src="jquery/jqueryui/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript">
function initTabs() {
	$(function() {
		$("#tabs").tabs();
		$("#tabs").tabs("add", "#login", "Login");
	});
}
</script>
</head>
<body onload="initTabs()">
<!-- dataone logo header -->
<div class="logoheader">
	<h1></h1>
</div>
<div id="tabs">
	<!-- place holder for tabs -->
	<ul></ul>

<div id="login">
<p>
This prototype Identity Management application allows:
<ul>
	<li>CILogon-based authentication using a number of different institutional identity providers</li>
	<li>DataONE account registration</li>
	<li>mapping one DataONE identity to another so that legacy account permissions can be preserved</li>
	<li>creating and editing DataONE groups to more easily manage permissions for a number of different identities</li>
	<li>verifying newly registered DataONE accounts (admin role)</li>
</ul>
</p>

<p>
You will need to have a valid CILogon-compatible identity to begin. 
If your institution is not listed on the CILogon page, you can create either a Google or ProtectNetwork account for free.
</p>
<p>
DataONE plays no role in the authentication process -- 
your username and passwords are only exchanged with the chosen identity provider and this is managed by CILogon.
</p>
<p>
<a href="<%=request.getContextPath()%>/startRequest?target=<%=request.getContextPath()%>/account.jsp">Begin Login</a>
</p>
</div>

</div>

</body>
</html>