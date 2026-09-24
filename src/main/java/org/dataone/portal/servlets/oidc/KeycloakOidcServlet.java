package org.dataone.portal.servlets.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.OidcException;
import org.dataone.portal.oidc.OidcResponses;
import org.dataone.portal.servlets.AccountRegistration;
import org.dataone.portal.servlets.RedirectTargets;
import org.dataone.portal.session.PortalSession;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

/**
 * Login through Keycloak (OpenID Connect authorization code flow with PKCE), alongside the direct
 * ORCID login in OrcidOAuthServlet. The endpoint names match the Python dataone-auth package.
 * <ul>
 * <li>{@code GET /login?target=<url>} sends the browser to Keycloak; {@code target} is
 * optional.</li>
 * <li>{@code GET /authorize?code=...&state=...} is the callback. Its URL is configured with
 * {@code keycloak.redirect.uri} and registered on the Keycloak client for each deployment.</li>
 * </ul>
 * A successful login stores the same session attributes (accessToken, userId, name) that
 * TokenServlet uses to issue DataONE JWTs, plus the Keycloak refresh and ID tokens. If the login
 * started with a {@code target}, the browser is then redirected there; otherwise the response is
 * the dataone-auth token payload,
 * {@code {"message": "Success", "token": {"access_token": ..., "refresh_token": ...}}}.
 * See {@link KeycloakProvider} for the settings.
 */
public class KeycloakOidcServlet extends KeycloakServlet {

    private static final long serialVersionUID = 1L;

    private static Log log = LogFactory.getLog(KeycloakOidcServlet.class);

    /** Register logged-in users with the CN if they aren't registered yet (default true) */
    public static final String REGISTER_ACCOUNTS = "keycloak.register.accounts";

    public static final String LOGIN_PATH = "/login";
    public static final String AUTHORIZE_PATH = "/authorize";

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        KeycloakProvider provider = getProvider();
        if (!provider.isLoginConfigured()) {
            OidcResponses.writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                                     OidcResponses.NOT_CONFIGURED, null);
            return;
        }

        String path = request.getServletPath();
        if (LOGIN_PATH.equals(path)) {
            try {
                handleLogin(provider, request, response);
            } catch (Exception e) {
                log.error("Could not start Keycloak login", e);
                writeError(response, toOidcException(e));
            }
        } else if (AUTHORIZE_PATH.equals(path)) {
            handleAuthorize(provider, request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleLogin(KeycloakProvider provider, HttpServletRequest request,
                             HttpServletResponse response) throws Exception {

        // where should we end up afterward?
        String target = request.getParameter("target");
        if (target != null && !RedirectTargets.isAllowed(target)) {
            OidcResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                                     OidcResponses.MISSING_PARAMETER, "Invalid target");
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

    private void handleAuthorize(KeycloakProvider provider, HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {

        // the callback must come back to the session that started the login, with its state
        PortalSession session = PortalSession.find(request);
        String query = request.getQueryString();
        AuthenticationResponse authResponse;
        try {
            authResponse = AuthenticationResponseParser.parse(
                URI.create(provider.getRedirectURI() + (query == null ? "" : "?" + query)));
        } catch (com.nimbusds.oauth2.sdk.ParseException e) {
            OidcResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                                     OidcResponses.MISSING_PARAMETER, e.getMessage());
            return;
        }
        String state = authResponse.getState() == null ? null : authResponse.getState().getValue();
        if (session == null || !session.consumeOAuthState(state)) {
            log.warn("Rejecting Keycloak callback with no session or a mismatched state");
            OidcResponses.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                                     OidcResponses.MISSING_PARAMETER,
                                     "Invalid or expired login request");
            return;
        }
        String nonce = session.getNonce();
        String verifier = session.getPkceVerifier();
        session.clearLoginSecrets();
        String target = session.getTarget();

        Tokens tokens;
        try {
            tokens = completeLogin(provider, request, session, authResponse, nonce, verifier);
        } catch (Exception e) {
            OidcException error = toOidcException(e);
            log.error("Keycloak login failed", e);
            if (target != null) {
                // send the browser back to where it started (checked in handleLogin)
                response.sendRedirect(withParameter(target, "error", "login_failed"));
            } else {
                writeError(response, error);
            }
            return;
        }

        if (target != null) {
            response.sendRedirect(target);
        } else {
            RefreshToken refreshToken = tokens.getRefreshToken();
            OidcResponses.writeTokens(response, "Success", tokens.getAccessToken().getValue(),
                                      refreshToken == null ? null : refreshToken.getValue());
        }
    }

    /**
     * Exchange the code, check the ID token, and record the login in the session.
     * @return the tokens from Keycloak
     */
    private Tokens completeLogin(KeycloakProvider provider, HttpServletRequest request,
                                 PortalSession session, AuthenticationResponse authResponse,
                                 String nonce, String verifier) throws Exception {
        if (!authResponse.indicatesSuccess()) {
            ErrorObject error = ((AuthenticationErrorResponse) authResponse).getErrorObject();
            throw new OidcException(HttpServletResponse.SC_UNAUTHORIZED,
                                    OidcResponses.AUTHORIZATION_FAILED,
                                    error.getCode() + (error.getDescription() == null ? ""
                                        : ": " + error.getDescription()));
        }
        AuthorizationCode code = ((AuthenticationSuccessResponse) authResponse)
            .getAuthorizationCode();

        // exchange the code for tokens, and check the ID token
        Tokens tokens = requestTokens(
            provider, new AuthorizationCodeGrant(code, provider.getRedirectURI(),
                                                 new CodeVerifier(verifier)),
            null, OidcResponses.AUTHORIZATION_FAILED);
        if (!(tokens instanceof OIDCTokens) || ((OIDCTokens) tokens).getIDToken() == null) {
            throw new OidcException(HttpServletResponse.SC_UNAUTHORIZED,
                                    OidcResponses.AUTHORIZATION_FAILED,
                                    "Keycloak did not return an ID token");
        }
        OIDCTokens oidcTokens = (OIDCTokens) tokens;
        IDTokenClaimsSet idClaims = provider.validateIdToken(oidcTokens.getIDToken(),
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
        session.setIdToken(oidcTokens.getIDTokenString());
        session.setUserId(userId);
        session.setName(name);

        registerAccount(userId, idClaims.getStringClaim("given_name"),
                        idClaims.getStringClaim("family_name"));
        return tokens;
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
