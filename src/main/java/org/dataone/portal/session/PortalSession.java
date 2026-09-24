package org.dataone.portal.session;

import java.security.SecureRandom;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Typed access to the login state that the portal keeps in the servlet container's HttpSession.
 * A login servlet (such as OrcidOAuthServlet) records who logged in; TokenServlet reads it back to
 * issue a DataONE JWT, and LogoutServlet clears it.
 */
public class PortalSession {

    public static final String ACCESS_TOKEN = "accessToken";
    public static final String USER_ID = "userId";
    public static final String NAME = "name";
    public static final String EXPIRES_IN = "expiresIn";
    public static final String SCOPE = "scope";
    public static final String ORCID = "orcid";
    public static final String TARGET = "target";
    public static final String OAUTH_STATE = "oauthState";
    public static final String AUTH_SOURCE = "authSource";
    public static final String REFRESH_TOKEN = "refreshToken";
    public static final String ID_TOKEN = "idToken";
    public static final String OIDC_NONCE = "oidcNonce";
    public static final String PKCE_VERIFIER = "pkceVerifier";

    /** {@link #AUTH_SOURCE} for logins through the direct ORCID OAuth servlet */
    public static final String SOURCE_ORCID = "orcid";

    /** {@link #AUTH_SOURCE} for logins through Keycloak OIDC */
    public static final String SOURCE_KEYCLOAK = "keycloak";

    private static final SecureRandom random = new SecureRandom();

    private final HttpSession session;

    private PortalSession(HttpSession session) {
        this.session = session;
    }

    /**
     * Get the portal session for a request, creating the HttpSession if needed.
     */
    public static PortalSession create(HttpServletRequest request) {
        return new PortalSession(request.getSession(true));
    }

    /**
     * Get the portal session for a request, or null if the request has no HttpSession. Use this
     * for read-only lookups so anonymous requests don't create sessions.
     */
    public static PortalSession find(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : new PortalSession(session);
    }

    /**
     * Store an OAuth state value generated elsewhere (for example by an OIDC library), to be
     * checked with {@link #consumeOAuthState(String)}.
     */
    public void setOAuthState(String state) {
        session.setAttribute(OAUTH_STATE, state);
    }

    /**
     * Generate a random OAuth state value, store it in the session, and return it.
     */
    public String newOAuthState() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(OAUTH_STATE, state);
        return state;
    }

    /**
     * Check a returned OAuth state value against the stored one. The stored value is removed so
     * that it can only be used once.
     * @return true if the state matches the value stored by {@link #newOAuthState()}
     */
    public boolean consumeOAuthState(String state) {
        Object expected = session.getAttribute(OAUTH_STATE);
        session.removeAttribute(OAUTH_STATE);
        return expected != null && expected.equals(state);
    }

    /**
     * @return true if a user has logged in on this session
     */
    public boolean isLoggedIn() {
        return getAccessToken() != null;
    }

    public String getAccessToken() {
        return (String) session.getAttribute(ACCESS_TOKEN);
    }

    public void setAccessToken(String accessToken) {
        session.setAttribute(ACCESS_TOKEN, accessToken);
    }

    public String getUserId() {
        return (String) session.getAttribute(USER_ID);
    }

    public void setUserId(String userId) {
        session.setAttribute(USER_ID, userId);
    }

    public String getName() {
        return (String) session.getAttribute(NAME);
    }

    public void setName(String name) {
        session.setAttribute(NAME, name);
    }

    public Long getExpiresIn() {
        return (Long) session.getAttribute(EXPIRES_IN);
    }

    public void setExpiresIn(Long expiresIn) {
        session.setAttribute(EXPIRES_IN, expiresIn);
    }

    public String getScope() {
        return (String) session.getAttribute(SCOPE);
    }

    public void setScope(String scope) {
        session.setAttribute(SCOPE, scope);
    }

    public String getOrcid() {
        return (String) session.getAttribute(ORCID);
    }

    public void setOrcid(String orcid) {
        session.setAttribute(ORCID, orcid);
    }

    public String getTarget() {
        return (String) session.getAttribute(TARGET);
    }

    public void setTarget(String target) {
        session.setAttribute(TARGET, target);
    }

    public String getAuthSource() {
        return (String) session.getAttribute(AUTH_SOURCE);
    }

    public void setAuthSource(String authSource) {
        session.setAttribute(AUTH_SOURCE, authSource);
    }

    public String getRefreshToken() {
        return (String) session.getAttribute(REFRESH_TOKEN);
    }

    public void setRefreshToken(String refreshToken) {
        session.setAttribute(REFRESH_TOKEN, refreshToken);
    }

    public String getIdToken() {
        return (String) session.getAttribute(ID_TOKEN);
    }

    public void setIdToken(String idToken) {
        session.setAttribute(ID_TOKEN, idToken);
    }

    /**
     * Store the OIDC nonce and PKCE code verifier for a login in progress.
     */
    public void setLoginSecrets(String nonce, String pkceVerifier) {
        session.setAttribute(OIDC_NONCE, nonce);
        session.setAttribute(PKCE_VERIFIER, pkceVerifier);
    }

    public String getNonce() {
        return (String) session.getAttribute(OIDC_NONCE);
    }

    public String getPkceVerifier() {
        return (String) session.getAttribute(PKCE_VERIFIER);
    }

    /**
     * Remove the OIDC nonce and PKCE code verifier once a login has completed.
     */
    public void clearLoginSecrets() {
        session.removeAttribute(OIDC_NONCE);
        session.removeAttribute(PKCE_VERIFIER);
    }

    /**
     * End the session and discard all login state.
     */
    public void invalidate() {
        session.invalidate();
    }
}
