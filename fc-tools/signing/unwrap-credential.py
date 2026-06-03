#!/usr/bin/env python3
"""
Unwrap and inspect JWT and enveloped credentials for debugging.

Decodes compact JWTs (vc+jwt, vp+jwt), EnvelopedVerifiableCredentials,
and EnvelopedVerifiablePresentations — printing headers, payload, and
recursively unwrapping inner credentials.

No signature verification — this is a read-only inspection tool.

Requirements:
  pip install PyJWT cryptography   (or just: base64 + json from stdlib)

Usage:
  # Decode a compact JWT file
  python3 scripts/unwrap-credential.py fixtures/loire/valid/participant.loire.signed.jwt

  # Decode an EVC/EVP JSON-LD file
  python3 scripts/unwrap-credential.py fixtures/enveloped/valid/participant.evc.jsonld

  # Decode from stdin
  cat some-credential.jwt | python3 scripts/unwrap-credential.py -

  # Compact summary (type, issuer, subject only)
  python3 scripts/unwrap-credential.py --summary fixtures/loire/valid/participant.loire.signed.jwt
"""

import argparse
import base64
import json
import sys
from pathlib import Path


# --- JWT decoding (no verification) ---

def b64url_decode(s: str) -> bytes:
    """Decode base64url without padding."""
    s += "=" * (4 - len(s) % 4)
    return base64.urlsafe_b64decode(s)


def decode_jwt(compact: str) -> dict:
    """Decode a compact JWT into header + payload (no signature verification)."""
    parts = compact.strip().split(".")
    if len(parts) != 3:
        raise ValueError(f"Expected 3 JWT parts, got {len(parts)}")
    header = json.loads(b64url_decode(parts[0]))
    payload = json.loads(b64url_decode(parts[1]))
    return {"header": header, "payload": payload}


# --- Enveloped credential unwrapping ---

ENVELOPED_TYPES = {"EnvelopedVerifiableCredential", "EnvelopedVerifiablePresentation"}
DATA_URI_PREFIX = "data:"


def extract_jwt_from_data_uri(uri: str) -> str:
    """Extract the compact JWT from a data: URI."""
    if not uri.startswith(DATA_URI_PREFIX):
        raise ValueError(f"Not a data: URI: {uri[:50]}...")
    comma = uri.index(",")
    media_type = uri[len(DATA_URI_PREFIX):comma]
    compact = uri[comma + 1:]
    if not compact:
        raise ValueError("data: URI payload is empty")
    return compact, media_type


def resolve_type(obj: dict) -> str | list | None:
    return obj.get("type") or obj.get("@type")


def resolve_id(obj: dict) -> str | None:
    return obj.get("id") or obj.get("@id")


def is_enveloped(obj: dict) -> bool:
    t = resolve_type(obj)
    if isinstance(t, str):
        return t in ENVELOPED_TYPES
    if isinstance(t, list):
        return bool(ENVELOPED_TYPES & set(t))
    return False


# --- Recursive unwrapping ---

def unwrap(content: str, depth: int = 0) -> dict:
    """Recursively unwrap a credential, returning a structured tree."""
    content = content.strip()

    # Compact JWT
    if content.startswith("eyJ"):
        decoded = decode_jwt(content)
        result = {
            "format": "JWT",
            "header": decoded["header"],
            "payload": decoded["payload"],
        }
        # Recurse into VP's verifiableCredential entries
        vc_entries = decoded["payload"].get("verifiableCredential", [])
        if vc_entries:
            result["innerCredentials"] = []
            for entry in vc_entries:
                if isinstance(entry, str):
                    result["innerCredentials"].append(unwrap(entry, depth + 1))
                elif isinstance(entry, dict) and is_enveloped(entry):
                    result["innerCredentials"].append(
                        unwrap(json.dumps(entry), depth + 1))
                else:
                    result["innerCredentials"].append({"format": "inline", "content": entry})
        return result

    # JSON-LD (possibly enveloped)
    try:
        obj = json.loads(content)
    except json.JSONDecodeError:
        return {"format": "unknown", "preview": content[:200]}

    if is_enveloped(obj):
        envelope_type = resolve_type(obj)
        id_val = resolve_id(obj)
        if id_val and id_val.startswith(DATA_URI_PREFIX):
            compact, media_type = extract_jwt_from_data_uri(id_val)
            inner = unwrap(compact, depth + 1)
            return {
                "format": envelope_type,
                "dataUriMediaType": media_type,
                "inner": inner,
            }
        return {"format": envelope_type, "error": f"missing or invalid id: {id_val}"}

    # Plain JSON-LD credential
    result = {"format": "JSON-LD", "content": obj}
    vc_entries = obj.get("verifiableCredential", [])
    if vc_entries:
        result["innerCredentials"] = []
        for entry in vc_entries:
            if isinstance(entry, str):
                result["innerCredentials"].append(unwrap(entry, depth + 1))
            elif isinstance(entry, dict) and is_enveloped(entry):
                result["innerCredentials"].append(
                    unwrap(json.dumps(entry), depth + 1))
            else:
                result["innerCredentials"].append({"format": "inline", "content": entry})
    return result


# --- Pretty printing ---

INDENT = "  "


def pretty_print(tree: dict, depth: int = 0) -> None:
    prefix = INDENT * depth
    fmt = tree.get("format", "unknown")

    if fmt == "JWT":
        print(f"{prefix}JWT")
        print(f"{prefix}  header: {json.dumps(tree['header'])}")
        payload = tree["payload"]
        typ = resolve_type(payload)
        iss = payload.get("iss") or payload.get("issuer")
        sub = payload.get("sub") or payload.get("credentialSubject", {}).get("id")
        print(f"{prefix}  type:   {typ}")
        print(f"{prefix}  iss:    {iss}")
        if sub:
            print(f"{prefix}  sub:    {sub}")
        valid_from = payload.get("validFrom") or payload.get("iat")
        valid_until = payload.get("validUntil") or payload.get("exp")
        if valid_from:
            print(f"{prefix}  from:   {valid_from}")
        if valid_until:
            print(f"{prefix}  until:  {valid_until}")
        # Show credentialSubject briefly
        cs = payload.get("credentialSubject")
        if cs:
            cs_type = resolve_type(cs) if isinstance(cs, dict) else None
            if cs_type:
                print(f"{prefix}  credentialSubject.type: {cs_type}")
            props = [k for k in (cs if isinstance(cs, dict) else {})
                     if k not in ("id", "@id", "type", "@type")]
            if props:
                print(f"{prefix}  credentialSubject keys: {props}")

    elif fmt in ENVELOPED_TYPES:
        media = tree.get("dataUriMediaType", "?")
        print(f"{prefix}{fmt} (data:{media})")
        if "inner" in tree:
            pretty_print(tree["inner"], depth + 1)
        if "error" in tree:
            print(f"{prefix}  error: {tree['error']}")

    elif fmt == "JSON-LD":
        content = tree["content"]
        typ = resolve_type(content)
        print(f"{prefix}JSON-LD  type: {typ}")

    elif fmt == "inline":
        content = tree.get("content", {})
        typ = resolve_type(content) if isinstance(content, dict) else None
        print(f"{prefix}inline VC  type: {typ or '?'}")

    else:
        print(f"{prefix}unknown format")
        if "preview" in tree:
            print(f"{prefix}  {tree['preview']}")

    for inner in tree.get("innerCredentials", []):
        pretty_print(inner, depth + 1)


# --- Main ---

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Unwrap and inspect JWT / enveloped credentials",
    )
    parser.add_argument("file", help="Credential file to inspect (use '-' for stdin)")
    parser.add_argument("--summary", action="store_true",
                        help="Show compact summary instead of full JSON payloads")
    args = parser.parse_args()

    if args.file == "-":
        content = sys.stdin.read()
    else:
        path = Path(args.file)
        if not path.exists():
            print(f"Error: file not found: {path}", file=sys.stderr)
            sys.exit(1)
        content = path.read_text()

    tree = unwrap(content)

    if args.summary:
        pretty_print(tree)
    else:
        print(json.dumps(tree, indent=2))


if __name__ == "__main__":
    main()
