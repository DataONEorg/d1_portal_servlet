/**
 *  '$RCSfile$'
 *    Purpose: An implementation of the AuthInterface interface that
 *             allows Metacat to use the LDAP protocol for
 *             directory services
 *  Copyright: 2000 Regents of the University of California and the
 *             National Center for Ecological Analysis and Synthesis
 *    Authors: Matt Jones
 *
 *   '$Author$'
 *     '$Date$'
 * '$Revision$'
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.dataone.portal.servlets.ldap;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ReferralException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;

/**
 * An implementation of the AuthInterface interface that allows Metacat to use
 * the LDAP protocol for directory services. The LDAP authentication service is
 * used to determine if a user is authenticated, and whether they are a member
 * of a particular group.
 */
public class AuthLdap {
	private String ldapUrl;
	private String ldapBase;
	private String referral;
	private String ldapConnectTimeLimit;
	private int ldapSearchTimeLimit;
	private int ldapSearchCountLimit;
	private String currentReferralInfo;
	Hashtable<String, String> env = new Hashtable<String, String>(11);
	ReferralException refExc;

	private static Log log = LogFactory.getLog(AuthLdap.class);

	/**
	 * Construct an AuthLdap
	 */
	public AuthLdap() throws InstantiationException {
		// Read LDAP URI for directory service information
		try {
			this.ldapUrl = Settings.getConfiguration().getString("ldap.url");
			this.ldapBase = Settings.getConfiguration().getString("ldap.base");
			this.referral = Settings.getConfiguration().getString("ldap.referral");
			this.ldapConnectTimeLimit = Settings.getConfiguration().getString("ldap.connectTimeLimit");
			this.ldapSearchTimeLimit = Integer.parseInt(Settings.getConfiguration().getString("ldap.searchTimeLimit"));
			this.ldapSearchCountLimit = Integer.parseInt(Settings.getConfiguration().getString("ldap.searchCountLimit"));
		} catch (Exception e) {
			throw new InstantiationException(
					"Could not instantiate AuthLdap - "
							+ e.getMessage());
		}

		// Store referral info for use in building group DNs in getGroups()
		this.currentReferralInfo = "";
	}

	/**
	 * Determine if a user/password are valid according to the authentication
	 * service.
	 * 
	 * @param user
	 *            the name of the principal to authenticate
	 * @param password
	 *            the password to use for authentication
	 * @returns boolean true if authentication successful, false otherwise
	 */
	public boolean authenticate(String user, String password) throws ConnectException {
		String ldapUrl = this.ldapUrl;
		String ldapBase = this.ldapBase;
		boolean authenticated = false;
		String identifier = user;

		// get uid here.
		if (user.indexOf(",") == -1) {
			throw new ConnectException("Invalid LDAP user credential: " + user
					+ ".  Missing ','");
		}
		String uid = user.substring(0, user.indexOf(","));
		user = user.substring(user.indexOf(","), user.length());

		log.debug("AuthLdap.authenticate - identifier: " + identifier + 
				", uid: " + uid +", user: " + user);

		try {
			// Check the usename as passed in
			log.info("AuthLdap.authenticate - Calling ldapAuthenticate" +
				" with user as identifier: " + identifier);

			authenticated = ldapAuthenticate(identifier, password);
			// if not found, try looking up a valid DN then auth again
			if (!authenticated) {
				log.info("AuthLdap.authenticate - Not Authenticated");
				log.info("AuthLdap.authenticate - Looking up DN for: " + identifier);
				identifier = getIdentifyingName(identifier, ldapUrl, ldapBase);
				if (identifier == null) {
					log.info("AuthLdap.authenticate - No DN found from getIdentifyingName");
					return authenticated;
				}

				log.info("AuthLdap.authenticate - DN found from getIdentifyingName: " + identifier);
				String decoded = URLDecoder.decode(identifier);
				log.info("AuthLdap.authenticate - DN decoded: " + decoded);
				identifier = decoded;
				String refUrl = "";
				String refBase = "";
				if (identifier.startsWith("ldap")) {
					log.debug("AuthLdap.authenticate - identifier starts with \"ldap\"");

					refUrl = identifier.substring(0, identifier.lastIndexOf("/") + 1);
					int position = identifier.indexOf(",");
					int position2 = identifier.indexOf(",", position + 1);

					refBase = identifier.substring(position2 + 1);
					identifier = identifier.substring(identifier.lastIndexOf("/") + 1);

					log.info("AuthLdap.authenticate - Calling ldapAuthenticate: " +
						"with user as identifier: " + identifier + " and refUrl as: " + 
						refUrl + " and refBase as: " + refBase);

					authenticated = ldapAuthenticate(identifier, password, refUrl, refBase);
				} else {
					log.info("AuthLdap.authenticate - identifier doesnt start with ldap");
					identifier = identifier + "," + ldapBase;

					log.info("AuthLdap.authenticate - Calling ldapAuthenticate" + 
							"with user as identifier: " + identifier);

					authenticated = ldapAuthenticate(identifier, password);
				}
			}
		} catch (NullPointerException npe) {
			log.error("AuthLdap.authenticate - NullPointerException while authenticating in "
					+ "AuthLdap.authenticate: " + npe);
			npe.printStackTrace();

			throw new ConnectException("AuthLdap.authenticate - NullPointerException while authenticating in "
					+ "AuthLdap.authenticate: " + npe);
		} catch (NamingException ne) {
			log.error("AuthLdap.authenticate - Naming exception while authenticating in "
					+ "AuthLdap.authenticate: " + ne);
			ne.printStackTrace();
		}

		return authenticated;
	}

	/**
	 * Connect to the LDAP directory and do the authentication using the
	 * username and password as passed into the routine.
	 * 
	 * @param identifier
	 *            the distinguished name to check against LDAP
	 * @param password
	 *            the password for authentication
	 */
	private boolean ldapAuthenticate(String identifier, String password) throws ConnectException, NamingException,
			NullPointerException {
		return ldapAuthenticate(identifier, password, this.ldapUrl, this.ldapBase);
	}

	/**
	 * Connect to the LDAP directory and do the authentication using the
	 * username and password as passed into the routine.
	 * 
	 * @param identifier
	 *            the distinguished name to check against LDAP
	 * @param password
	 *            the password for authentication
	 */

	private boolean ldapAuthenticate(String dn, String password, String rootServer, String rootBase) {

		boolean authenticated = false;

		String server = "";
		String userDN = "";
		log.info("AuthLdap.ldapAuthenticate - dn is: " + dn);

		int position = dn.lastIndexOf("/");
		log.debug("AuthLdap.ldapAuthenticate - position is: " + position);
		if (position == -1) {
			server = rootServer;
			if (dn.indexOf(userDN) < 0) {
				userDN = dn + "," + rootBase;
			} else {
				userDN = dn;
			}
			log.info("AuthLdap.ldapAuthenticate - userDN is: " + userDN);

		} else {
			server = dn.substring(0, position + 1);
			userDN = dn.substring(position + 1);
			log.info("AuthLdap.ldapAuthenticate - server is: " + server);
			log.info("AuthLdap.ldapAuthenticate - userDN is: " + userDN);
		}

		log.warn("AuthLdap.ldapAuthenticate - Trying to authenticate: " + 
				userDN + " Using server: " + server);

		
		Hashtable<String, String> env = new Hashtable<String, String>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, server);
		env.put(Context.REFERRAL, "throw");
		
		try {
			authenticated = authenticateTLS(env, userDN, password);
		} catch (AuthenticationException ee) {
		    log.info("AuthLdap.ldapAuthenticate - failed to login : "+ee.getMessage());
		    String aliasedDn = null;
		    try {
		        aliasedDn = getAliasedDnTLS(userDN, env);
		        if(aliasedDn != null) {
		            log.warn("AuthLdap.ldapAuthenticate - an aliased object " + aliasedDn + " was found for the DN "+userDN+". We will try to authenticate this new DN "+aliasedDn+".");
		            authenticated = authenticateTLS(env, aliasedDn, password);
		        }
		    } catch (NamingException e) {
		        log.error("AuthLdap.ldapAuthenticate - NamingException "+e.getMessage()+" happend when the ldap server authenticated the aliased object "+aliasedDn);
		    } catch (IOException e) {
		        log.error("AuthLdap.ldapAuthenticate - IOException "+e.getMessage()+" happend when the ldap server authenticated the aliased object "+aliasedDn);
		    }
		}
		

		return authenticated;
	}
	
	public SubjectInfo getSubjectInfo(String username) {
		SubjectInfo info = new SubjectInfo();
		Person person = new Person();
		Subject subject = new Subject();
		subject.setValue(username);
		person.setSubject(subject);
		// get attributes
		try {
			HashMap<String, Vector<String>> attributes = this.getAttributes(username);
			if (attributes.containsKey("mail")) {
				person.addEmail(attributes.get("mail").firstElement());
			}
			if (attributes.containsKey("givenName")) {
				person.addGivenName(attributes.get("givenName").firstElement());
			}
			if (attributes.containsKey("sn")) {
				person.setFamilyName(attributes.get("sn").firstElement());
			}
			
		} catch (Exception e) {
			log.warn("Error looking up person", e);
		}
		
		// get group membership
		try {
			String[][] groups = this.getGroups(null, null, username);
			for (String[] group: groups) {
				Group g = new Group();
				Subject groupSubject = new Subject();
				groupSubject.setValue(group[0]);
				g.setSubject(groupSubject);
				g.addHasMember(subject);
				person.addIsMemberOf(groupSubject);
				info.addGroup(g);
			}
			
		} catch (ConnectException e) {
			log.warn("Error looking up group", e);
		}
		
		info.addPerson(person);

		return info;
	}
	
	
	/*
	 * Get the aliased dn through a TLS connection. The null will be returned if there is no real name associated with the alias
	 */
	private String getAliasedDnTLS(String alias, Hashtable<String, String> env) throws NamingException, IOException {
	    boolean useTLS = true;
	    return getAliasedDn(alias, env, useTLS);
	}
	
	/*
	 * Get the aliasedDN (the real DN) for a specified an alias name
	 */
	private String getAliasedDn(String alias, Hashtable<String, String> env, boolean useTLS) throws NamingException, IOException  {
	    String aliasedDn = null;
	    if(env != null) {
	        env.put(Context.REFERRAL, "ignore");
	    }
        LdapContext sctx = new InitialLdapContext(env, null);
        StartTlsResponse tls = null;
        if(useTLS) {
            tls = (StartTlsResponse) sctx.extendedOperation(new StartTlsRequest());
            // Open a TLS connection (over the existing LDAP association) and get details
            // of the negotiated TLS session: cipher suite, peer certificate, etc.
            SSLSession session = tls.negotiate();
        }
        SearchControls ctls = new SearchControls();
        ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        String filter = "(objectClass=*)";
        NamingEnumeration answer  = sctx.search(alias, filter, ctls);
        while(answer.hasMore()) {
            SearchResult result = (SearchResult) answer.next();
            if(!result.isRelative()) {
                //if is not relative, this will be alias.
                aliasedDn = result.getNameInNamespace();
                break;
            }
        }
        if(useTLS && tls != null) {
            tls.close();
        }
        sctx.close();
        return aliasedDn;
	    
	}
	
	private boolean authenticateTLS(Hashtable<String, String> env, String userDN, String password)
			throws AuthenticationException{	
		log.info("AuthLdap.authenticateTLS - Trying to authenticate with TLS");
		try {
			LdapContext ctx = null;
			double startTime;
			double stopTime;
			startTime = System.currentTimeMillis();
			ctx = new InitialLdapContext(env, null);
			// Start up TLS here so that we don't pass our jewels in
			// cleartext
			StartTlsResponse tls = 
				(StartTlsResponse) ctx.extendedOperation(new StartTlsRequest());
			// tls.setHostnameVerifier(new SampleVerifier());
			SSLSession sess = tls.negotiate();
			ctx.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
			ctx.addToEnvironment(Context.SECURITY_PRINCIPAL, userDN);
			ctx.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
			ctx.reconnect(null);
			stopTime = System.currentTimeMillis();
			log.info("AuthLdap.authenticateTLS - Connection time thru "
					+ ldapUrl + " was: " + (stopTime - startTime) / 1000 + " seconds.");
		} catch (AuthenticationException ae) {
            log.warn("AuthLdap.authenticateTLS - Authentication exception: " + ae.getMessage());
            throw ae; 
		} catch (NamingException ne) {
			throw new AuthenticationException("AuthLdap.authenticateTLS - Naming error when athenticating via TLS: " + ne.getMessage());
		} catch (IOException ioe) {
			throw new AuthenticationException("AuthLdap.authenticateTLS - I/O error when athenticating via TLS: " + ioe.getMessage());
		}
		return true;
	}
	

	/**
	 * Get the identifying name for a given userid or name. This is the name
	 * that is used in conjunction withthe LDAP BaseDN to create a distinguished
	 * name (dn) for the record
	 * 
	 * @param user
	 *            the user for which the identifying name is requested
	 * @returns String the identifying name for the user, or null if not found
	 */
	private String getIdentifyingName(String user, String ldapUrl, String ldapBase)
			throws NamingException {

		String identifier = null;
		Hashtable env = new Hashtable();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.REFERRAL, "throw");
		env.put(Context.PROVIDER_URL, ldapUrl + ldapBase);
		try {
			int position = user.indexOf(",");
			String uid = user.substring(user.indexOf("=") + 1, position);
			log.info("AuthLdap.getIdentifyingName - uid is: " + uid);
			String org = user.substring(user.indexOf("=", position + 1) + 1, user
					.indexOf(",", position + 1));
			log.info("AuthLdap.getIdentifyingName - org is: " + org);

			DirContext sctx = new InitialDirContext(env);
			SearchControls ctls = new SearchControls();
			ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			String filter = "(&(uid=" + uid + ")(o=" + org + "))";
			log.warn("AuthLdap.getIdentifyingName - Searching for DNs with following filter: " + filter);

			for (boolean moreReferrals = true; moreReferrals;) {
				try {
					// Perform the search
				    
					NamingEnumeration answer = sctx.search("", filter, ctls);

					// Return the answer
					while (answer.hasMore()) {
						SearchResult sr = (SearchResult) answer.next();
						identifier = sr.getName();
						return identifier;
					}
					// The search completes with no more referrals
					moreReferrals = false;
				} catch (ReferralException e) {
					log.info("AuthLdap.getIdentifyingName - Got referral: " + e.getReferralInfo());
					// Point to the new context from the referral
					if (moreReferrals) {
						// try following referral, skip if error
						boolean referralError = true;
						while (referralError) {
							try {
								sctx = (DirContext) e.getReferralContext();
								referralError = false;
							}
							catch (NamingException ne) {
								log.error("NamingException when getting referral contex. Skipping this referral. " + ne.getMessage());
								e.skipReferral();
								referralError = true;
							}
						}
					}
				}				
			}
		} catch (NamingException e) {
			log.error("AuthLdap.getIdentifyingName - Naming exception while getting dn: " + e);
			throw new NamingException("Naming exception in AuthLdap.getIdentifyingName: "
					+ e);
		}
		return identifier;
	}

	/**
	 * Get all users from the authentication service
	 * 
	 * @param user
	 *            the user for authenticating against the service
	 * @param password
	 *            the password for authenticating against the service
	 * @returns string array of all of the user names
	 */
	public String[] getUserInfo(String user, String password) throws ConnectException {
		String[] userinfo = new String[3];

		log.info("AuthLdap.getUserInfo - get the user info for user  "+user);
		// Identify service provider to use
		Hashtable env = new Hashtable(11);
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");	
		env.put(Context.PROVIDER_URL, ldapUrl);
		String realName = null;
		try {
		    realName = getAliasedDnTLS(user,env);
		} catch(Exception e) {
		    log.warn("AuthLdap.getUserInfo - can't get the alias name for the user "+user+" since "+e.getMessage());
		}
		log.info("AuthLdap.getUserInfo - the aliased dn for "+user+" is "+realName);
		if(realName != null) {
		    //the the user is an alias name. we need to use the the real name
		    user = realName;
		}

		try {

			// Create the initial directory context
		    env.put(Context.REFERRAL, referral);
			DirContext ctx = new InitialDirContext(env);
			// Specify the attributes to match.
			// Users are objects that have the attribute
			// objectclass=InetOrgPerson.
			SearchControls ctls = new SearchControls();
			String[] attrIDs = { "cn", "o", "mail" };
			ctls.setReturningAttributes(attrIDs);
			ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			// ctls.setCountLimit(1000);
			// create the filter based on the uid

			String filter = null;

			/*if (user.indexOf("o=") > 0) {
				String tempStr = user.substring(user.indexOf("o="));
				filter = "(&(" + user.substring(0, user.indexOf(",")) + ")("
						+ tempStr.substring(0, tempStr.indexOf(",")) + "))";
			} else {
				filter = "(&(" + user.substring(0, user.indexOf(",")) + "))";
			}*/
			filter = "(&(" + user.substring(0, user.indexOf(",")) + "))";

			NamingEnumeration namingEnum = ctx.search(user, filter, ctls);

			Attributes tempAttr = null;
			try {
				while (namingEnum.hasMore()) {
					SearchResult sr = (SearchResult) namingEnum.next();
					tempAttr = sr.getAttributes();

					if ((tempAttr.get("cn") + "").startsWith("cn: ")) {
						userinfo[0] = (tempAttr.get("cn") + "").substring(4);
					} else {
						userinfo[0] = (tempAttr.get("cn") + "");
					}

					if ((tempAttr.get("o") + "").startsWith("o: ")) {
						userinfo[1] = (tempAttr.get("o") + "").substring(3);
					} else {
						userinfo[1] = (tempAttr.get("o") + "");
					}

					if ((tempAttr.get("mail") + "").startsWith("mail: ")) {
						userinfo[2] = (tempAttr.get("mail") + "").substring(6);
					} else {
						userinfo[2] = (tempAttr.get("mail") + "");
					}
				}
			} catch (SizeLimitExceededException slee) {
				log.error("AuthLdap.getUserInfo - LDAP Server size limit exceeded. "
						+ "Returning incomplete record set.");
			}

			// Close the context when we're done
			ctx.close();

		} catch (NamingException e) {
			log.error("AuthLdap.getUserInfo - Problem getting users:" + e);
			// e.printStackTrace(System.err);
			throw new ConnectException("Problem getting users in AuthLdap.getUsers:" + e);
		}

		return userinfo;
	}

	/**
	 * Get the users for a particular group from the authentication service
	 * 
	 * @param group
	 *            the group whose user list should be returned
	 * @returns string array of the user names belonging to the group
	 */
	private String[] getUsers(String group)
			throws ConnectException {
		String[] users = null;

		// Identify service provider to use
		Hashtable env = new Hashtable(11);
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.REFERRAL, referral);
		env.put(Context.PROVIDER_URL, ldapUrl);

		try {

			// Create the initial directory context
			DirContext ctx = new InitialDirContext(env);

			// Specify the ids of the attributes to return
			String[] attrIDs = { "uniqueMember" };

			Attributes answer = ctx.getAttributes(group, attrIDs);

			Vector uvec = new Vector();
			try {
				for (NamingEnumeration ae = answer.getAll(); ae.hasMore();) {
					Attribute attr = (Attribute) ae.next();
					for (NamingEnumeration e = attr.getAll(); e.hasMore(); uvec.add(e
							.next())) {
						;
					}
				}
			} catch (SizeLimitExceededException slee) {
				log.error("AuthLdap.getUsers - LDAP Server size limit exceeded. "
						+ "Returning incomplete record set.");
			}

			// initialize users[]; fill users[]
			users = new String[uvec.size()];
			for (int i = 0; i < uvec.size(); i++) {
				users[i] = (String) uvec.elementAt(i);
			}

			// Close the context when we're done
			ctx.close();

		} catch (NamingException e) {
			log.error("AuthLdap.getUsers - Problem getting users for a group in "
					+ "AuthLdap.getUsers:" + e);
			/*
			 * throw new ConnectException( "Problem getting users for a group in
			 * AuthLdap.getUsers:" + e);
			 */
		}

		return users;
	}

	/**
	 * Get the groups for a particular user from the authentication service
	 * 
	 * @param user
	 *            the user for authenticating against the service
	 * @param password
	 *            the password for authenticating against the service
	 * @param foruser
	 *            the user whose group list should be returned
	 * @returns string array of the group names
	 */
	private String[][] getGroups(String user, String password, String foruser)
			throws ConnectException {

		log.debug("AuthLdap.getGroups - getGroups() called.");

		// create vectors to store group and dscription values returned from the
		// ldap servers
		Vector gvec = new Vector();
		Vector desc = new Vector();
		Attributes tempAttr = null;
		Attributes rsrAttr = null;

		// DURING getGroups(), DO WE NOT BIND USING userName AND userPassword??
		// NEED TO FIX THIS ...
		// Identify service provider to use
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.REFERRAL, "throw");
		env.put(Context.PROVIDER_URL, ldapUrl);
		env.put("com.sun.jndi.ldap.connect.timeout", ldapConnectTimeLimit);
		
		// Iterate through the referrals, handling NamingExceptions in the
		// outer catch statement, ReferralExceptions in the inner catch
		// statement
		try { // outer try

			// Create the initial directory context
			DirContext ctx = new InitialDirContext(env);

			// Specify the attributes to match.
			// Groups are objects with attribute objectclass=groupofuniquenames.
			// and have attribute uniquemember: uid=foruser,ldapbase.
			SearchControls ctls = new SearchControls();
			// Specify the ids of the attributes to return
			String[] attrIDs = { "cn", "o", "description" };
			ctls.setReturningAttributes(attrIDs);
			// set the ldap search scope
			ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			// set a 10 second time limit on searches to limit non-responding
			// servers
			ctls.setTimeLimit(ldapSearchTimeLimit);
			// return at most 20000 entries
			ctls.setCountLimit(ldapSearchCountLimit);

			// build the ldap search filter that represents the "group" concept
			String filter = null;
			String gfilter = "(objectClass=groupOfUniqueNames)";
			if (null == foruser) {
				filter = gfilter;
			} else {
				filter = "(& " + gfilter + "(uniqueMember=" + foruser + "))";
			}
			log.info("AuthLdap.getGroups - group filter is: " + filter);

			// now, search and iterate through the referrals
			for (boolean moreReferrals = true; moreReferrals;) {
				try { // inner try

					NamingEnumeration namingEnum = ctx.search(ldapBase, filter, ctls);

					// Print the groups
					while (namingEnum.hasMore()) {
						SearchResult sr = (SearchResult) namingEnum.next();

						tempAttr = sr.getAttributes();

						if ((tempAttr.get("description") + "")
								.startsWith("description: ")) {
							desc.add((tempAttr.get("description") + "").substring(13));
						} else {
							desc.add(tempAttr.get("description") + "");
						}

						// check for an absolute URL value or an answer value
						// relative
						// to the target context
						if (!sr.getName().startsWith("ldap") && sr.isRelative()) {
							log.debug("AuthLdap.getGroups - Search result entry is relative ...");
							gvec.add(sr.getName() + "," + ldapBase);
							log.info("AuthLdap.getGroups - group " + sr.getName() + "," + ldapBase
									+ " added to the group vector");
						} else {
							log.debug("AuthLdap.getGroups - Search result entry is absolute ...");

							// search the top level directory for referral
							// objects and match
							// that of the search result's absolute URL. This
							// will let us
							// rebuild the group name from the search result,
							// referral point
							// in the top directory tree, and ldapBase.

							// configure a new directory search first
							Hashtable envHash = new Hashtable(11);
							// Identify service provider to use
							envHash.put(Context.INITIAL_CONTEXT_FACTORY,
									"com.sun.jndi.ldap.LdapCtxFactory");
							envHash.put(Context.REFERRAL, "ignore");
							envHash.put(Context.PROVIDER_URL, ldapUrl);
							envHash.put("com.sun.jndi.ldap.connect.timeout",
									ldapConnectTimeLimit);

							try {
								// Create the initial directory context
								DirContext DirCtx = new InitialDirContext(envHash);

								SearchControls searchCtls = new SearchControls();
								// Specify the ids of the attributes to return
								String[] attrNames = { "o" };
								searchCtls.setReturningAttributes(attrNames);
								// set the ldap search scope - only look for top
								// level referrals
								searchCtls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
								// set a time limit on searches to limit
								// non-responding servers
								searchCtls.setTimeLimit(ldapSearchTimeLimit);
								// return the configured number of entries
								searchCtls.setCountLimit(ldapSearchCountLimit);

								// Specify the attributes to match.
								// build the ldap search filter to match
								// referral entries that
								// match the search result
								String rFilter = "(&(objectClass=referral)(ref="
										+ currentReferralInfo.substring(0,
												currentReferralInfo.indexOf("?")) + "))";
								log.debug("AuthLdap.getGroups - rFilter is: " + rFilter);

								NamingEnumeration rNamingEnum = DirCtx.search(ldapBase,
										rFilter, searchCtls);

								while (rNamingEnum.hasMore()) {
									SearchResult rsr = (SearchResult) rNamingEnum.next();
									rsrAttr = rsr.getAttributes();
									log.debug("AuthLdap.getGroups - referral search result is: "
											+ rsr.toString());

									// add the returned groups to the group
									// vector. Test the
									// syntax of the returned attributes -
									// sometimes they are
									// preceded with the attribute id and a
									// colon
									if ((tempAttr.get("cn") + "").startsWith("cn: ")) {
										gvec.add("cn="
												+ (tempAttr.get("cn") + "").substring(4)
												+ "," + "o="
												+ (rsrAttr.get("o") + "").substring(3)
												+ "," + ldapBase);
										log.info("AuthLdap.getGroups - group "
												+ (tempAttr.get("cn") + "").substring(4)
												+ "," + "o="
												+ (rsrAttr.get("o") + "").substring(3)
												+ "," + ldapBase
												+ " added to the group vector");
									} else {
										gvec.add("cn=" + tempAttr.get("cn") + "," + "o="
												+ rsrAttr.get("o") + "," + ldapBase);
										log.info("AuthLdap.getGroups - group " + "cn="
												+ tempAttr.get("cn") + "," + "o="
												+ rsrAttr.get("o") + "," + ldapBase
												+ " added to the group vector");
									}
								}

							} catch (NamingException nameEx) {
								log.debug("AuthLdap.getGroups - Caught naming exception: ");
								nameEx.printStackTrace(System.err);
							}
						}
					}// end while

					moreReferrals = false;

				} catch (ReferralException re) {

					log
							.info("AuthLdap.getGroups -  caught referral exception: "
									+ re.getReferralInfo());
					this.currentReferralInfo = (String) re.getReferralInfo();

					// set moreReferrals to true and set the referral context
					moreReferrals = true;
					
					// try following referral, skip if error
					boolean referralError = true;
					while (referralError) {
						try {
							ctx = (DirContext) re.getReferralContext();
							referralError = false;
						}
						catch (NamingException ne) {
							log.error("NamingException when getting referral contex. Skipping this referral. " + ne.getMessage());
							re.skipReferral();
							referralError = true;
						}
					}

				}// end inner try
			}// end for

			// close the context now that all initial and referral
			// searches are processed
			ctx.close();

		} catch (NamingException e) {

			// naming exceptions get logged, groups are returned
			log.info("AuthLdap.getGroups - caught naming exception: ");
			e.printStackTrace(System.err);

		} finally {
			// once all referrals are followed, report and return the groups
			// found
			log.warn("AuthLdap.getGroups - The user is in the following groups: " + gvec.toString());
			// build and return the groups array
			String groups[][] = new String[gvec.size()][2];
			for (int i = 0; i < gvec.size(); i++) {
				groups[i][0] = (String) gvec.elementAt(i);
				groups[i][1] = (String) desc.elementAt(i);
			}
			return groups;
		}// end outer try
	}

	/**
	 * Get attributes describing a user or group
	 * 
	 * @param foruser
	 *            the user for which the attribute list is requested
	 * @returns HashMap a map of attribute name to a Vector of values
	 */
	public HashMap<String, Vector<String>> getAttributes(String foruser) throws ConnectException {
		HashMap<String, Vector<String>> attributes = new HashMap<String, Vector<String>>();
		String ldapUrl = this.ldapUrl;

		// Identify service provider to use
		Hashtable env = new Hashtable(11);
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.REFERRAL, referral);
		env.put(Context.PROVIDER_URL, ldapUrl);

		try {

			// Create the initial directory context
			DirContext ctx = new InitialDirContext(env);

			// Ask for all attributes of the user
			// Attributes attrs = ctx.getAttributes(userident);
			Attributes attrs = ctx.getAttributes(foruser);

			// Print all of the attributes
			NamingEnumeration en = attrs.getAll();
			while (en.hasMore()) {
				Attribute att = (Attribute) en.next();
				Vector<String> values = new Vector();
				String attName = att.getID();
				NamingEnumeration attvalues = att.getAll();
				while (attvalues.hasMore()) {
				    try {
				        String value = (String) attvalues.next();
				        values.add(value);
				    } catch (ClassCastException cce) {
				        log.debug("Could not cast LDAP attribute (" +
				                attName + ") to a String value, so skipping.");
				    }
				}
				attributes.put(attName, values);
			}

			// Close the context when we're done
			ctx.close();
		} catch (NamingException e) {
			log.error("AuthLdap.getAttributes - Problem getting attributes:"
					+ e);
			throw new ConnectException(
					"Problem getting attributes in AuthLdap.getAttributes:" + e);
		}

		return attributes;
	}

	
}
