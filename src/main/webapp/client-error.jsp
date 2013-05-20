<%@page language="java"%>
<%@page contentType="text/html; charset=UTF-8" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<title>DataONE Portal Error</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<link type="text/css" href="jquery/jqueryui/css/smoothness/jquery-ui-1.8.16.custom.css" rel="Stylesheet" />
<link type="text/css" href="portal.css" rel="Stylesheet" />	
<script src="jquery/jquery-1.6.4.min.js"></script>
<script src="jquery/jqueryui/jquery-ui-1.8.16.custom.min.js"></script>
<%
	// default to non-production google id
	String trackerId = "UA-15017327-13";
	if (request.getServerName().contains("cn.dataone.org")) {
		trackerId = "UA-15017327-10";
	}
%>
<script type="text/javascript">
	
	var _gaq = _gaq || [];
	_gaq.push(['_setAccount', '<%=trackerId%>']);
	_gaq.push(['_setDomainName', 'dataone.org']);
	_gaq.push(['_trackPageview']);
	
	(function() {
		var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
		ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
		var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
	})();

</script>
</head>
<body onload="init()">

<!-- dataone logo header -->
<div class="logoheader">
	<h1></h1>
</div>
<body>
	<p>
		<b>Uh, oh...</b>
		<br/>
		There was a problem getting the cert. Check the server logs...
		<br/>
		The message received was: 
		<br/>
		<pre>${stackTrace}</pre>
		<br/>
		<br/>Message:${cause}
	</p>
</body>
</html>