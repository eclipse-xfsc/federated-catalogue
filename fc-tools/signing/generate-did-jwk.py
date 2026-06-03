#!/usr/bin/env python3
"""Generate a did:jwk DID from an RSA public key PEM and an x5u URL.

Usage:
    python3 scripts/generate-did-jwk.py <public-key.pem> <x5u-url>

Example:
    python3 scripts/generate-did-jwk.py \
        ../federated-catalogue/fc-tools/signer/rsa2048.pub.pem \
        https://did-server/certs/chain.pem

To extract the public key from a private key:
    openssl rsa -in rsa2048.sign.pem -pubout -out rsa2048.pub.pem

Output: the full did:jwk:...#0 string, ready to paste into the Makefile's
FC_SIGNER_DID variable.
"""

import base64
import json
import sys

from cryptography.hazmat.primitives.serialization import load_pem_public_key


def int_to_base64url(n: int) -> str:
    """Encode an integer as base64url (no padding), per RFC 7518."""
    length = (n.bit_length() + 7) // 8
    raw = n.to_bytes(length, byteorder="big")
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def main() -> None:
    if len(sys.argv) != 3:
        print(__doc__.strip(), file=sys.stderr)
        sys.exit(1)

    pem_path, x5u_url = sys.argv[1], sys.argv[2]

    with open(pem_path, "rb") as f:
        pub_key = load_pem_public_key(f.read())

    numbers = pub_key.public_numbers()  # type: ignore[union-attr]

    jwk = {
        "alg": "PS256",
        "e": int_to_base64url(numbers.e),
        "kty": "RSA",
        "n": int_to_base64url(numbers.n),
        "use": "sig",
        "x5u": x5u_url,
    }

    # Compact JSON, sorted keys — deterministic output
    jwk_json = json.dumps(jwk, separators=(",", ":"), sort_keys=True)
    jwk_b64 = base64.urlsafe_b64encode(jwk_json.encode()).rstrip(b"=").decode("ascii")

    did = f"did:jwk:{jwk_b64}#0"
    print(did)


if __name__ == "__main__":
    main()
