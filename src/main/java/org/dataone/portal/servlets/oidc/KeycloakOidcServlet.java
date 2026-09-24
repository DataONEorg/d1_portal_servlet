package org.dataone.portal.servlets.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.servlets.AccountRegistration;
import org.dataone.portal.servlets.RedirectTargets;
import org.dataone.portal.session.PortalSession;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

/**
 * Login through Keycloak (OpenID Connect authorization code flow with PKCE), alongside the direct
 * ORCID login in OrcidOAuthServlet. A successful login stores the same session attributes
 * (accessToken, userId, name) that TokenServlet uses to issue DataONE JWTs, plus the Keycloak
 * refresh and ID tokens.
 * <ul>
 * <li>{@code GET /oidc?action=start&target=<url>} sends the browser to Keycloak.</li>
 * <li>{@code GET /oidc?code=...&state=...} is the callback; its URL is configured with
 * {@code keycloak.redirect.uri} and registered on the Keycloak client for each deployment.</li>
 * </ul>
 * See {@link KeycloakProvider} for the settings.
 */
public class KeycloakOidcServlet extends HttpServlet {

    private static Log log = LogFactory.getLog(KeycloakOidcServlet.class);

    /** Register logged-in users with the CN if they aren't registered yet (default true) */
    public static final String REGISTER_ACCOUNTS = "keycloak.register.accounts";

    /**
     * @return the Keycloak provider; tests override this
     */
    protected KeycloakProvider getProvider() {
        return KeycloakProvider.getInstance();
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        KeycloakProvider provider = getProvider();
        if (!provider.isLoginConfigured()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                               "Keycloak login is not configured");
            return;
        }

        String action = request.getParameter("action");
        try {
            if ("start".equals(action)) {
                handleStart(provider, request, response);
            } else if (action == null) {
                handleCallback(provider, request, response);
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
            }
        } catch (Exception e) {
            log.error("Keycloak login failed (action=" + action + ")", e);
            if (response.isCommitted()) {
                return;
            }
            // send the browser back to where it started, if we know where that was
            PortalSession session = PortalSession.find(request);
            String target = session == null ? null : session.getTarget();
            if (action == null && RedirectTargets.isAllowed(target)) {
                response.sendRedirect(withParameter(target, "error", "login_failed"));
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Login failed");
            }
        }
    }

    private void handleStart(KeycloakProvider provider, HttpServletRequest request,
                             HttpServletResponse response) throws Exception {

        // where should we end up afterward?
        String target = request.getParameter("target");
        if (target != null && !RedirectTargets.isAllowed(target)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid target");
            return;
        }

        State state = new State();
        Nonce nonce = new Nonce();
        CodeVerifier verifier = new CodeVerifier();

        // remember for the callback
        PortalSession session = PortalSession.create(request);
        session.setTarget(target);
        session.setOAuthState(state.getValue());
        session.setLoginSecrets(nonce.getValue(), verifier.getValue());

        AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
            new ResponseType(ResponseType.Value.CODE), Scope.parse(provider.getScope()),
            provider.getClientID(), provider.getRedirectURI())
            .endpointURI(provider.getMetadata().getAuthorizationEndpointURI())
            .state(state)
            .nonce(nonce)
            .codeChallenge(verifier, CodeChallengeMethod.S256)
            .build();

        response.sendRedirect(authRequest.toURI().toString());
    }

    private void handleCallback(KeycloakProvider provider, HttpServletRequest request,
                                HttpServletResponse response) throws Exception {

        // the callback must come back to the session that started the login, with its state
        PortalSession session = PortalSession.find(request);
        String query = request.getQueryString();
        AuthenticationResponse authResponse = AuthenticationResponseParser.parse(
            URI.create(provider.getRedirectURI() + (query == null ? "" : "?" + query)));
        String state = authResponse.getState() == null ? null : authResponse.getState().getValue();
        if (session == null || !session.consumeOAuthState(state)) {
            log.warn("Rejecting Keycloak callback with no session or a mismatched state");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               "Invalid or expired login request");
            return;
        }
        String nonce = session.getNonce();
        String verifier = session.getPkceVerifier();
        session.clearLoginSecrets();

        if (!authResponse.indicatesSuccess()) {
            ErrorObject error = ((AuthenticationErrorResponse) authResponse).getErrorObject();
            throw new IllegalStateException("Keycloak returned an error: " + error.getCode()
                + " " + error.getDescription());
        }
        AuthorizationCode code = ((AuthenticationSuccessResponse) authResponse)
            .getAuthorizationCode();

        // exchange the code for tokens, and check the ID token
        OIDCTokens tokens = requestTokens(provider, code, new CodeVerifier(verifier));
        IDTokenClaimsSet idClaims = provider.validateIdToken(tokens.getIDToken(),
                                                             new Nonce(nonce));
        String userId = provider.getSubject(idClaims.toJWTClaimsSet());
        String name = KeycloakProvider.getName(idClaims.toJWTClaimsSet());

        // prevent session fixation: the logged-in session gets a new id
        request.changeSessionId();
        session.setAuthSource(PortalSession.SOURCE_KEYCLOAK);
        session.setAccessToken(tokens.getAccessToken().getValue());
        session.setExpiresIn(tokens.getAccessToken().getLifetime());
        session.setScope(tokens.getAccessToken().getScope() == null ? null
            : tokens.getAccessToken().getScope().toString());
        RefreshToken refreshToken = tokens.getRefreshToken();
        session.setRefreshToken(refreshToken == null ? null : refreshToken.getValue());
        session.setIdToken(tokens.getIDTokenString());
        session.setUserId(userId);
        session.setName(name);

        registerAccount(userId, idClaims.getStringClaim("given_name"),
                        idClaims.getStringClaim("family_name"));

        String target = session.getTarget();
        if (target != null) {
            // redirect to target (checked in handleStart)
            response.sendRedirect(target);
        } else {
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println("Login complete.");
        }
    }

    /**
     * Exchange an authorization code for tokens at Keycloak's token endpoint.
     */
    protected OIDCTokens requestTokens(KeycloakProvider provider, AuthorizationCode code,
                                       CodeVerifier verifier) throws Exception {
        TokenRequest tokenRequest = new TokenRequest.Builder(
            provider.getMetadata().getTokenEndpointURI(),
            new ClientSecretBasic(provider.getClientID(), new Secret(provider.getClientSecret())),
            new AuthorizationCodeGrant(code, provider.getRedirectURI(), verifier))
            .build();
        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(
            tokenRequest.toHTTPRequest().send());
        if (!tokenResponse.indicatesSuccess()) {
            ErrorObject error = tokenResponse.toErrorResponse().getErrorObject();
            throw new IllegalStateException("Keycloak token request failed: " + error.getCode()
                + " " + error.getDescription());
        }
        return ((OIDCTokenResponse) tokenResponse.toSuccessResponse()).getOIDCTokens();
    }

    /**
     * Register the user with the CN, unless keycloak.register.accounts is false.
     */
    protected void registerAccount(String userId, String givenName, String familyName) {
        if (Settings.getConfiguration().getBoolean(REGISTER_ACCOUNTS, true)) {
            AccountRegistration.registerIfNeeded(userId, givenName, familyName);
        }
    }

    /**
     * Add a query parameter to a URL
     */
    static String withParameter(String url, String name, String value) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
