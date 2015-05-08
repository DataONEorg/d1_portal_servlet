package org.dataone.portal.servlets.ldap;

import java.util.HashMap;
import java.util.Vector;

import org.dataone.configuration.Settings;
import org.dataone.portal.servlets.ldap.AuthLdap;
import org.dataone.service.types.v1.SubjectInfo;
import org.junit.Assert;
import org.junit.Test;

public class AuthLdapTest {

	@Test
	public void testAuthenticate() {
		try {
			AuthLdap auth = new AuthLdap();
			String user = Settings.getConfiguration().getString("ldap.test.username");
			String password = Settings.getConfiguration().getString("ldap.test.password");
			
			// try a good account
			boolean success = auth.authenticate(user, password);
			Assert.assertTrue(success);
			
			// try a bad one
			success = auth.authenticate(user, password + "BAD");
			Assert.assertFalse(success);
			
			// look up user attributes
			HashMap<String, Vector<String>> attributes = auth.getAttributes(user);
			String cn = attributes.get("cn").firstElement();
			Assert.assertNotNull(cn);
			
			// look up subject info
			SubjectInfo info = auth.getSubjectInfo(user);
			Assert.assertNotNull(info.getPerson(0).getFamilyName());
			
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
		
	}
}
