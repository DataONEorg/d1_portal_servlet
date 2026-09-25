package org.dataone.portal.oidc;

import java.util.List;

/**
 * A valid access token that lacks a scope the request requires (dataone-auth's
 * InsufficientScopeError, reported as 403).
 */
public class InsufficientScopeException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String requiredScope;

    public InsufficientScopeException(String requiredScope, List<String> availableScopes) {
        super("Required: '" + requiredScope + "'. Available: " + availableScopes);
        this.requiredScope = requiredScope;
    }

    public String getRequiredScope() {
        return requiredScope;
    }
}
