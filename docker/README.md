# Build & Test Procedure

Ensure you have JDK 17, Maven 3.5.4 (or newer) and Git installed

First clone the Federated Catalogue repository:

``` sh
git clone git@gitlab.eclipse.org:eclipse/xfsc/cat/fc-service.git
```

## Run Catalog
Go to `/docker` folder. Use docker compose to start the stack needed to use the Catalog:

``` sh
docker-compose up
```

Development option that starts the stack with locally build jar files:
- to build the jars first run `mvn clean install` in the root folder of this repository
- then start it with `dev.env` profile from `/docker` folder:

```sh
docker-compose --env-file dev.env up --build
```

### Verification Defaults

By default, the catalogue runs with **semantic validation only**. Signature verification and
schema validation are disabled because signature checks without the Gaia-X trust framework
provide no meaningful security (see architecture decision ADR 3).

| Flag | Default | Description |
|------|---------|-------------|
| `semantics` | `true` | Validate `@type` against loaded ontologies |
| `schema` | `false` | Validate against SHACL shapes |
| `vp-signature` | `false` | Verify VP proof (JWS) |
| `vc-signature` | `false` | Verify VC proof (JWS) |
| `gaiax.enabled` | `false` | Gaia-X Trust Framework compliance |

### Strict Profile (Full Verification)

To enable the full verification pipeline (signatures + trust framework + schema), use the
strict compose overlay:

```sh
docker compose -f docker-compose.yml -f docker-compose.strict.yml --env-file dev.env up
```

Or via the dev helper script:

```sh
./dev.sh strict
```

This enables all verification flags and requires DID resolution infrastructure (did-server,
certificates, trust anchor registry).

### Keycloak setup

When all components started you should setup Keycloak which is used as Identity and Access Management layer in the project. Add keycloak host to your local `hosts` file:

```
127.0.0.1	key-server
```

Open keycloak admin console at `http://key-server:8080/admin` with `admin/admin` credentials, select `gaia-x` realm.

#### Client secret

The `federated-catalogue` client secret must match the `FC_CLIENT_SECRET` value in `.env`/`dev.env`. If you regenerate the secret in Keycloak, update `.env`/`dev.env` and restart the server:

```sh
docker compose --env-file dev.env build server
docker compose --env-file dev.env up -d server
```

#### Create a user

Go to Users, create a new user with username and attributes, save. Then go to Credentials tab, set a password, disable Temporary, save.

#### Assign roles

Go to the user's Role Mappings tab, click `Assign role`, filter by client `federated-catalogue`, and assign a composite role:
For development and running integration tests, assign `Ro-MU-CA` or `ADMIN_ALL`.

#### Re-importing the realm (existing stack)

If you need to update the realm (e.g., after adding new roles), use Keycloak's partial import:

1. Go to Realm Settings → Action → Partial import
2. Upload `keycloak/realms/gaia-x-realm.json`
3. Select **Skip** for existing resources (preserves client secret and user accounts)

New roles will be created; existing ones are preserved. To update composite role mappings on existing roles, manually edit them in the Keycloak UI or do a full teardown (`./dev.sh clean`) and restart.

Now you can test FC Service with Demo Portal web app. Go to `http://localhost:8088` in your browser and press Login button. You should be redirected to Keycloak Login page. Use  user credentials you created above..


## Run tests
To run all tests as part of this project run `mvn test` in the root folder of the repository.


## Build Docker-Images manually

### Docker based build 
*This method only requires to have a working instance of docker installed on your system.*

Go to the main folder of this repository and run the following command:
```sh
# Build an image for the Catalog server 
docker build --target fc-service-server -t fc-service-server . 

# Build an image for the Demo-Portal app 
docker build --target fc-demo-portal -t fc-demo-portal . 
```

Note: initial build may take up to 5 minutes to download all required libraries. Subsequent builds take much less time. 

### Maven based build
For a build without docker you can use the [Maven Jib plugin](https://github.com/GoogleContainerTools/jib) to build container for the catalogs components. 

1. Set the Environment variables `CI_REGISTRY`, `CI_REGISTRY_USERNAME` and `CI_REGISTRY_PASSWORD`.
2. Run following command in the root folder of this repository:
    ```sh
    mvn compile jib:build
    ```
    