# d1_portal_servlet

The DataONE portal webapp (`portal.war`), deployed on Coordinating Nodes at `/portal`. It logs users in, then issues the DataONE JSON Web Tokens (JWTs) that DataONE services accept as `Authorization: Bearer` credentials.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /portal/oauth?action=start&target=<url>` | Log in directly with ORCID. After login the browser returns to `target`. |
| `GET /portal/oidc?action=start&target=<url>` | Log in through Keycloak (OpenID Connect, with ORCID as Keycloak's identity provider). After login the browser returns to `target`. |
| `GET /portal/token` | Get a DataONE JWT for the logged-in session, or in exchange for a Keycloak access token (below). |
| `GET /portal/logout?target=<url>` | End the session; for Keycloak logins, also end the Keycloak session. Then return to `target`. |

`target` must be an `https` URL on a host allowed by `portal.redirect.allowlist`. Otherwise the request fails with 400. If a login fails after reaching the identity provider, the browser returns to `target` with `error=login_failed` added.

## Getting a DataONE JWT

### From a portal login session

After logging in through `/portal/oauth` or `/portal/oidc`, request `/portal/token` with the session cookie. The response body is the JWT as plain text, or empty if the session isn't logged in. From another site this is a cross-site request made with credentials, so it depends on the browser allowing third-party cookies.

### By exchanging a Keycloak access token

Clients that log in to Keycloak themselves can exchange a Keycloak access token for a DataONE JWT. This needs no cookies or session:

```
GET /portal/token
Authorization: Bearer <keycloak access token>
```

| Response | Meaning |
|---|---|
| `200`, body is a DataONE JWT | The access token is valid. The JWT's subject is the token's `keycloak.subject.claim` (`preferred_username` by default, which holds the ORCID iD). |
| `401`, `WWW-Authenticate: Bearer error="invalid_token"` | The token names this portal's Keycloak issuer but isn't valid: bad signature, expired, wrong audience, not an access token, or missing the subject claim. |

A valid access token must:
- be signed by the realm (RS256), with issuer `keycloak.issuer`;
- be unexpired, with `typ` set to `Bearer`;
- have an audience in `keycloak.token.exchange.audiences`.

Bearer tokens from any other issuer (such as an existing DataONE JWT) are ignored, and the session is used as before. DataONE JWTs expire (18 hours by default); to renew, exchange a fresh Keycloak access token.

## Configuration

Settings are read from the file named by the `portal.properties.file` context parameter in `web.xml`. See `src/main/webapp/WEB-INF/portal.properties` for the full list with comments.

| Setting | Purpose |
|---|---|
| `cn.server.privatekey.filename`, `cn.server.publiccert.filename` | Key and certificate(s) used to sign and verify DataONE JWTs |
| `orcid.*` | Direct ORCID login client |
| `portal.redirect.allowlist` | Comma-separated host suffixes allowed as `target` |
| `keycloak.issuer` | Keycloak realm issuer URL, e.g. `https://auth.dataone.org/realms/dataone`. Keycloak login and token exchange are off while this is empty. |
| `keycloak.client.id`, `keycloak.client.secret` | Confidential client for portal logins (default client `d1-confidential`) |
| `keycloak.redirect.uri` | This deployment's login callback URL. It must reach this portal's `/oidc` and be registered as a redirect URI on the Keycloak client. |
| `keycloak.scope` | Scopes requested at login (default `openid profile email`) |
| `keycloak.subject.claim` | Claim holding the DataONE subject (default `preferred_username`) |
| `keycloak.token.exchange.audiences` | Audiences accepted for token exchange (default: `keycloak.client.id`) |
| `keycloak.register.accounts` | Register Keycloak users with the CN on login (default `true`) |

For Keycloak logout to return to `target`, each target must also be allowed as a post-logout redirect URI on the Keycloak client.

## Building

```
mvn clean verify
```

Requires JDK 25 and `d1_portal` (built from its `develop` branch until a release is published).
