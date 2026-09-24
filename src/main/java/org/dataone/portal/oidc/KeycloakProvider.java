package org.dataone.portal.oidc;

import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;

/**
 * Configuration and token validation for a Keycloak realm used as an OpenID Connect provider.
 * <p>
 * Settings (from portal.properties):
 * <ul>
 * <li>{@code keycloak.issuer}: the realm's issuer URL, e.g.
 * {@code https://auth.dataone.org/realms/dataone}. Keycloak support is off when this is empty.</li>
 * <li>{@code keycloak.client.id}: the confidential client the portal logs in with (default
 * {@code d1-confidential}).</li>
 * <li>{@code keycloak.client.secret}: that client's secret.</li>
 * <li>{@code keycloak.redirect.uri}: this deployment's login callback URL, which must be
 * registered as a redirect URI on the client and must reach the portal's /oidc servlet.</li>
 * <li>{@code keycloak.scope}: scopes requested at login (default {@code openid profile email}).</li>
 * <li>{@code keycloak.subject.claim}: the claim holding the user's DataONE subject (default
 * {@code preferred_username}, which the realm fills with the ORCID iD).</li>
 * <li>{@code keycloak.token.exchange.audiences}: comma-separated audiences an access token must
 * carry to be exchanged for a DataONE JWT (default: the client id).</li>
 * </ul>
 * Endpoints are discovered from the issuer's {@code .well-known/openid-configuration} on first
 * use, and the realm's signing keys are fetched and cached from its JWKS endpoint.
 */
public class KeycloakProvider {

    public static final String ISSUER = "keycloak.issuer";
    public static final String CLIENT_ID = "keycloak.client.id";
    public static final String CLIENT_SECRET = "keycloak.client.secret";
    public static final String REDIRECT_URI = "keycloak.redirect.uri";
    public static final String SCOPE = "keycloak.scope";
    public static final String SUBJECT_CLAIM = "keycloak.subject.claim";
    public static final String EXCHANGE_AUDIENCES = "keycloak.token.exchange.audiences";

    public static final String DEFAULT_CLIENT_ID = "d1-confidential";
    public static final String DEFAULT_SCOPE = "openid profile email";
    public static final String DEFAULT_SUBJECT_CLAIM = "preferred_username";

    /** Keycloak marks access tokens with typ=Bearer (ID tokens have typ=ID) */
    private static final String ACCESS_TOKEN_TYPE = "Bearer";

    /** Timeout in milliseconds for fetching provider metadata and keys */
    private static final int HTTP_TIMEOUT = 5000;

    private static Log log = LogFactory.getLog(KeycloakProvider.class);

    private static volatile KeycloakProvider instance;

    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String scope;
    private final String subjectClaim;
    private final Set<String> exchangeAudiences;

    private volatile OIDCProviderMetadata metadata;
    private volatile JWKSource<SecurityContext> jwkSource;

    /**
     * @return the provider configured from Settings, created on first use
     */
    public static KeycloakProvider getInstance() {
        if (instance == null) {
            synchronized (KeycloakProvider.class) {
                if (instance == null) {
                    instance = fromSettings();
                }
            }
        }
        return instance;
    }

    /**
     * Replace the shared provider, for tests. Pass null to read Settings again on next use.
     */
    public static void setInstance(KeycloakProvider provider) {
        instance = provider;
    }

    public static KeycloakProvider fromSettings() {
        String clientId = Settings.getConfiguration().getString(CLIENT_ID, DEFAULT_CLIENT_ID);
        Set<String> audiences = new LinkedHashSet<String>();
        // split here: Settings may or may not have list delimiter parsing enabled
        for (String value : Settings.getConfiguration().getStringArray(EXCHANGE_AUDIENCES)) {
            for (String audience : value.split(",")) {
                if (!audience.trim().isEmpty()) {
                    audiences.add(audience.trim());
                }
            }
        }
        if (audiences.isEmpty()) {
            audiences.add(clientId);
        }
        return new KeycloakProvider(Settings.getConfiguration().getString(ISSUER),
                                    clientId,
                                    Settings.getConfiguration().getString(CLIENT_SECRET),
                                    Settings.getConfiguration().getString(REDIRECT_URI),
                                    Settings.getConfiguration().getString(SCOPE, DEFAULT_SCOPE),
                                    Settings.getConfiguration()
                                        .getString(SUBJECT_CLAIM, DEFAULT_SUBJECT_CLAIM),
                                    audiences);
    }

    public KeycloakProvider(String issuer, String clientId, String clientSecret, String redirectUri,
                            String scope, String subjectClaim, Set<String> exchangeAudiences) {
        this.issuer = trimToNull(issuer);
        this.clientId = trimToNull(clientId);
        this.clientSecret = trimToNull(clientSecret);
        this.redirectUri = trimToNull(redirectUri);
        this.scope = scope;
        this.subjectClaim = subjectClaim;
        this.exchangeAudiences = exchangeAudiences;
    }

    /**
     * Create a provider with known metadata and keys, without network lookups (for tests).
     */
    public KeycloakProvider(String issuer, String clientId, String clientSecret, String redirectUri,
                            String scope, String subjectClaim, Set<String> exchangeAudiences,
                            OIDCProviderMetadata metadata, JWKSource<SecurityContext> jwkSource) {
        this(issuer, clientId, clientSecret, redirectUri, scope, subjectClaim, exchangeAudiences);
        this.metadata = metadata;
        this.jwkSource = jwkSource;
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * @return true if Keycloak access tokens can be validated (an issuer is configured)
     */
    public boolean isEnabled() {
        return issuer != null;
    }

    /**
     * @return true if browser login through Keycloak is fully configured
     */
    public boolean isLoginConfigured() {
        return isEnabled() && clientId != null && clientSecret != null && redirectUri != null;
    }

    public String getIssuer() {
        return issuer;
    }

    public ClientID getClientID() {
        return new ClientID(clientId);
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public URI getRedirectURI() {
        return URI.create(redirectUri);
    }

    public String getScope() {
        return scope;
    }

    /**
     * @return the provider's discovered metadata (endpoints), fetched on first use
     */
    public OIDCProviderMetadata getMetadata() throws GeneralException, java.io.IOException {
        if (metadata == null) {
            synchronized (this) {
                if (metadata == null) {
                    metadata = OIDCProviderMetadata.resolve(new Issuer(issuer), HTTP_TIMEOUT,
                                                            HTTP_TIMEOUT);
                }
            }
        }
        return metadata;
    }

    private JWKSource<SecurityContext> getJWKSource() throws GeneralException, java.io.IOException {
        if (jwkSource == null) {
            synchronized (this) {
                if (jwkSource == null) {
                    URL jwksURL = getMetadata().getJWKSetURI().toURL();
                    // caches keys and refreshes them when a token names an unknown key id
                    jwkSource = JWKSourceBuilder
                        .create(jwksURL, new DefaultResourceRetriever(HTTP_TIMEOUT, HTTP_TIMEOUT))
                        .build();
                }
            }
        }
        return jwkSource;
    }

    /**
     * Validate an ID token from a login callback.
     * @return the validated claims
     */
    public IDTokenClaimsSet validateIdToken(JWT idToken, Nonce expectedNonce)
        throws GeneralException, java.io.IOException, BadJOSEException, JOSEException {
        IDTokenValidator validator = new IDTokenValidator(
            new Issuer(issuer), getClientID(),
            new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, getJWKSource()),
            null);
        return validator.validate(idToken, expectedNonce);
    }

    /**
     * @return true if the token is a JWT whose (unverified) issuer is this provider. Used to decide
     *         whether a bearer token should be validated as a Keycloak access token.
     */
    public boolean isIssuedBy(String token) {
        if (!isEnabled()) {
            return false;
        }
        try {
            return issuer.equals(JWTParser.parse(token).getJWTClaimsSet().getIssuer());
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Validate a Keycloak access token: its signature against the realm's keys, the issuer, an
     * accepted audience, expiry, the access token type, and the presence of the subject claim.
     * @return the validated claims
     */
    public JWTClaimsSet validateAccessToken(String token)
        throws ParseException, BadJOSEException, JOSEException, GeneralException,
        java.io.IOException {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(
            new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, getJWKSource()));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<SecurityContext>(
            exchangeAudiences,
            new JWTClaimsSet.Builder().issuer(issuer).claim("typ", ACCESS_TOKEN_TYPE).build(),
            new HashSet<String>(Arrays.asList("exp", subjectClaim)),
            null));
        return processor.process(token, null);
    }

    /**
     * @return the DataONE subject from a token's claims
     */
    public String getSubject(JWTClaimsSet claims) throws ParseException {
        String subject = claims.getStringClaim(subjectClaim);
        if (subject == null || subject.isEmpty()) {
            throw new ParseException("Token has no " + subjectClaim + " claim", 0);
        }
        return subject;
    }

    /**
     * @return the user's full name from a token's claims, or null
     */
    public static String getName(JWTClaimsSet claims) throws ParseException {
        String name = claims.getStringClaim("name");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String given = claims.getStringClaim("given_name");
        String family = claims.getStringClaim("family_name");
        if (given != null && family != null) {
            return given + " " + family;
        }
        return family != null ? family : given;
    }
}
