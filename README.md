# d1_portal_servlet

The DataONE portal webapp (`portal.war`), deployed on Coordinating Nodes at `/portal`. It logs users in, then issues the DataONE JSON Web Tokens (JWTs) that DataONE services accept as `Authorization: Bearer` credentials.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /portal/oauth?action=start&target=<url>` | Log in directly with ORCID. After login the browser returns to `target`. |
| `GET /portal/login?target=<url>&scope=<scopes>` | Log in through Keycloak (OpenID Connect, with ORCID as Keycloak's identity provider). Both parameters are optional (see [Scopes](#scopes)). |
| `GET /portal/authorize` | Keycloak's login callback. With a `target`, redirects there; without one, returns the Keycloak tokens as JSON (below). |
| `POST /portal/refresh` | Exchange a Keycloak refresh token for new tokens (below). |
| `GET /portal/token` | Get a DataONE JWT for the logged-in session, or in exchange for a Keycloak access token (below). |
| `GET /portal/logout?target=<url>` | End the session; for Keycloak logins, also end the Keycloak session. Then return to `target`. |

`target` must be an `https` URL on a host allowed by `portal.redirect.allowlist`. Otherwise the request fails with 400. If a login fails after reaching the identity provider, the browser returns to `target` with `error=login_failed` added.

`/portal/login`, `/portal/authorize` and `/portal/refresh` are named and behave like the endpoints of the Python [dataone-auth](https://github.com/DataONEorg/dataone-auth) package, so clients can use the portal and dataone-auth services alike:

- Token responses (from `/authorize` without a `target`, and from `/refresh`):
  `{"message": "...", "token": {"access_token": "...", "refresh_token": "..."}}`
- Errors: `{"error": {"message": "...", "details": "..."}}`. The status is 400 for a bad or missing parameter, 401 for an invalid token, code or refresh token, 502 when Keycloak can't be reached, and 503 when Keycloak isn't configured.

## Scopes

Keycloak access tokens carry the scopes a user was granted, and services check for their own scopes when making authorization decisions. `/portal/login` requests:

1. the base scopes (`keycloak.scope.base`, default `openid profile email`);
2. the deployment's extra scopes (`keycloak.scope.extra`), like the scopes a dataone-auth service passes to `create_client`;
3. the login's `scope` parameter: a space-separated list, e.g. `scope=dataone:token-exchange ogdc:workflow:execute` for a token used with several services.

Duplicates are dropped. The portal keeps no list of allowed scopes: Keycloak decides which scopes exist and which the user may have, and the token's `scope` claim shows what was granted. The portal only rejects a `scope` parameter that isn't valid OAuth scope syntax or is over 2048 characters.

If Keycloak refuses the login (for example `invalid_scope` or `access_denied`), a login with a `target` returns there with Keycloak's code in the `error` parameter. Without a `target`, the code starts the JSON error's `details`.

## Keycloak tokens and refresh

A client that logs in through `/portal/login` without a `target` receives Keycloak access and refresh tokens from `/portal/authorize`. Before the access token expires, it can get new tokens with:

```
POST /portal/refresh
Content-Type: application/json

{"refresh_token": "<keycloak refresh token>", "scope": "<optional>"}
```

The portal adds its client secret, which Keycloak requires for the confidential `d1-confidential` client. Each new access token can then be exchanged at `/portal/token` for a new DataONE JWT.

## Getting a DataONE JWT

### From a portal login session

After logging in through `/portal/oauth` or `/portal/login`, request `/portal/token` with the session cookie. The response body is the JWT as plain text, or empty if the session isn't logged in. From another site this is a cross-site request made with credentials, so it depends on the browser allowing third-party cookies.

### By exchanging a Keycloak access token

Clients that log in to Keycloak themselves can exchange a Keycloak access token for a DataONE JWT. This needs no cookies or session:

```
GET /portal/token
Authorization: Bearer <keycloak access token>
```

| Response | Meaning |
|---|---|
| `200`, body is a DataONE JWT | The access token is valid. The JWT's subject is the token's `keycloak.subject.claim` (`orcid` by default, the ORCID iD claim, as in dataone-auth). |
| `401`, `WWW-Authenticate: Bearer error="invalid_token"`, JSON error body | The token names this portal's Keycloak issuer but isn't valid: bad signature, expired, wrong audience or authorized party, not an access token, missing the subject claim, or longer than 16 KB. |
| `403`, `WWW-Authenticate: Bearer error="insufficient_scope", scope="dataone:token-exchange"`, JSON error body | The token is valid but wasn't granted the exchange scope. |

A valid access token must:
- be signed by the realm (RS256), with issuer `keycloak.issuer`;
- be unexpired, with `typ` set to `Bearer`;
- have an audience in `keycloak.token.exchange.audiences`;
- if it has an authorized party (`azp`), have one from that list too;
- carry the exchange scope, `keycloak.token.exchange.scope` (default `dataone:token-exchange`), in its `scope` claim. Request it at login (`/portal/login?scope=dataone:token-exchange`) or through `keycloak.scope.extra`.

Bearer tokens from any other issuer (such as an existing DataONE JWT) are ignored, and the session is used as before. DataONE JWTs expire (18 hours by default); to renew, exchange a fresh Keycloak access token.

## Configuration

Settings are read from the file named by the `portal.properties.file` context parameter in `web.xml`. See `src/main/webapp/WEB-INF/portal.properties` for the full list with comments.

| Setting | Purpose |
|---|---|
| `cn.server.privatekey.filename`, `cn.server.publiccert.filename` | Key and certificate(s) used to sign and verify DataONE JWTs |
| `orcid.*` | Direct ORCID login client |
| `portal.redirect.allowlist` | Comma-separated host suffixes allowed as `target` |
| `keycloak.client.secrets.file` | Optional `client_secrets.json` in the dataone-auth format (`client_id`, `client_secret`, `server_metadata_url`). The individual settings take precedence over it. |
| `keycloak.issuer` | Keycloak realm issuer URL, e.g. `https://auth.dataone.org/realms/dataone` |
| `keycloak.server.metadata.url` | The realm's `.well-known/openid-configuration` URL, as an alternative to the issuer. Keycloak login and token exchange are off while neither is set. |
| `keycloak.client.id`, `keycloak.client.secret` | Confidential client for portal logins (default client `d1-confidential`) |
| `keycloak.redirect.uri` | This deployment's login callback URL. It must reach this portal's `/authorize` and be registered as a redirect URI on the Keycloak client. |
| `keycloak.scope.base` | Base scopes requested at login (default `openid profile email`) |
| `keycloak.scope.extra` | Extra scopes this deployment always requests at login (comma or space separated) |
| `keycloak.subject.claim` | Claim holding the DataONE subject (default `orcid`) |
| `keycloak.token.exchange.audiences` | Clients whose access tokens may be exchanged (default: `keycloak.client.id`) |
| `keycloak.token.exchange.scope` | Scope an access token needs to be exchanged (default `dataone:token-exchange`; empty turns the check off) |
| `keycloak.register.accounts` | Register Keycloak users with the CN on login (default `true`) |

For Keycloak logout to return to `target`, each target must also be allowed as a post-logout redirect URI on the Keycloak client.

## Building

```
mvn clean verify
```

Requires JDK 25 and `d1_portal` (built from its `develop` branch until a release is published).
