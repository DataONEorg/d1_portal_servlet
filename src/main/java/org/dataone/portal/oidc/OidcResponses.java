package org.dataone.portal.oidc;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.nimbusds.jose.util.JSONObjectUtils;

/**
 * Writes JSON responses in the same shapes as the Python dataone-auth package, so clients can
 * treat the portal's OIDC endpoints and dataone-auth services alike.
 * <ul>
 * <li>Tokens: {@code {"message": ..., "token": {"access_token": ..., "refresh_token": ...}}}</li>
 * <li>Errors: {@code {"error": {"message": ..., "details": ...}}}</li>
 * </ul>
 */
public class OidcResponses {

    // error messages and statuses used by dataone-auth
    public static final String MISSING_PARAMETER = "Missing required parameter";
    public static final String INVALID_TOKEN_OR_HEADER = "Invalid token or header";
    public static final String TOKEN_VALIDATION_FAILED = "Token validation failed";
    public static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";
    public static final String CLIENT_AUTHENTICATION_FAILED = "OIDC client authentication failed";
    public static final String AUTHORIZATION_FAILED = "Authorization failed";
    public static final String OAUTH2_ERROR = "An OAuth2 error occurred";
    public static final String PROVIDER_UNAVAILABLE = "Failed to reach the OIDC provider";
    public static final String NOT_CONFIGURED = "OIDC login is not configured";
    public static final String INTERNAL_ERROR = "Internal authentication error";

    private OidcResponses() {
    }

    /**
     * Write a 200 response with the token payload.
     */
    public static void writeTokens(HttpServletResponse response, String message,
                                   String accessToken, String refreshToken) throws IOException {
        Map<String, Object> token = new LinkedHashMap<String, Object>();
        token.put("access_token", accessToken);
        token.put("refresh_token", refreshToken);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        body.put("token", token);
        write(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * Write an error response with the given status.
     */
    public static void writeError(HttpServletResponse response, int status, String message,
                                  String details) throws IOException {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("message", message);
        error.put("details", details);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", error);
        write(response, status, body);
    }

    private static void write(HttpServletResponse response, int status, Map<String, Object> body)
        throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(JSONObjectUtils.toJSONString(body));
    }
}
