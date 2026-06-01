# Future appendix — Loire compliance against a real GXDCH

> **Status: documented, not yet executable.** This appendix describes how a real Gaia-X compliance check is meant to
> flow through the catalogue, what's missing to run it end-to-end from this repository today, and the path to lighting it
> up. There are no fixture files in this folder — the walkthroughs in [`dcs-template-demo/`](./dcs-template-demo/) and [
`admin-toggles-demo/`](./admin-toggles-demo/) are the executable demos.

## What this would prove

Two related things:

1. **Direct GXDCH conformance** — a signed Loire credential produced from this repository's tooling is accepted as
   conformant by a real Gaia-X Digital Clearing House. Proves the *credential* is well-formed and trust-chained from the
   federation's perspective. Works today with **x5c** binding if the rest of the preconditions are met.
2. **Catalogue-mediated round-trip** — the catalogue can submit a stored asset to GXDCH and persist the result alongside
   the asset, making it queryable via `GET /assets/{id}/compliance-checks`. Proves the catalogue is interoperable with
   the wider Gaia-X federation rather than an island. Requires **x5u** binding because the catalogue's own internal
   verification (`LoirePolicyEnforcer`) only validates trust chains via `x5u`, not inline `x5c`.

The catalogue-side architecture is in place — see
`fc-service-core/.../trustframework/compliance/JwtVcComplianceClient.java` and `ComplianceCheckOrchestrator`. The
OpenAPI contract is at [`../openapi/fc_openapi.yaml`](../openapi/fc_openapi.yaml) §`/assets/{id}/compliance-check` and
`/trust-frameworks`. Both flows are sketched below; see [x5c vs x5u](#x5c-vs-x5u--what-binds-to-a-gaia-x-trust-anchor)
for why they currently diverge.

## Why this is documented, not built

A real GXDCH check has three preconditions the local docker stack doesn't satisfy:

1. **A TSP-issued certificate chain that terminates at a Gaia-X-recognised root.** The local `did-server` ships a
   self-signed key (`docker/did-server/certs/jwt-signing.pem`) — enough for the catalogue's own verification, but a real
   GXDCH rejects it because the chain doesn't reach a Gaia-X trust anchor. Cert procurement is out-of-scope for example
   fixtures.
2. **A real, notarisable participant identity.** GXDCH's Loire flow includes a notary check on
   `gx:legalRegistrationNumber` — a VAT/LEI/EORI that resolves in the corresponding public registry. The demo
   participants in [`queries/`](./queries/) (`deltaDAO AG`, `deltaDAO Dresden GmbH`, …) are fictional and won't
   notarise.
3. **A trust-framework profile registered in the catalogue that points at GXDCH.** The default profiles loaded from
   `trustframeworks/gaia-x-2511/` declare shapes but don't bind to an external compliance service URL. A Loire-GXDCH
   profile would need to be created via `POST /admin/trust-frameworks` with the GXDCH base URL (
   `https://compliance.lab.gaia-x.eu/v2`).

None of these are catalogue bugs — they are setup tasks that depend on an actual organisation (or test tenant) that has
gone through TSP onboarding.

## x5c vs x5u — what binds to a Gaia-X trust anchor

Loire credentials bind an issuer's identity to a Gaia-X trust anchor in one of two ways inside `publicKeyJwk`:

- **`x5u`** — a URL pointing at a hosted PEM certificate chain. Requires the issuer to operate a resolvable HTTPS
  endpoint that serves the chain (typically the same origin as their `did:web`).
- **`x5c`** — the full certificate chain **embedded inline** in the JWK as an array of base64-encoded DER certs.

The local stack has no public-facing DID-web origin — `did:web:did-server` only resolves from inside the docker network.
Operating a public `x5u` endpoint just to test GXDCH would mean standing up a reverse proxy with a real TLS cert; that's
its own project. **x5c sidesteps the hosting requirement entirely**: the chain travels inside the credential. For ad-hoc
compliance testing against a public GXDCH from a developer machine, x5c is the path of least resistance.

> ### ⚠️ The catalogue rejects x5c — only GXDCH validates it
>
> `LoirePolicyEnforcer` (`fc-service-core/.../verification/LoirePolicyEnforcer.java:166`) **rejects** any VC whose
`publicKeyJwk` carries an inline `x5c` chain, with `ClientException`: *"x5c certificate chain validation is not
supported. Use x5u (Trust Anchor Registry URL) instead."* The reason, per the same file's Javadoc: full chain building,
> trust-anchor-registry lookup and revocation checking are not implemented, and accepting x5c with expiry-only checks
> would silently mask broken trust chains.
>
> **GXDCH does perform the full check** (chain building, TAR lookup, revocation — that's its job). So a x5c-signed Loire
> credential is **valid for GXDCH** but **inadmissible to the catalogue**: today the catalogue cannot act as the
> submission proxy for an x5c-signed credential, because the credential won't pass `POST /assets` ingestion.
>
> Practical consequence: the curl flow below skips the catalogue and submits directly to a public GXDCH endpoint. Once
> the FACIS team has a stable production participant DID at a resolvable origin, **switch from x5c to x5u** and the
> catalogue-mediated round-trip (`POST /assets/{id}/compliance-check`) becomes the canonical path.

## Two flows, depending on what you have

### Flow A — direct GXDCH submission with x5c (today)

Skips the catalogue. Useful for one-shot conformity testing of a TSP-issued participant credential before any catalogue
ingestion is contemplated.

```bash
export PARTICIPANT_VP=/path/to/signed-participant-vp.jwt
export GXDCH=https://compliance.lab.gaia-x.eu/v2

# 1. Look up the current Loire compliance API path
curl -fsS "$GXDCH/docs"   # OpenAPI doc; identify the participant-compliance endpoint

# 2. Submit the x5c-signed VP directly to GXDCH
curl -fsS -X POST "$GXDCH/api/credential-offers" \
  -H "Content-Type: application/jwt" \
  --data-binary @"$PARTICIPANT_VP" | jq .
```

GXDCH returns the compliance assessment as a signed Verifiable Credential. Inspect that VC with the
`unwrap-credential.py` helper under `../../fc-tools/signing/` and confirm the issuer is the GXDCH compliance engine.

The catalogue is not involved. This proves the *credential* is Loire-compliant in the federation's eyes; it doesn't
prove anything about the catalogue's behaviour.

### Flow B — catalogue-mediated round-trip with x5u (when public hosting exists)

This is the canonical demo once a participant has a public DID-web origin serving its certificate chain.

```bash
export TOKEN=$(../auth.sh)
export FRAMEWORK_PROFILE_ID="gaia-x-loire-public"   # to be registered in the catalogue
export PARTICIPANT_VP=/path/to/signed-participant-vp.jwt   # signed with x5u, not x5c

# 1. Ingest the participant credential as an asset (catalogue verifies x5u chain itself)
ASSET_ID=$(curl -fsS -X POST http://localhost:8081/assets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/vc+jwt" \
  --data-binary @"$PARTICIPANT_VP" | jq -r .id)

# 2. Ask the catalogue to run a Loire compliance check via GXDCH
curl -fsS -X POST "http://localhost:8081/assets/$(printf %s "$ASSET_ID" | jq -sRr @uri)/compliance-check" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
        \"frameworkProfileId\": \"$FRAMEWORK_PROFILE_ID\",
        \"credential\": \"$(cat "$PARTICIPANT_VP")\"
      }" | jq .

# 3. Inspect stored results
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/assets/$(printf %s "$ASSET_ID" | jq -sRr @uri)/compliance-checks" | jq .
```

## Signing the VP

For Flow A (x5c) with a TSP-issued chain in hand:

```bash
python3 ../fc-tools/signing/generate-jwt-fixture.py \
  --payload signed-participant-vp.jsonld \
  --key tsp-issued-private.pem \
  --x5c tsp-chain.pem \
  --output signed-participant-vp.jwt
```

For Flow B (x5u), use `--x5u https://<your-origin>/.well-known/cert-chain.pem` instead of `--x5c`. The PEM at that URL
must contain the full chain, with the leaf at the top and the Gaia-X-recognised root at the bottom.

The **TSA gap** documented in [
`dcs-template-demo/README.md`](./dcs-template-demo/README.md#signing-the-fixtures-for-strict-mode) applies equally here.
Once `eclipse-xfsc/tsa-service` supports VC 2.0, the signing step becomes an HTTP call to TSA with the participant's
tenant credentials, and the Python script can be retired.

## Reference

- GXDCH overview: <https://gaia-x.eu/gxdch/>
- GXDCH lab v2 OpenAPI: <https://compliance.lab.gaia-x.eu/v2/docs>
- Loire participant compliance
  criteria: <https://docs.gaia-x.eu/policy-rules-committee/compliance-document/25.10/criteria_participant/>
- Catalogue compliance integration: `fc-service-core/src/main/java/eu/xfsc/fc/core/service/trustframework/compliance/`
- Local GXDCH test harness (cloned spec): [
  `../../gaiax-docs/gxdch/gxdch_test/loire/`](../../../gaiax-docs/gxdch/gxdch_test/loire/) — Python script that
  exercises the public GXDCH endpoints; useful as an executable reference for what valid GXDCH traffic looks like.

## Path to making this executable

A reasonable sequence to convert this appendix into a working demo:

1. Obtain a TSP-issued certificate chain for a real (or test-tenant) participant. The Gaia-X Lab T&Cs cover this for
   federation members.
2. Register the participant with a notarisable `gx:legalRegistrationNumber`. The participant's legal entity must exist
   in the corresponding registry.
3. Register a Loire trust-framework profile in the catalogue pointing at `compliance.lab.gaia-x.eu/v2` (
   `POST /admin/trust-frameworks`).
4. Move this file into a real `gxdch-demo/` folder with executable curl scripts and a corresponding
   `gxdch-participant.jsonld` template that the team-owned signing pipeline can fill in.
5. Make `gxdch-participant.jsonld` a `.example.jsonld` template — actual signed credentials must not be committed.
