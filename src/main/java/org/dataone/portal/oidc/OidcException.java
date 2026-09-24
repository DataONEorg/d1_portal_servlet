package org.dataone.portal.oidc;

/**
 * A failure in an OIDC endpoint, with the HTTP status and dataone-auth style message to report.
 */
public class OidcException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int status;

    public OidcException(int status, String message, String details) {
        super(message + (details == null ? "" : ": " + details));
        this.status = status;
        this.message = message;
        this.details = details;
    }

    private final String message;
    private final String details;

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
}
