package org.dataone.portal.servlets.oidc;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.dataone.portal.oidc.KeycloakProvider;
import org.dataone.portal.oidc.OidcException;
import org.dataone.portal.oidc.OidcResponses;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.OAuth2Error;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.token.Tokens;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;

/**
 * Shared parts of the servlets that talk to Keycloak's token endpoint.
 */
public abstract class KeycloakServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * @return the Keycloak provider; tests override this
     */
    protected KeycloakProvider getProvider() {
        return KeycloakProvider.getInstance();
    }

    /**
     * Send a grant to Keycloak's token endpoint, authenticating with the client secret.
     * @param grantFailedMessage the error message to report if Keycloak rejects the grant
     * @return the tokens from a successful response
     */
    protected Tokens requestTokens(KeycloakProvider provider, AuthorizationGrant grant, Scope scope,
                                   String grantFailedMessage) throws OidcException {
        TokenResponse tokenResponse;
        try {
            TokenRequest tokenRequest = new TokenRequest.Builder(
                provider.getMetadata().getTokenEndpointURI(),
                new ClientSecretBasic(provider.getClientID(),
                                      new Secret(provider.getClientSecret())),
                grant).scope(scope).build();
            tokenResponse = sendTokenRequest(tokenRequest);
        } catch (IOException | com.nimbusds.oauth2.sdk.GeneralException e) {
            throw new OidcException(HttpServletResponse.SC_BAD_GATEWAY,
                                    OidcResponses.PROVIDER_UNAVAILABLE, e.getMessage());
        }
        if (!tokenResponse.indicatesSuccess()) {
            ErrorObject error = tokenResponse.toErrorResponse().getErrorObject();
            String details = error.getCode() + (error.getDescription() == null ? ""
                : ": " + error.getDescription());
            if (OAuth2Error.INVALID_CLIENT.getCode().equals(error.getCode())) {
                throw new OidcException(HttpServletResponse.SC_UNAUTHORIZED,
                                        OidcResponses.CLIENT_AUTHENTICATION_FAILED, details);
            }
            if (OAuth2Error.INVALID_GRANT.getCode().equals(error.getCode())) {
                throw new OidcException(HttpServletResponse.SC_UNAUTHORIZED, grantFailedMessage,
                                        details);
            }
            throw new OidcException(HttpServletResponse.SC_UNAUTHORIZED,
                                    OidcResponses.OAUTH2_ERROR, details);
        }
        return tokenResponse.toSuccessResponse().getTokens();
    }

    /**
     * Send a token request over HTTP; tests override this.
     */
    protected TokenResponse sendTokenRequest(TokenRequest tokenRequest)
        throws IOException, ParseException {
        return OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
    }

    /**
     * Map an unexpected failure to a dataone-auth style error.
     */
    protected static OidcException toOidcException(Exception e) {
        if (e instanceof OidcException) {
            return (OidcException) e;
        }
        if (e instanceof BadJOSEException || e instanceof JOSEException
            || e instanceof java.text.ParseException) {
            return new OidcException(HttpServletResponse.SC_UNAUTHORIZED,
                                     OidcResponses.TOKEN_VALIDATION_FAILED, e.getMessage());
        }
        if (e instanceof IOException || e instanceof com.nimbusds.oauth2.sdk.GeneralException) {
            return new OidcException(HttpServletResponse.SC_BAD_GATEWAY,
                                     OidcResponses.PROVIDER_UNAVAILABLE, e.getMessage());
        }
        return new OidcException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                 OidcResponses.INTERNAL_ERROR, e.getMessage());
    }

    /**
     * Write an error response for the exception, unless the response has already been sent.
     */
    protected static void writeError(HttpServletResponse response, OidcException e)
        throws IOException {
        if (!response.isCommitted()) {
            OidcResponses.writeError(response, e.getStatus(), e.getErrorMessage(), e.getDetails());
        }
    }
}
