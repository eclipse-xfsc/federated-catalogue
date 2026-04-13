# Keycloak Realm Configuration

The `gaia-x-realm.json` file exists in three environment-specific subdirectories:

| Directory  | Purpose                                      |
|------------|----------------------------------------------|
| `dev/`     | Local development (docker-compose stack)     |
| `staging/` | Staging environment                          |
| `prod/`    | Production environment                       |

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
