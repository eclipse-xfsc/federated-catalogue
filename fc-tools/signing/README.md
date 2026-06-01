# fc-tools/signing

Python utilities for signing Verifiable Credentials against the local docker
stack's DID (`did:web:did-server`).

These scripts are copied from `cat-integration-tests/scripts/`. They are now also
co-located with the Bruno collection so onboarding users find them in either place.

## Scripts

| Script                         | Purpose                                                                                                                                                |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `generate-jwt-fixture.py`      | Sign JSON-LD payloads as JWT-VC 2.0 (Ed25519 / EdDSA). Supports inner-VC embedding and EVC/EVP envelopes.                                              |
| `generate-ld-proof-fixture.py` | Sign with Ed25519Signature2020 Linked-Data Proofs. **Legacy** — kept for negative tests only; the catalogue no longer verifies LD proofs (post-Loire). |
| `unwrap-credential.py`         | Decode JWT / EVC / EVP / VP-with-inner-VC. Inspection only, no verification.                                                                           |
| `generate-did-jwk.py`          | Diagnostic: build a `did:jwk` from an RSA public key + x5u URL.                                                                                        |
| `decode-did-jwk.py`            | Diagnostic: decode a `did:jwk:...#0` back to its embedded JWK.                                                                                         |

## Prerequisites

```bash
pip install PyJWT[crypto] cryptography
```

## Default signing key

The docker-compose stack's DID `did:web:did-server` trusts the private key at:

```
federated-catalogue/docker/did-server/certs/jwt-signing.pem
```

Pass it via `--key`:

```bash
python3 generate-jwt-fixture.py \
  --payload <some-vc.jsonld> \
  --key ../docker/did-server/certs/jwt-signing.pem
```

If `--key` is omitted, the script generates a fresh key and prints the
`assertionMethod` block to add to `docker/did-server/www/.well-known/did.json`.

## Examples

See the script's `--help` and the headers of `generate-jwt-fixture.py` for the
full set of patterns (standalone VC, VP with embedded VC, EVC/EVP envelopes,
discovery mode).

## See also

- `../README.md` — onboarding guide and demo flows that consume these scripts
- `../signer/readme.md` — the deprecated Java LD-proof signer (kept for reference)
- `/cat-integration-tests/Makefile` target `sign-jwt-fixtures` — bulk re-signing of BDD test fixtures
