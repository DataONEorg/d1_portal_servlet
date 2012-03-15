<%@page language="java"%>
<%@page import="org.dataone.client.D1Client"%>
<%@page import="org.dataone.service.types.v1.NodeList"%>
<%@page import="java.util.List"%>
<%@page import="org.dataone.service.types.v1.Node"%>
<%@page import="org.dataone.service.types.v1.ObjectList"%>
<%@page import="org.dataone.service.types.v1.Identifier"%>
<%@page import="org.dataone.service.types.v1.ObjectLocationList"%>
<%@page import="org.dataone.service.types.v1.ObjectLocation"%>
<%@page import="org.dataone.service.types.v1.NodeReference"%>
<%@page import="java.io.InputStream"%>
<%@page import="org.dataone.service.types.v1.SystemMetadata"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
<title>DataONE PID Status</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<link type="text/css" href="jquery/jqueryui/css/smoothness/jquery-ui-1.8.16.custom.css" rel="Stylesheet" />
<link type="text/css" href="portal.css" rel="Stylesheet" />	
<script src="jquery/jquery-1.6.4.min.js"></script>
<script src="jquery/jqueryui/jquery-ui-1.8.16.custom.min.js"></script>
<script type="text/javascript">
function initTabs() {
	$(function() {
		$("#tabs").tabs();
		$("#tabs").tabs("add", "#status", "Object Status");		
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
	
	<div id="status">	
		
		<p>
		Environment: <em><%=D1Client.getCN().getNodeBaseServiceUrl() %></em>
		</p>

<%
	// get the PID from the request if it is there
	Identifier pid = null;
	if (request.getParameter("pid") != null) {
		pid = new Identifier();
		pid.setValue(request.getParameter("pid"));
	}
%>
	<form action="" method="get">
		PID: <input type="text" name="pid" id="pid" size="50" value='<%=pid == null ? "" : pid.getValue() %>'>
		<input type="submit" value="Check"/>
	</form>
<%	
	if (pid != null) {
%>
		<p>
		Resolve results for <em><%=pid.getValue() %></em>
		</p>
			<%
			String errorMsg = null;
			ObjectLocationList objectLocationList = null;
			try {
				objectLocationList = D1Client.getCN().resolve(null, pid);
			} catch (Exception e) {
				errorMsg = e.getMessage();
			}
			
			if (objectLocationList != null) {
			%>

				<table width="100%">
					<tr>
						<th>Location</th>
						<th>Get</th>
						<th>System Metadata</th>
						<th>Log</th>
					</tr>
			<%
				for (ObjectLocation objectLocation: objectLocationList.getObjectLocationList()) {
					NodeReference node = objectLocation.getNodeIdentifier();
					String getString = "unavailable";
					try {
						// TODO: check the stream for content?
						InputStream result = D1Client.getMN(node).get(null, pid);
						if (result != null) {
							getString = "success";
						}
					} catch (Exception e) {
						// crudely report an error
						getString = "<pre>" + e. getClass().getName() + " - "+ e.getMessage() + "</pre>";
					}
					String systemMetadataString = "unavailable";
					try {
						// TODO: more useful part of SM to display?
						SystemMetadata sm = D1Client.getMN(node).getSystemMetadata(null, pid);
						if (sm != null) {
							systemMetadataString = "{" + sm.getChecksum().getAlgorithm() + "}" + sm.getChecksum().getValue();
						}
					} catch (Exception e) {
						// crudely report an error
						systemMetadataString = "<pre>" + e. getClass().getName() + " - "+ e.getMessage() + "</pre>";
					}
					// TODO: log count
					String logString = "check not implemented";
					%>
					<tr>
						<td><%=node.getValue() %></td>
						<td><%=getString %></td>
						<td><%=systemMetadataString %></td>
						<td><%=logString %></td>
					</tr>
					<%
				}
			%>
				</table>

				<p>Other nodes that may contain the object</p>
	
				<table width="100%">
					<tr>
						<th>Location</th>
						<th>Get</th>
						<th>System Metadata</th>
						<th>Log</th>
					</tr>
			<%
			
				// show results for all other nodes
				NodeList nodeList = D1Client.getCN().listNodes();
				if (nodeList!= null) {
					List<Node> nodes = nodeList.getNodeList();
					for (Node n: nodes) {
						NodeReference node = n.getIdentifier();
						String getString = "unavailable";
						try {
							// TODO: check the stream for content?
							InputStream result = D1Client.getMN(node).get(null, pid);
							if (result != null) {
								getString = "success";
							}
						} catch (Exception e) {
							// crudely report an error
							getString = "<pre>" + e. getClass().getName() + " - "+ e.getMessage() + "</pre>";
						}
						String systemMetadataString = "unavailable";
						try {
							// TODO: more useful part of SM to display?
							SystemMetadata sm = D1Client.getMN(node).getSystemMetadata(null, pid);
							if (sm != null) {
								systemMetadataString = "{" + sm.getChecksum().getAlgorithm() + "}" + sm.getChecksum().getValue();
							}
						} catch (Exception e) {
							// crudely report an error
							systemMetadataString = "<pre>" + e. getClass().getName() + " - "+ e.getMessage() + "</pre>";
						}
						// TODO: log count
						String logString = "check not implemented";
						%>
						<tr>
							<td><%=node.getValue() %></td>
							<td><%=getString %></td>
							<td><%=systemMetadataString %></td>
							<td><%=logString %></td>
						</tr>
						<%
					}
				%>
				</table>
				<%
				}
			
			} else {
			%>
				<p><%=errorMsg %></p>
			<%
			} 
		}
			%>

	</div>

</div>

</body>
</html>