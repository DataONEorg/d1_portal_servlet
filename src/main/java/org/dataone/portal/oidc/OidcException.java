package org.dataone.portal.oidc;

/**
 * A failure in an OIDC endpoint, with the HTTP status and dataone-auth style message to report.
 */
public class OidcException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int status;

    private final String message;
    private final String details;
    private final String oauthError;

    public OidcException(int status, String message, String details) {
        this(status, message, details, null);
    }

    /**
     * @param oauthError the OAuth error code from Keycloak (e.g. invalid_scope), if any
     */
    public OidcException(int status, String message, String details, String oauthError) {
        super(message + (details == null ? "" : ": " + details));
        this.status = status;
        this.message = message;
        this.details = details;
        this.oauthError = oauthError;
    }

    public int getStatus() {
        return status;
    }

    /** @return the short message, one of the {@link OidcResponses} constants */
    public String getErrorMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    /** @return the OAuth error code from Keycloak (e.g. invalid_scope), or null */
    public String getOAuthError() {
        return oauthError;
    }
}
