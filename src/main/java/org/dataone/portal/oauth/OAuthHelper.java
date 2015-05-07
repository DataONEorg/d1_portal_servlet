package org.dataone.portal.oauth;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

import com.hazelcast.client.ClientConfig;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

public class OAuthHelper {

	private static OAuthHelper instance = null;
	
	private IMap<String, Map<String, Object>> sessions = null;
	
	private OAuthHelper() {
		
	}
	
	public static OAuthHelper getInstance() {
		if (instance == null) {
			instance = new OAuthHelper();
		}
		return instance;
	}
	
	public void init(ServletConfig config) throws ServletException {
		
		// only need to do this once
		if (sessions != null) {
			return;
		}
		
		try {
			// connect to HZ cluster as client for sharing sessions
			String configFileName = config.getServletContext().getInitParameter("config-location");
			FileSystemXmlConfig hzConfig = new FileSystemXmlConfig(configFileName);
			String hzGroupName = hzConfig.getGroupConfig().getName();
	        String hzGroupPassword = hzConfig.getGroupConfig().getPassword();
	        String hzAddress = hzConfig.getNetworkConfig().getInterfaces().getInterfaces().iterator().next() + ":" + hzConfig.getNetworkConfig().getPort();
	        ClientConfig cc = new ClientConfig();
	        cc.getGroupConfig().setName(hzGroupName);
	        cc.getGroupConfig().setPassword(hzGroupPassword);
	        cc.addAddress(hzAddress);
			HazelcastInstance hzInstance = HazelcastClient.newHazelcastClient(cc);
			String sessionMapName = config.getServletContext().getInitParameter("map-name");
			sessions = hzInstance.getMap(sessionMapName);
			
		} catch (Exception e) {
			throw new ServletException(e);
		}
		
	}
	
	public Map<String, Object> getMap(String sessionId) {
		return this.sessions.get(sessionId);
		
	}
	
	public void saveSession(HttpSession session) {
		// save the session info in the shared map
		Map<String, Object> sessionMap = new HashMap<String, Object>();
		Enumeration attributeNames = session.getAttributeNames();
		while (attributeNames.hasMoreElements()) {
			String name = (String) attributeNames.nextElement();
			Object value = session.getAttribute(name);
			sessionMap.put(name, value);
		}
		sessions.put(session.getId(), sessionMap );
		
	}
	public void saveMap(String sessionId, Map<String, Object> sessionMap) {
		this.sessions.put(sessionId, sessionMap);
		
	}

}
