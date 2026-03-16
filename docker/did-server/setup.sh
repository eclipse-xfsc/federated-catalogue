#!/usr/bin/env bash
# Generates a self-contained did:web trust environment for BDD testing.
#
# Creates:
#   certs/ca.key, ca.crt          — local CA
#   certs/server.key, server.crt  — TLS cert for did-server (signed by CA)
#   certs/custom-cacerts           — JVM truststore (default CAs + our CA)
#   www/.well-known/did.json      — DID document with RSA public key
#
# Usage: ./setup.sh [path-to-rsa2048.sign.pem]

set -euo pipefail
cd "$(dirname "$0")"

SIGNER_KEY="${1:-../../fc-tools/signer/src/main/resources/rsa2048.sign.pem}"
HOSTNAME="did-server"

mkdir -p certs www/.well-known

# --- 1. Generate local CA ---
if [ ! -f certs/ca.key ]; then
  echo "==> Generating local CA..."
  openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout certs/ca.key -out certs/ca.crt \
    -days 3650 -subj "/CN=FC Test CA"
fi

# --- 2. Generate server cert signed by CA ---
if [ ! -f certs/server.key ]; then
  echo "==> Generating server cert for ${HOSTNAME}..."
  openssl req -newkey rsa:2048 -nodes \
    -keyout certs/server.key -out certs/server.csr \
    -subj "/CN=${HOSTNAME}"

  openssl x509 -req -in certs/server.csr \
    -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial \
    -out certs/server.crt -days 3650 \
    -extfile <(printf "subjectAltName=DNS:${HOSTNAME}")

  rm -f certs/server.csr certs/ca.srl
fi

# --- 3. Create JVM truststore (default CAs + our CA) ---
if [ ! -f certs/custom-cacerts ]; then
  echo "==> Creating custom JVM truststore..."
  # Find default cacerts
  JAVA_CACERTS=""
  if [ -n "${JAVA_HOME:-}" ] && [ -f "${JAVA_HOME}/lib/security/cacerts" ]; then
    JAVA_CACERTS="${JAVA_HOME}/lib/security/cacerts"
  elif command -v java >/dev/null 2>&1; then
    JAVA_CACERTS="$(java -XshowSettings:property -version 2>&1 | grep java.home | awk '{print $3}')/lib/security/cacerts"
  fi

  if [ -n "$JAVA_CACERTS" ] && [ -f "$JAVA_CACERTS" ]; then
    cp "$JAVA_CACERTS" certs/custom-cacerts
  else
    echo "WARNING: Could not find default cacerts, creating empty truststore"
    keytool -genkeypair -alias dummy -keystore certs/custom-cacerts \
      -storepass changeit -keypass changeit -dname "CN=dummy" -keyalg RSA 2>/dev/null
    keytool -delete -alias dummy -keystore certs/custom-cacerts -storepass changeit 2>/dev/null
  fi

  keytool -import -trustcacerts -noprompt \
    -keystore certs/custom-cacerts -storepass changeit \
    -alias fc-test-ca -file certs/ca.crt
fi

# --- 4. Generate signer certificate + chain ---
mkdir -p www/certs
if [ ! -f certs/signer.crt ]; then
  echo "==> Generating signer certificate..."
  openssl req -new -key "${SIGNER_KEY}" -out certs/signer.csr -subj "/CN=FC Test Signer"
  openssl x509 -req -in certs/signer.csr \
    -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial \
    -out certs/signer.crt -days 3650
  rm -f certs/signer.csr certs/ca.srl
fi
cat certs/signer.crt certs/ca.crt > www/certs/chain.pem

# --- 5. Generate DID document from RSA public key ---
echo "==> Generating DID document from ${SIGNER_KEY}..."
python3 -c "
import json, base64, sys
from cryptography.hazmat.primitives.serialization import load_pem_private_key, Encoding, PublicFormat
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicNumbers

with open('${SIGNER_KEY}', 'rb') as f:
    private_key = load_pem_private_key(f.read(), password=None)

pub = private_key.public_key().public_numbers()

def b64url(n, length):
    return base64.urlsafe_b64encode(n.to_bytes(length, 'big')).rstrip(b'=').decode()

n_bytes = (pub.n.bit_length() + 7) // 8
jwk = {
    'kty': 'RSA',
    'e': b64url(pub.e, 3),
    'n': b64url(pub.n, n_bytes),
    'kid': 'signRSA2048',
    'alg': 'PS256',
    'x5u': 'https://${HOSTNAME}/certs/chain.pem',
}

did = 'did:web:${HOSTNAME}'
did_doc = {
    '@context': ['https://www.w3.org/ns/did/v1', 'https://w3id.org/security/suites/jws-2020/v1'],
    'id': did,
    'verificationMethod': [{
        'id': did + '#0',
        'type': 'JsonWebKey2020',
        'controller': did,
        'publicKeyJwk': jwk,
    }],
    'authentication': [did + '#0'],
    'assertionMethod': [did + '#0'],
}

with open('www/.well-known/did.json', 'w') as f:
    json.dump(did_doc, f, indent=2)

print('DID document written: ' + did)
print('Verification method: ' + did + '#0')
"

echo "==> Setup complete."
echo "    DID:    did:web:${HOSTNAME}"
echo "    Method: did:web:${HOSTNAME}#0"