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
