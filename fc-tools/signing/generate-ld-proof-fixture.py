#!/usr/bin/env python3
"""
Generate a JSON-LD VP fixture with an Ed25519Signature2020 LD proof.

The credential content is serialized (keys sorted, compact), signed with the test
Ed25519 key, and the signature embedded as multibase base58btc proofValue.
This is NOT spec-compliant RDF Dataset Normalization — it is a test fixture only.
checkCryptography() rejects LD proofs without verifying, so the exact signing
input does not matter; what matters is that a proof property is present and the
proofValue was produced by the real test key.

Usage:
  python3 scripts/generate-ld-proof-fixture.py \\
      --key ../federated-catalogue/docker/did-server/certs/jwt-signing.pem \\
      --out fixtures/valid/ld-proof/participant-vp.ld-proof.jsonld

Requirements (same as generate-jwt-fixture.py):
  pip install PyJWT[crypto] cryptography
"""

import argparse
import base64
import json
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives.serialization import load_pem_private_key
except ImportError:
    print("Missing dependency. Install with: pip install cryptography", file=sys.stderr)
    sys.exit(1)

ISSUER_DID = "did:web:did-server"
KEY_ID = f"{ISSUER_DID}#jwt-key-1"

VP_TEMPLATE = {
    "@context": [
        "https://www.w3.org/ns/credentials/v2",
        "https://w3id.org/gaia-x/2511#"
    ],
    "type": ["VerifiablePresentation"],
    "id": "urn:uuid:vp-bdd-ld-proof",
    "holder": ISSUER_DID,
    "verifiableCredential": [
        {
            "@context": [
                "https://www.w3.org/ns/credentials/v2",
                "https://w3id.org/gaia-x/2511#"
            ],
            "type": ["VerifiableCredential", "gx:LegalPerson"],
            "id": "urn:uuid:vc-bdd-ld-proof",
            "issuer": ISSUER_DID,
            "validFrom": "2026-01-30T00:00:00Z",
            "validUntil": "2027-12-31T23:59:59Z",
            "credentialSubject": {
                "id": "did:key:z6MkjRagNiMu91DduvCvgEsqLZDVzrJzFrwahc4tXLt9DoHd",
                "@type": "gx:LegalPerson"
            }
        }
    ]
}

BASE58_ALPHABET = b"123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"


def base58btc_encode(data: bytes) -> str:
    """Encode bytes to base58btc (Bitcoin alphabet)."""
    n = int.from_bytes(data, "big")
    result = []
    while n > 0:
        n, r = divmod(n, 58)
        result.append(BASE58_ALPHABET[r:r + 1])
    # leading zero bytes → leading '1' chars
    for byte in data:
        if byte == 0:
            result.append(b"1")
        else:
            break
    return b"".join(reversed(result)).decode("ascii")


def multibase_base58btc(data: bytes) -> str:
    """Wrap bytes in multibase base58btc encoding (prefix 'z')."""
    return "z" + base58btc_encode(data)


def load_key(pem_path: str) -> Ed25519PrivateKey:
    pem_bytes = Path(pem_path).read_bytes()
    key = load_pem_private_key(pem_bytes, password=None)
    if not isinstance(key, Ed25519PrivateKey):
        print(f"Error: {pem_path} is not an Ed25519 private key", file=sys.stderr)
        sys.exit(1)
    return key


def sign_document(doc: dict, key: Ed25519PrivateKey) -> bytes:
    """Sign the JSON-serialized document (sorted keys, no whitespace)."""
    payload = json.dumps(doc, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return key.sign(payload)


def build_ld_proof_vp(key: Ed25519PrivateKey, key_id: str) -> dict:
    vp = dict(VP_TEMPLATE)
    signature = sign_document(vp, key)
    proof_value = multibase_base58btc(signature)
    vp["proof"] = {
        "type": "Ed25519Signature2020",
        "created": "2026-01-30T00:00:00Z",
        "verificationMethod": key_id,
        "proofPurpose": "assertionMethod",
        "proofValue": proof_value
    }
    return vp


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate a JSON-LD VP fixture with an Ed25519Signature2020 LD proof"
    )
    parser.add_argument(
        "--key", required=True,
        help="Ed25519 private key PEM (e.g. ../federated-catalogue/docker/did-server/certs/jwt-signing.pem)"
    )
    parser.add_argument(
        "--out", required=True,
        help="Output .jsonld path"
    )
    parser.add_argument(
        "--kid", default=KEY_ID,
        help=f"verificationMethod key ID (default: {KEY_ID})"
    )
    args = parser.parse_args()

    key = load_key(args.key)
    print(f"Loaded key: {args.key}")

    vp = build_ld_proof_vp(key, args.kid)

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(vp, indent=2) + "\n")
    print(f"Fixture written: {out_path}")
    print(f"proofValue: {vp['proof']['proofValue'][:32]}...")


if __name__ == "__main__":
    main()
