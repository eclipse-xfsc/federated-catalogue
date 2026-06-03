#!/usr/bin/env python3
"""Decode a did:jwk DID and display the embedded JWK as pretty-printed JSON.

Usage:
    python3 scripts/decode-did-jwk.py <did:jwk:...>

You can pass the DID with or without the #0 fragment.

Example (inline):
    python3 scripts/decode-did-jwk.py 'did:jwk:eyJhbGciOiJQUzI1NiIs...#0'
"""

import base64
import json
import sys


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        sys.exit(1)

    did = sys.argv[1]

    # Strip fragment (#0) if present
    did = did.split("#")[0]

    prefix = "did:jwk:"
    if not did.startswith(prefix):
        print(f"Error: expected a did:jwk: URI, got: {did[:30]}...", file=sys.stderr)
        sys.exit(1)

    b64 = did[len(prefix):]

    # Restore base64 padding
    b64 += "=" * (-len(b64) % 4)

    try:
        jwk = json.loads(base64.urlsafe_b64decode(b64))
    except Exception as e:
        print(f"Error decoding JWK: {e}", file=sys.stderr)
        sys.exit(1)

    print(json.dumps(jwk, indent=2))


if __name__ == "__main__":
    main()
