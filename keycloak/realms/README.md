# Keycloak Realm Configuration

Each environment-specific subdirectory contains a single ecosystem-neutral realm import file
`fc-realm.json` (realm name `federated-catalogue-realm`).

| Directory  | Purpose                                      |
|------------|----------------------------------------------|
| `dev/`     | Local development (docker-compose stack)     |
| `staging/` | Staging environment                          |
| `prod/`    | Production environment                       |

The realm name is selected at runtime via `KEYCLOAK_REALM` (default `federated-catalogue-realm`).
The name in the imported JSON must match `KEYCLOAK_REALM` for the application to authenticate against it.

### Existing `gaia-x` deployments

Keycloak's `--import-realm` only imports when the realm does not already exist in the database, so
existing deployments (e.g. our QA stage) already have a `gaia-x` realm persisted in Postgres. They
continue to work by setting `KEYCLOAK_REALM=gaia-x` — no import file is needed.

A fresh deployment that wants a `gaia-x` realm name must supply its own realm import JSON via a
volume mount or ConfigMap; we no longer ship one.

## Why three copies?

Each environment uses a different Keycloak client secret (`FC_CLIENT_SECRET`) and may have
environment-specific redirect URIs, user accounts, or role assignments. The correct file is
selected at container startup via the `KC_IMPORT` env var or the `dev.sh` profile mechanism
in `docker/`.

## Updating realm config

1. Export the updated realm from Keycloak admin UI (`Realm settings → Action → Partial export`).
2. Replace the appropriate environment file.
3. Do **not** commit real secrets — the `clientSecret` field must use a placeholder value
   (`REPLACE_ME`) that is overridden at runtime via `FC_CLIENT_SECRET`.
