package org.dataone.portal.session;

import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hazelcast.client.ClientConfig;
import com.hazelcast.client.ClientConfigBuilder;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

public class SessionHelper {

	private static SessionHelper instance = null;
	
	private static Log log = LogFactory.getLog(SessionHelper.class);
	
	private IMap<String, Map<String, Object>> sessions = null;
	
	private SessionHelper() {
		
	}
	
	public static SessionHelper getInstance() {
		if (instance == null) {
			instance = new SessionHelper();
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
			String configFileName = config.getServletContext().getInitParameter("client-config-location");
			log.info("SessionHelper.init ================== the client configuration file path is "+configFileName);
			
			ClientConfig cc = new ClientConfigBuilder(configFileName).build();
			if (log.isDebugEnabled()) {
			    log.debug("SessionHelper.init ================== the group name from the client configuration is " + cc.getGroupConfig().getName());
			    
			    InetSocketAddress address = cc.getAddressList().iterator().next();
			    log.debug("SessionHelper.init ================== the hz address from the client configuration is " + address.getHostString() + ":" + address.getPort());

			    log.debug("SessionHelper.init ================== the reconnection timeout from the client configuration is " + cc.getReConnectionTimeOut());
			    log.debug("SessionHelper.init ================== the reconnection attempts from the client configuration is " + cc.getReconnectionAttemptLimit());
			}
			
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
	
	public Map<String, Object> removeSession(String sessionId) {
		return this.sessions.remove(sessionId);
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
