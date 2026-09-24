package org.dataone.portal.servlets.oidc;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.OidcException;
import org.dataone.portal.oidc.OidcResponses;

import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.oauth2.sdk.token.Tokens;

/**
 * {@code POST /refresh}: exchange a Keycloak refresh token for new tokens, adding the client
 * secret server-side (the d1-confidential client can't refresh without it). Compatible with the
 * Python dataone-auth package:
 * <ul>
 * <li>Request body: {@code {"refresh_token": "...", "scope": "..."}} (scope is optional)</li>
 * <li>Response: {@code {"message": "Token refresh successful", "token": {"access_token": ...,
 * "refresh_token": ...}}}</li>
 * <li>Errors: {@code {"error": {"message": ..., "details": ...}}}, with 401 for a missing,
 * invalid or expired refresh token</li>
 * </ul>
 * Clients can then exchange the new access token at /token for a DataONE JWT.
 */
public class KeycloakRefreshServlet extends KeycloakServlet {

    private static final long serialVersionUID = 1L;

    private static Log log = LogFactory.getLog(KeycloakRefreshServlet.class);

    /** Longest request body accepted */
    private static final int MAX_BODY_LENGTH = 2 * KeycloakProvider.MAX_TOKEN_LENGTH;

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        KeycloakProvider provider = getProvider();
        if (!provider.isLoginConfigured()) {
            OidcResponses.writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                                     OidcResponses.NOT_CONFIGURED, null);
            return;
        }

        try {
            Map<String, Object> body = readJsonBody(request);
            Object refreshToken = body.get("refresh_token");
            if (!(refreshToken instanceof String) || ((String) refreshToken).trim().isEmpty()
                || ((String) refreshToken).length() > KeycloakProvider.MAX_TOKEN_LENGTH) {
                // dataone-auth reports a missing refresh token as a token error (401)
                throw new OidcException(HttpServletResponse.SC_UNAUTHORIZED,
                                        OidcResponses.INVALID_TOKEN_OR_HEADER,
                                        "Missing refresh_token");
            }
            Object scope = body.get("scope");

            Tokens tokens = requestTokens(
                provider, new RefreshTokenGrant(new RefreshToken((String) refreshToken)),
                scope instanceof String ? Scope.parse((String) scope) : null,
                OidcResponses.INVALID_REFRESH_TOKEN);
            RefreshToken newRefreshToken = tokens.getRefreshToken();
            OidcResponses.writeTokens(response, "Token refresh successful",
                                      tokens.getAccessToken().getValue(),
                                      newRefreshToken == null ? null : newRefreshToken.getValue());
        } catch (Exception e) {
            OidcException error = toOidcException(e);
            if (error.getStatus() >= 500) {
                log.error("Keycloak token refresh failed", e);
            } else {
                log.info("Keycloak token refresh rejected: " + error.getMessage());
            }
            writeError(response, error);
        }
    }

    /**
     * @return the request's JSON object body
     * @throws OidcException 400 if the body is missing, too long, or not a JSON object
     */
    private static Map<String, Object> readJsonBody(HttpServletRequest request)
        throws OidcException, IOException {
        String json;
        try (Reader reader = request.getReader()) {
            char[] buffer = new char[MAX_BODY_LENGTH + 1];
            int length = IOUtils.read(reader, buffer);
            if (length > MAX_BODY_LENGTH) {
                throw new OidcException(HttpServletResponse.SC_BAD_REQUEST,
                                        OidcResponses.MISSING_PARAMETER, "Request body too long");
            }
            json = new String(buffer, 0, length);
        }
        try {
            return JSONObjectUtils.parse(json);
        } catch (java.text.ParseException e) {
            throw new OidcException(HttpServletResponse.SC_BAD_REQUEST,
                                    OidcResponses.MISSING_PARAMETER,
                                    "Request body must be a JSON object");
        }
    }
}
