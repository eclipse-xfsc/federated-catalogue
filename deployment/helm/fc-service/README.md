# fc-service Helm Chart

Deploys the full Federated Catalogue stack to Kubernetes.

## Components

| Component      | Image                                            | Port |
|----------------|--------------------------------------------------|------|
| fc-service     | ghcr.io/eclipse-xfsc/federated-catalogue/fc-service-server | 8081 |
| fc-demo-portal | ghcr.io/eclipse-xfsc/federated-catalogue/fc-demo-portal    | 8088 |
| Keycloak 26    | quay.io/keycloak/keycloak:26.6.0 (keycloakx 7.1.9) | 8080 |
| PostgreSQL 18  | postgres:18.3                                    | 5432 |
| Fuseki         | docker.io/stain/jena-fuseki:5.1.0                | 3030 |

Sub-chart dependency: `keycloakx 7.1.9`. All GHCR images are public — no pull secret required.

---

## New cluster setup

### 1. Install Traefik ingress controller

```bash
helm repo add traefik https://traefik.github.io/charts
helm repo update
helm install traefik traefik/traefik -n kube-system \
  --set service.type=LoadBalancer \
  --set "ports.web.http.redirections.entryPoint.to=websecure" \
  --set "ports.web.http.redirections.entryPoint.scheme=https" \
  --set "ports.web.http.redirections.entryPoint.permanent=true"
```

Wait until the controller has an external IP:

```bash
kubectl get svc traefik -n kube-system --watch
```

Note the `EXTERNAL-IP` — you will need it in step 3.

### 2. Install cert-manager

```bash
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace \
  --set crds.enabled=true
```

Verify all pods are running before continuing:

```bash
kubectl get pods -n cert-manager
```

### 3. Configure the ingress IP

Edit the `hosts` section at the top of `values.yaml`:

```yaml
hosts:
  fcService: "fc-server.<EXTERNAL-IP>.sslip.io"
  keycloak:  "key-server.<EXTERNAL-IP>.sslip.io"
  portal:    "fc-demo-portal.<EXTERNAL-IP>.sslip.io"
```

Templates derive hostnames from `hosts.*`. Also update the matching literal strings in
`ingress.hosts`, `keycloak.hostname.hostname`, and `keycloak.ingress.rules` — those
sub-chart values cannot reference `hosts.*` directly and must be kept in sync.

### 4. Apply the Let's Encrypt ClusterIssuer

```bash
kubectl apply -f deployment/cert-manager/clusterissuer.yaml
```

### 5. Create namespace and out-of-band secrets

The Keycloak OAuth2 client secret is not managed by Helm. Its value must match the `secret` field of the `fc-service` client in `gaia-x-realm.json` (currently `**********`):

```bash
kubectl create namespace federated-catalogue

kubectl create secret generic fc-keycloak-client-secret \
  --from-literal=keycloak_client_secret=<value-matching-gaia-x-realm.json> \
  -n federated-catalogue
```

### 6. Pull chart dependencies

```bash
helm dependency build deployment/helm/fc-service
```

### 7. Deploy

```bash
helm upgrade --install fc-service deployment/helm/fc-service \
  -n federated-catalogue \
  -f deployment/helm/fc-service/values.yaml \
  --server-side=true --force-conflicts
```

`-f values.yaml` is required — without it Helm reuses stored release values and ignores local changes.  
`--server-side=true --force-conflicts` is required for Helm v4 server-side apply field ownership.

### 8. Verify

```bash
kubectl get pods -n federated-catalogue
```

All five pods should reach `Running` / `1/1`. Fuseki and fc-service take ~60 s to pass their readiness probes. On first start, Fuseki waits for its PVC to be provisioned — allow extra time on cold start.

---

## Service URLs

Replace `<EXTERNAL-IP>` with the configured IP:

| Service        | URL                                                      |
|----------------|----------------------------------------------------------|
| fc-service API | `https://fc-server.<EXTERNAL-IP>.sslip.io`               |
| Keycloak admin | `https://key-server.<EXTERNAL-IP>.sslip.io/auth`         |
| fc-demo-portal | `https://fc-demo-portal.<EXTERNAL-IP>.sslip.io`          |

TLS certificates are issued automatically by Let's Encrypt via HTTP-01 challenge. Allow a minute after first deploy for certificates to be provisioned.

---

## Optional: did:web hosting

The chart can host a development-grade `did:web` endpoint alongside the catalogue. **Disabled by default** (`did.enabled: false`). Enable it when you need a public DID document and certificate chain reachable from the catalogue's verification flow — e.g. signing test credentials against your own DID.

> **Development scope only — because of the certificate, not the DID server itself.** The chart serves
> a TLS chain issued by Let's Encrypt, a domain-validation (DV) CA. Gaia-X production (GXDCH) only
> accepts certificates issued by an eIDAS-qualified Trust Service Provider or an EV-SSL CA from the
> Mozilla CA store, because those bind the cert to a validated legal entity — DV certs do not.
> Production also requires registering the chain as a trust anchor with the GXDCH Registry, which is
> out of scope here. The same DID server workload can be reused in production by swapping the manually
> managed TLS secret for an eIDAS/EV chain and completing trust-anchor registration.

### Resources created when enabled

| Kind | Name | Purpose |
|------|------|---------|
| Deployment | `fc-did-server` | nginx serving the static artifacts |
| Service | `fc-did-server` | ClusterIP on `did.service.port` |
| ConfigMap | `fc-did-server-artifacts` | `did.json` + `chain.pem` + nginx config |
| Ingress | `fc-did-server` | TLS termination at `hosts.did` |

Endpoints exposed:

- `https://<hosts.did>/.well-known/did.json`
- `https://<hosts.did>/certs/chain.pem`

### Setup

#### 1. Obtain a TLS certificate for the DID host

The DID host uses a **manually managed TLS secret** (not cert-manager) so the same private key can be reused for credential signing.

```bash
certbot certonly --standalone -d did.<EXTERNAL-IP>.sslip.io
```

Outputs at `/etc/letsencrypt/live/did.<EXTERNAL-IP>.sslip.io/`:
- `privkey.pem` — TLS key + signer key
- `fullchain.pem` — TLS chain + `x5u` chain

#### 2. Create the TLS secret

```bash
kubectl create secret tls fc-did-server-tls \
  --cert=/etc/letsencrypt/live/did.<EXTERNAL-IP>.sslip.io/fullchain.pem \
  --key=/etc/letsencrypt/live/did.<EXTERNAL-IP>.sslip.io/privkey.pem \
  -n federated-catalogue
```

The secret name must match `did.tls.secretName` (default `fc-did-server-tls`).

#### 3. Build the DID document

Adapt `docker/did-server/setup.sh` for the public hostname:

- `HOSTNAME=did.<EXTERNAL-IP>.sslip.io`
- `SIGNER_KEY=/etc/letsencrypt/live/did.<EXTERNAL-IP>.sslip.io/privkey.pem`
- `x5u` → `https://did.<EXTERNAL-IP>.sslip.io/certs/chain.pem`

The script writes `www/.well-known/did.json`. The `chain.pem` is `fullchain.pem` from step 1.

#### 4. Configure values.yaml

Set `hosts.did`, flip `did.enabled`, and paste the artifacts inline:

```yaml
hosts:
  did: "did.<EXTERNAL-IP>.sslip.io"

did:
  enabled: true
  tls:
    secretName: fc-did-server-tls
  artifacts:
    didJson: |
      {
        "@context": ["https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1"],
        "id": "did:web:did.<EXTERNAL-IP>.sslip.io",
        "verificationMethod": [{
          "id": "did:web:did.<EXTERNAL-IP>.sslip.io#0",
          "type": "JsonWebKey2020",
          "controller": "did:web:did.<EXTERNAL-IP>.sslip.io",
          "publicKeyJwk": {
            "kty": "RSA", "e": "AQAB", "n": "<from setup.sh>",
            "kid": "signRSA2048", "alg": "PS256",
            "x5u": "https://did.<EXTERNAL-IP>.sslip.io/certs/chain.pem"
          }
        }],
        "authentication": ["did:web:did.<EXTERNAL-IP>.sslip.io#0"],
        "assertionMethod": ["did:web:did.<EXTERNAL-IP>.sslip.io#0"]
      }
    chainPem: |
      -----BEGIN CERTIFICATE-----
      <leaf>
      -----END CERTIFICATE-----
      -----BEGIN CERTIFICATE-----
      <intermediate>
      -----END CERTIFICATE-----
      -----BEGIN CERTIFICATE-----
      <root>
      -----END CERTIFICATE-----
```

Keep your populated values file out of version control if it contains environment-specific hostnames or chain material.

#### 5. Deploy

Same `helm upgrade --install` as the main flow. With `did.enabled: true` the DID resources are created; otherwise they are skipped.

### Verify

```bash
# DID document reachable and parseable
curl -s https://did.<EXTERNAL-IP>.sslip.io/.well-known/did.json | jq .id

# x5u entry resolvable
curl -s https://did.<EXTERNAL-IP>.sslip.io/.well-known/did.json \
  | jq '.verificationMethod[0].publicKeyJwk.x5u'

# Chain reachable, leaf subject correct
curl -s https://did.<EXTERNAL-IP>.sslip.io/certs/chain.pem \
  | openssl x509 -noout -subject

# TLS trusted without flags
curl -sv https://did.<EXTERNAL-IP>.sslip.io/.well-known/did.json 2>&1 \
  | grep "SSL certificate verify"
# Expected: SSL certificate verify ok.
```

### Sign credentials against the DID

`cat-integration-tests/scripts/generate-jwt-fixture.py` signs with the LE private key:

```bash
python3 scripts/generate-jwt-fixture.py \
  --payload my-participant.jsonld \
  --key /etc/letsencrypt/live/did.<EXTERNAL-IP>.sslip.io/privkey.pem \
  --kid "did:web:did.<EXTERNAL-IP>.sslip.io#0"
```

Submit the signed credential to the catalogue `/verification` endpoint with default flags (`gaiax.enabled=false`). Strict trust-anchor validation is intentionally out of scope.

### Disable

Set `did.enabled: false` (the default) and re-run `helm upgrade --install`. The DID resources are removed on the next reconcile.

---

## Upgrading an existing release

```bash
helm upgrade --install fc-service deployment/helm/fc-service \
  -n federated-catalogue \
  -f deployment/helm/fc-service/values.yaml \
  --server-side=true --force-conflicts
```

StatefulSet pods (Keycloak, Postgres, Fuseki) do not roll automatically on upgrade — delete them manually if a config change needs to be picked up:

```bash
kubectl delete pod fc-keycloak-0 fc-postgres-0 fc-fuseki-0 -n federated-catalogue
```

---

## Credentials

Postgres and Keycloak admin credentials are currently in plaintext in `values.yaml`. This is acceptable for development but not for production.

The Keycloak OAuth2 client secret (`fc-keycloak-client-secret`) is intentionally excluded from the chart and never overwritten by Helm upgrades.

---

## Security context

`fc-service` and `fc-demo-portal` run hardened:
- UID 1000, non-root
- `readOnlyRootFilesystem: true`
- All Linux capabilities dropped

`/tmp` (Spring Boot JAR extraction) and `/logs` (Logback) are mounted as `emptyDir` volumes. Both are configurable via `volumes` / `volumeMounts` in `values.yaml`.

PostgreSQL runs as root (`allowPrivilegeEscalation: false` only) — required by the official image's gosu-based initialisation.

Fuseki runs as UID 9008 (`fuseki` user). The pod security context sets `fsGroup: 9008` so the mounted PVC is writable on first start.
