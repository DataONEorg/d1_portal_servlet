package org.dataone.portal.oidc;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.BadJWTException;
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
 * <li>{@code keycloak.client.secrets.file}: optional path to a {@code client_secrets.json} in
 * the format used by the Python dataone-auth package, with {@code client_id},
 * {@code client_secret} and {@code server_metadata_url}. Values set with the individual
 * settings below take precedence over the file.</li>
 * <li>{@code keycloak.issuer}: the realm's issuer URL, e.g.
 * {@code https://auth.dataone.org/realms/dataone}.</li>
 * <li>{@code keycloak.server.metadata.url}: the realm's
 * {@code .well-known/openid-configuration} URL, as an alternative to the issuer. Keycloak support
 * is off when neither is set.</li>
 * <li>{@code keycloak.client.id}: the confidential client the portal logs in with (default
 * {@code d1-confidential}).</li>
 * <li>{@code keycloak.client.secret}: that client's secret.</li>
 * <li>{@code keycloak.redirect.uri}: this deployment's login callback URL, which must be
 * registered as a redirect URI on the client and must reach the portal's /authorize
 * servlet.</li>
 * <li>{@code keycloak.scope}: scopes requested at login (default {@code openid profile email}).</li>
 * <li>{@code keycloak.subject.claim}: the claim holding the user's DataONE subject (default
 * {@code orcid}, the ORCID iD claim the realm's profile scope adds, as dataone-auth uses).</li>
 * <li>{@code keycloak.token.exchange.audiences}: comma-separated clients whose access tokens may
 * be exchanged for a DataONE JWT (default: the client id). The token's audience must include
 * one of them, and its authorized party (azp), if present, must be one of them.</li>
 * </ul>
 * Endpoints are discovered from the provider metadata on first use, and the realm's signing keys
 * are fetched and cached from its JWKS endpoint.
 */
public class KeycloakProvider {

    public static final String SECRETS_FILE = "keycloak.client.secrets.file";
    public static final String ISSUER = "keycloak.issuer";
    public static final String METADATA_URL = "keycloak.server.metadata.url";
    public static final String CLIENT_ID = "keycloak.client.id";
    public static final String CLIENT_SECRET = "keycloak.client.secret";
    public static final String REDIRECT_URI = "keycloak.redirect.uri";
    public static final String SCOPE = "keycloak.scope";
    public static final String SUBJECT_CLAIM = "keycloak.subject.claim";
    public static final String EXCHANGE_AUDIENCES = "keycloak.token.exchange.audiences";

    public static final String DEFAULT_CLIENT_ID = "d1-confidential";
    public static final String DEFAULT_SCOPE = "openid profile email";
    public static final String DEFAULT_SUBJECT_CLAIM = "orcid";

    /** Keycloak marks access tokens with typ=Bearer (ID tokens have typ=ID) */
    private static final String ACCESS_TOKEN_TYPE = "Bearer";

    /** Longest token accepted, as in dataone-auth (MAX_TOKEN_LEN) */
    public static final int MAX_TOKEN_LENGTH = 16_384;

    /** Timeout in milliseconds for fetching provider metadata and keys */
    private static final int HTTP_TIMEOUT = 5000;

    private static Log log = LogFactory.getLog(KeycloakProvider.class);

    private static volatile KeycloakProvider instance;

    private final String issuer;
    private final String metadataUrl;
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
        Map<String, Object> secrets = loadSecretsFile(
            Settings.getConfiguration().getString(SECRETS_FILE));

        String clientId = setting(CLIENT_ID, secrets, "client_id", DEFAULT_CLIENT_ID);
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
                                    setting(METADATA_URL, secrets, "server_metadata_url", null),
                                    clientId,
                                    setting(CLIENT_SECRET, secrets, "client_secret", null),
                                    Settings.getConfiguration().getString(REDIRECT_URI),
                                    Settings.getConfiguration().getString(SCOPE, DEFAULT_SCOPE),
                                    Settings.getConfiguration()
                                        .getString(SUBJECT_CLAIM, DEFAULT_SUBJECT_CLAIM),
                                    audiences);
    }

    /**
     * Read a dataone-auth style client_secrets.json file.
     * @return its entries, or an empty map if no file is configured or it can't be read
     */
    static Map<String, Object> loadSecretsFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return new HashMap<String, Object>();
        }
        try {
            String json = new String(Files.readAllBytes(Paths.get(path.trim())),
                                     StandardCharsets.UTF_8);
            return JSONObjectUtils.parse(json);
        } catch (IOException | ParseException e) {
            log.error("Could not read Keycloak client secrets from " + path, e);
            return new HashMap<String, Object>();
        }
    }

    /**
     * @return the Settings value for key, else the secrets file entry, else the default
     */
    private static String setting(String key, Map<String, Object> secrets, String secretsKey,
                                  String defaultValue) {
        String value = Settings.getConfiguration().getString(key);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        Object fromFile = secrets.get(secretsKey);
        if (fromFile instanceof String && !((String) fromFile).trim().isEmpty()) {
            return (String) fromFile;
        }
        return defaultValue;
    }

    public KeycloakProvider(String issuer, String metadataUrl, String clientId,
                            String clientSecret, String redirectUri, String scope,
                            String subjectClaim, Set<String> exchangeAudiences) {
        this.issuer = trimToNull(issuer);
        this.metadataUrl = trimToNull(metadataUrl);
        this.clientId = trimToNull(clientId);
        this.clientSecret = trimToNull(clientSecret);
        this.redirectUri = trimToNull(redirectUri);
        this.scope = scope == null ? DEFAULT_SCOPE : scope;
        this.subjectClaim = subjectClaim == null ? DEFAULT_SUBJECT_CLAIM : subjectClaim;
        this.exchangeAudiences = exchangeAudiences == null
            ? new LinkedHashSet<String>(Arrays.asList(this.clientId == null ? DEFAULT_CLIENT_ID
                : this.clientId))
            : exchangeAudiences;
    }

    /**
     * Create a provider with known metadata and keys, without network lookups (for tests).
     */
    public KeycloakProvider(String issuer, String clientId, String clientSecret, String redirectUri,
                            String scope, String subjectClaim, Set<String> exchangeAudiences,
                            OIDCProviderMetadata metadata, JWKSource<SecurityContext> jwkSource) {
        this(issuer, null, clientId, clientSecret, redirectUri, scope, subjectClaim,
             exchangeAudiences);
        this.metadata = metadata;
        this.jwkSource = jwkSource;
    }

    /**
     * @return a provider with no issuer, so Keycloak support is off
     */
    public static KeycloakProvider disabled() {
        return new KeycloakProvider(null, null, DEFAULT_CLIENT_ID, null, null, null, null, null);
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * @return true if Keycloak access tokens can be validated (an issuer or metadata URL is set)
     */
    public boolean isEnabled() {
        return issuer != null || metadataUrl != null;
    }

    /**
     * @return true if browser login through Keycloak is fully configured
     */
    public boolean isLoginConfigured() {
        return isEnabled() && clientId != null && clientSecret != null && redirectUri != null;
    }

    /**
     * @return the configured issuer, or the issuer from the provider metadata
     */
    public String getIssuer() throws GeneralException, IOException {
        return issuer != null ? issuer : getMetadata().getIssuer().getValue();
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
     * @return the provider's metadata (endpoints), fetched on first use from the metadata URL,
     *         or from the issuer's .well-known/openid-configuration
     */
    public OIDCProviderMetadata getMetadata() throws GeneralException, IOException {
        if (metadata == null) {
            synchronized (this) {
                if (metadata == null) {
                    OIDCProviderMetadata resolved;
                    if (metadataUrl != null) {
                        String json = new DefaultResourceRetriever(HTTP_TIMEOUT, HTTP_TIMEOUT)
                            .retrieveResource(URI.create(metadataUrl).toURL()).getContent();
                        resolved = OIDCProviderMetadata.parse(json);
                        if (issuer != null && !issuer.equals(resolved.getIssuer().getValue())) {
                            throw new GeneralException("Issuer in " + metadataUrl + " ("
                                + resolved.getIssuer() + ") doesn't match " + ISSUER + " "
                                + issuer);
                        }
                    } else {
                        resolved = OIDCProviderMetadata.resolve(new Issuer(issuer), HTTP_TIMEOUT,
                                                                HTTP_TIMEOUT);
                    }
                    metadata = resolved;
                }
            }
        }
        return metadata;
    }

    private JWKSource<SecurityContext> getJWKSource() throws GeneralException, IOException {
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
        throws GeneralException, IOException, BadJOSEException, JOSEException {
        IDTokenValidator validator = new IDTokenValidator(
            new Issuer(getIssuer()), getClientID(),
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
        String tokenIssuer;
        try {
            tokenIssuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            return false;
        }
        try {
            return tokenIssuer != null && tokenIssuer.equals(getIssuer());
        } catch (GeneralException | IOException e) {
            log.warn("Could not load Keycloak provider metadata", e);
            return false;
        }
    }

    /**
     * Validate a Keycloak access token: its signature against the realm's keys, the issuer, an
     * accepted audience and authorized party, expiry, the access token type, the presence of the
     * subject claim, and its length.
     * @return the validated claims
     */
    public JWTClaimsSet validateAccessToken(String token)
        throws ParseException, BadJOSEException, JOSEException, GeneralException, IOException {
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new BadJWTException("Token exceeds maximum allowed length");
        }
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSKeySelector(
            new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, getJWKSource()));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<SecurityContext>(
            exchangeAudiences,
            new JWTClaimsSet.Builder().issuer(getIssuer()).claim("typ", ACCESS_TOKEN_TYPE).build(),
            new HashSet<String>(Arrays.asList("exp", subjectClaim)),
            null));
        JWTClaimsSet claims = processor.process(token, null);
        String azp = claims.getStringClaim("azp");
        if (azp != null && !exchangeAudiences.contains(azp)) {
            throw new BadJWTException("Invalid authorized party (azp): " + azp);
        }
        return claims;
    }

    /**
     * @return the accepted audiences, for error messages
     */
    public List<String> getExchangeAudiences() {
        return new ArrayList<String>(exchangeAudiences);
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
