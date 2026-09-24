package org.dataone.portal.oidc;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.SubjectType;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;

/**
 * A fake Keycloak realm for tests: a signing key, provider metadata with fixed endpoints, and
 * helpers to mint ID and access tokens like the DataONE realm's.
 */
public class TestKeycloak {

    public static final String ISSUER = "https://auth.example.org/realms/dataone";
    public static final String CLIENT_ID = "d1-confidential";
    public static final String CLIENT_SECRET = "test-secret";
    public static final String REDIRECT_URI = "https://cn.example.org/portal/authorize";
    public static final String ORCID = "https://orcid.org/0000-0000-0000-0001";
    public static final String AUTHORIZATION_ENDPOINT = ISSUER + "/protocol/openid-connect/auth";
    public static final String TOKEN_ENDPOINT = ISSUER + "/protocol/openid-connect/token";
    public static final String END_SESSION_ENDPOINT = ISSUER + "/protocol/openid-connect/logout";

    private final RSAKey key;

    public TestKeycloak() throws JOSEException {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    public KeycloakProvider provider() {
        return provider(new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())));
    }

    /** A provider that trusts a different key than the one tokens are signed with */
    public KeycloakProvider providerTrustingOtherKey() throws JOSEException {
        RSAKey other = new RSAKeyGenerator(2048).keyID("test-key").generate();
        return provider(new ImmutableJWKSet<>(new JWKSet(other.toPublicJWK())));
    }

    private KeycloakProvider provider(ImmutableJWKSet<com.nimbusds.jose.proc.SecurityContext> keys) {
        OIDCProviderMetadata metadata = new OIDCProviderMetadata(
            new Issuer(ISSUER), Collections.singletonList(SubjectType.PUBLIC),
            URI.create(ISSUER + "/protocol/openid-connect/certs"));
        metadata.setAuthorizationEndpointURI(URI.create(AUTHORIZATION_ENDPOINT));
        metadata.setTokenEndpointURI(URI.create(TOKEN_ENDPOINT));
        metadata.setEndSessionEndpointURI(URI.create(END_SESSION_ENDPOINT));
        return new KeycloakProvider(ISSUER, CLIENT_ID, CLIENT_SECRET, REDIRECT_URI,
                                    KeycloakProvider.DEFAULT_SCOPE,
                                    KeycloakProvider.DEFAULT_SUBJECT_CLAIM,
                                    new LinkedHashSet<String>(Arrays.asList(CLIENT_ID)),
                                    metadata, keys);
    }

    /** Claims common to the realm's ID and access tokens, valid for five minutes */
    public JWTClaimsSet.Builder claims() {
        Date now = new Date();
        return new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject("3f1c1d2e-0000-4000-8000-000000000001")
            .issueTime(now)
            .expirationTime(new Date(now.getTime() + 5 * 60 * 1000))
            .claim("preferred_username", ORCID)
            .claim("orcid", ORCID)
            .claim("given_name", "Test")
            .claim("family_name", "User");
    }

    public JWTClaimsSet.Builder idTokenClaims(String nonce) {
        return claims().audience(CLIENT_ID).claim("typ", "ID").claim("azp", CLIENT_ID)
            .claim("nonce", nonce);
    }

    public JWTClaimsSet.Builder accessTokenClaims() {
        return claims().audience(Arrays.asList(CLIENT_ID, "account")).claim("typ", "Bearer")
            .claim("azp", CLIENT_ID).claim("scope", "openid profile email");
    }

    public SignedJWT sign(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt;
    }
}
