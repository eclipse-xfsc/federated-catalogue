#!/usr/bin/env python3
"""
Sign JSON-LD payloads as JWT fixtures for BDD tests.

Reads a .jsonld file (the JWT claims set), wraps it with JWT headers, signs with
Ed25519, and writes the compact JWT. The .jsonld file is the source of truth for
the credential content — human-readable and easy to diff.

For production credential generation, use gx-credential-helper + vc-jwt.io.
This script is for BDD fixture signing only.

Requirements:
  pip install PyJWT[crypto] cryptography

Usage:
  # Sign a Loire VC (auto-detects typ/cty from payload)
  python3 scripts/generate-jwt-fixture.py --payload fixtures/loire/valid/participant.loire.jsonld

  # Sign with explicit output path
  python3 scripts/generate-jwt-fixture.py --payload fixtures/loire/valid/participant.loire.jsonld \
      --out fixtures/loire/valid/participant.loire.signed.jwt

  # Sign a Loire VP with inner VC embedding (signs the inner VC first, injects into VP)
  python3 scripts/generate-jwt-fixture.py --payload fixtures/loire/valid/participant-vp.loire.jsonld \
      --embed-vc fixtures/loire/valid/participant.loire.jsonld

  # Sign a danubetech-format VC (payload has "vc" wrapper claim)
  python3 scripts/generate-jwt-fixture.py --payload fixtures/vc20/valid/participant.vc2.jsonld

  # Discovery mode: omit --payload, derive from --out (foo.signed.jwt → foo.jsonld)
  python3 scripts/generate-jwt-fixture.py --out fixtures/loire/valid/participant.loire.signed.jwt

  # Use existing key
  python3 scripts/generate-jwt-fixture.py --payload fixtures/loire/valid/participant.loire.jsonld \
      --key keys/jwt-signing.pem

  # Override auto-detected headers
  python3 scripts/generate-jwt-fixture.py --payload my-vc.jsonld --typ vc+jwt --cty vc

  # Produce an EnvelopedVerifiableCredential JSON-LD fixture (Gaia-X ICAM 24.07):
  # Signs the VC JWT and wraps it in an EVC envelope (data:application/vc+ld+json+jwt,<JWT>).
  python3 scripts/generate-jwt-fixture.py --payload fixtures/loire/valid/participant.loire.jsonld \
      --key keys/jwt-signing.pem \
      --wrap-as evc \
      --out fixtures/enveloped/valid/participant.evc.jsonld

  # Produce an EnvelopedVerifiablePresentation JSON-LD fixture (Gaia-X ICAM 24.07):
  # Signs the inner VC, embeds it in the VP, signs the VP JWT, wraps it in an EVP envelope.
  python3 scripts/generate-jwt-fixture.py --payload fixtures/loire/valid/participant-vp.loire.jsonld \
      --embed-vc fixtures/loire/valid/participant.loire.jsonld \
      --key keys/jwt-signing.pem \
      --wrap-as evp \
      --out fixtures/enveloped/valid/participant.evp.jsonld

DID document update (after generating a new key):
  1. Copy the printed "assertionMethod" block into docker/did-server/www/.well-known/did.json
  2. Add the key ID to the "assertionMethod" array
  3. docker compose down && docker compose up
     (restart does NOT flush the FC server's Caffeine DID cache)
"""

import argparse
import base64
import json
import sys
from pathlib import Path

try:
    import jwt as pyjwt
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives.serialization import (
        Encoding, NoEncryption, PrivateFormat, PublicFormat,
    )
except ImportError:
    print("Missing dependencies. Install with:", file=sys.stderr)
    print("  pip install PyJWT[crypto] cryptography", file=sys.stderr)
    sys.exit(1)

ISSUER_DID = "did:web:did-server"
KEY_ID = f"{ISSUER_DID}#jwt-key-1"


# --- Key management ---

def b64url_no_pad(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def generate_key() -> Ed25519PrivateKey:
    return Ed25519PrivateKey.generate()


def load_key_from_pem(pem_path: str) -> Ed25519PrivateKey:
    from cryptography.hazmat.primitives.serialization import load_pem_private_key
    pem_bytes = Path(pem_path).read_bytes()
    key = load_pem_private_key(pem_bytes, password=None)
    if not isinstance(key, Ed25519PrivateKey):
        print(f"Error: {pem_path} is not an Ed25519 private key", file=sys.stderr)
        sys.exit(1)
    return key


def save_key_pem(key: Ed25519PrivateKey, path: Path) -> None:
    pem = key.private_bytes(Encoding.PEM, PrivateFormat.PKCS8, NoEncryption())
    path.write_bytes(pem)
    print(f"Private key saved: {path}")


def build_public_key_jwk(key: Ed25519PrivateKey, key_id: str) -> dict:
    pub = key.public_key()
    raw = pub.public_bytes(Encoding.Raw, PublicFormat.Raw)
    return {
        "kty": "OKP",
        "crv": "Ed25519",
        "kid": key_id,
        "x": b64url_no_pad(raw),
        "use": "sig",
    }


# --- Payload & header detection ---

def detect_headers(payload: dict) -> tuple[str, str | None]:
    """Auto-detect JWT typ/cty from payload structure.

    Loire format: top-level @context, no vc/vp wrapper → vc+jwt/vc or vp+jwt/vp
    Danubetech format: vc or vp wrapper claim → typ: JWT, no cty
    """
    has_vc_wrapper = "vc" in payload
    has_vp_wrapper = "vp" in payload
    has_top_level_context = "@context" in payload
    has_verifiable_credential = "verifiableCredential" in payload

    if has_vc_wrapper or has_vp_wrapper:
        # Danubetech format
        return "JWT", None

    if has_top_level_context:
        # Loire format
        if has_verifiable_credential:
            return "vp+jwt", "vp"
        return "vc+jwt", "vc"

    return "JWT", None


def sign_jwt(payload: dict, key: Ed25519PrivateKey, kid: str,
             typ: str = "JWT", cty: str | None = None) -> str:
    headers = {"kid": kid, "typ": typ}
    if cty is not None:
        headers["cty"] = cty
    return pyjwt.encode(payload, key, algorithm="EdDSA", headers=headers)


# --- Payload resolution ---

def resolve_payload_path(args) -> Path:
    """Resolve payload path from --payload or by discovery from --out."""
    if args.payload:
        return Path(args.payload)

    if args.out:
        # Discovery: foo.signed.jwt → foo.jsonld
        out = Path(args.out)
        stem = out.name
        for suffix in [".signed.jwt", ".jwt"]:
            if stem.endswith(suffix):
                stem = stem[: -len(suffix)]
                break
        candidate = out.parent / f"{stem}.jsonld"
        if candidate.exists():
            print(f"Discovered payload: {candidate}")
            return candidate
        # Also try without the format qualifier (e.g., participant.loire.signed.jwt → participant.loire.jsonld)
        print(f"Error: no --payload given and discovery failed (tried {candidate})", file=sys.stderr)
        sys.exit(1)

    print("Error: either --payload or --out is required", file=sys.stderr)
    sys.exit(1)


def load_payload(path: Path) -> dict:
    text = path.read_text()
    try:
        return json.loads(text)
    except json.JSONDecodeError as e:
        print(f"Error: {path} is not valid JSON: {e}", file=sys.stderr)
        sys.exit(1)


def resolve_output_path(args, payload_path: Path) -> Path | None:
    """Resolve output path from --out or by derivation from payload path."""
    if args.out:
        return Path(args.out)

    # Derive: foo.jsonld → foo.signed.jwt
    stem = payload_path.stem  # e.g., "participant.loire"
    return payload_path.parent / f"{stem}.signed.jwt"


# --- Envelope wrappers (Gaia-X ICAM 24.07) ---

EVC_ENVELOPE = {
    "@context": "https://www.w3.org/ns/credentials/v2",
    "id": "data:application/vc+ld+json+jwt,{jwt}",
    "type": "EnvelopedVerifiableCredential",
}

EVP_ENVELOPE = {
    "@context": "https://www.w3.org/ns/credentials/v2",
    "id": "data:application/vp+ld+jwt,{jwt}",
    "type": "EnvelopedVerifiablePresentation",
}


def wrap_as_envelope(jwt_compact: str, wrap_as: str) -> str:
    """Produce an EVC or EVP JSON-LD document embedding the given compact JWT."""
    template = EVC_ENVELOPE if wrap_as == "evc" else EVP_ENVELOPE
    envelope = {k: v.format(jwt=jwt_compact) if isinstance(v, str) else v
                for k, v in template.items()}
    return json.dumps(envelope, indent=2) + "\n"


# --- VP inner VC embedding ---

VC_JWT_PLACEHOLDER = "{{VC_JWT}}"


def embed_inner_vc(vp_payload: dict, vc_jwt: str) -> dict:
    """Replace {{VC_JWT}} placeholders in VP payload with the signed inner VC JWT."""
    raw = json.dumps(vp_payload)
    if VC_JWT_PLACEHOLDER not in raw:
        print(f"Warning: no {VC_JWT_PLACEHOLDER} placeholder found in VP payload", file=sys.stderr)
        return vp_payload
    raw = raw.replace(VC_JWT_PLACEHOLDER, vc_jwt)
    return json.loads(raw)


# --- Output ---

def print_did_document_snippet(jwk: dict, key_id: str) -> None:
    print("\n" + "=" * 60)
    print("DID document update (docker/did-server/www/.well-known/did.json):")
    print("=" * 60)
    print('\nAdd to "verificationMethod" array:')
    vm_entry = {
        "id": key_id,
        "type": "JsonWebKey2020",
        "controller": key_id.split("#")[0],
        "publicKeyJwk": jwk,
    }
    print(json.dumps(vm_entry, indent=2))
    print(f'\nAdd to "assertionMethod" array: "{key_id}"')
    print("\nThen: docker compose down && docker compose up")
    print("=" * 60 + "\n")


# --- Main ---

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Sign JSON-LD payloads as JWT fixtures for BDD tests",
    )
    parser.add_argument("--payload", help="JSON-LD file to use as JWT claims (discovered from --out if omitted)")
    parser.add_argument("--out", help="Output .signed.jwt path (derived from --payload if omitted)")
    parser.add_argument("--embed-vc", dest="embed_vc",
                        help="(VP) JSON-LD file for inner VC — signed first, then injected via {{VC_JWT}} placeholder")
    parser.add_argument("--typ", help="JWT typ header (auto-detected from payload if omitted)")
    parser.add_argument("--cty", help="JWT cty header (auto-detected from payload if omitted)")
    parser.add_argument("--key", help="Ed25519 private key PEM (generates new key if omitted)")
    parser.add_argument("--save-key", help="Save generated key to this PEM path")
    parser.add_argument("--kid", default=KEY_ID, help=f"Key ID (default: {KEY_ID})")
    parser.add_argument("--wrap-as", choices=["evc", "evp"], dest="wrap_as",
                        help="Wrap the signed JWT in an EVC or EVP JSON-LD envelope (writes .jsonld)")
    args = parser.parse_args()

    # Key
    if args.key:
        key = load_key_from_pem(args.key)
        print(f"Loaded key: {args.key}")
    else:
        key = generate_key()
        print("Generated new Ed25519 key pair.")
        if args.save_key:
            save_key_pem(key, Path(args.save_key))
        else:
            print("Tip: use --save-key keys/jwt-signing.pem to persist the key")

    # Payload
    payload_path = resolve_payload_path(args)
    payload = load_payload(payload_path)
    print(f"Payload: {payload_path}")

    # Embed inner VC if VP
    if args.embed_vc:
        vc_payload_path = Path(args.embed_vc)
        vc_payload = load_payload(vc_payload_path)
        vc_typ, vc_cty = detect_headers(vc_payload)
        vc_jwt = sign_jwt(vc_payload, key, args.kid, typ=vc_typ, cty=vc_cty)
        payload = embed_inner_vc(payload, vc_jwt)
        print(f"Embedded inner VC: {vc_payload_path} (typ={vc_typ}, cty={vc_cty})")

    # Headers
    auto_typ, auto_cty = detect_headers(payload)
    typ = args.typ or auto_typ
    cty = args.cty or auto_cty
    print(f"Headers: typ={typ}, cty={cty or '(none)'}")

    # Sign
    compact_jwt = sign_jwt(payload, key, args.kid, typ=typ, cty=cty)

    # Output
    if args.wrap_as:
        envelope_json = wrap_as_envelope(compact_jwt, args.wrap_as)
        out_path = Path(args.out) if args.out else payload_path.parent / f"{payload_path.stem}.{args.wrap_as}.jsonld"
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(envelope_json)
        print(f"Envelope ({args.wrap_as.upper()}) written: {out_path}")
    else:
        out_path = resolve_output_path(args, payload_path)
        if out_path:
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(compact_jwt)
            print(f"Fixture written: {out_path}")
        else:
            print("\n--- Compact JWT ---")
            print(compact_jwt)
            print("---\n")

    # DID document snippet
    jwk = build_public_key_jwk(key, args.kid)
    print_did_document_snippet(jwk, args.kid)


if __name__ == "__main__":
    main()
