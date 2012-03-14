<%@page language="java"%>
<%@page import="org.dataone.client.D1Client"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%@page import="org.dataone.service.types.v1.NodeList"%>
<%@page import="java.util.List"%>
<%@page import="org.dataone.service.types.v1.Node"%>
<%@page import="org.dataone.service.types.v1.ObjectList"%><html>
<head>
<title>DataONE Network Status</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<link type="text/css" href="jquery/jqueryui/css/smoothness/jquery-ui-1.8.16.custom.css" rel="Stylesheet" />
<link type="text/css" href="portal.css" rel="Stylesheet" />	
<script src="jquery/jquery-1.6.4.min.js"></script>
<script src="jquery/jqueryui/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript">
function initTabs() {
	$(function() {
		$("#tabs").tabs();
		$("#tabs").tabs("add", "#overview", "Overview");
		$("#tabs").tabs("add", "#nodes", "Nodes");
		
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

	<div id="overview">
		<p>
		DataONE network overview:
		<ul>
			<li>Name of the environment</li>
			<li>Coordinating and Member nodes participating in the environment</li>
			<li>Number of objects per node</li>
			<li>Ping status and time of last check for each node</li>
			<li>Last synchronization check for member nodes</li>
		</ul>
		</p>
		
		<p>
		This is a prototype and may be relocated
		</p>
	</div>
	
	<div id="nodes">	
		<p>
		Nodes:
		</p>
		<table>
			<tr>
				<td>Node</td>
				<td>Object Count</td>
				<td>State</td>
				<td>Last synchronized</td>
			</tr>
			<%
			NodeList nodeList = D1Client.getCN().listNodes();
			if (nodeList!= null) {
				List<Node> nodes = nodeList.getNodeList();
				for (Node node: nodes) {
					String objectCountString = "unavailable";
					try {
						ObjectList objectList = D1Client.getMN(node.getIdentifier()).listObjects(null);
						int objectCount = objectList.getTotal();
						objectCountString = Integer.toString(objectCount);
					} catch (Exception e) {
						// what can we really do?
						objectCountString = "Error: " + e.getMessage();		
						e.printStackTrace();
					}
			%>	
					<tr>
						<td><%=node.getIdentifier().getValue() %></td>
						<td><%=objectCountString %></td>
						<td><%=node.getState().xmlValue() %></td>
						<td><%=node.getSynchronization().getLastHarvested() %></td>
					</tr>
			<%	
				}
			}
			%>
			</tr>
		</table>
		
		<p>
		This is a prototype and may be relocated
		</p>
	</div>

</div>

</body>
</html>