#!/usr/bin/env bash
set -euo pipefail

# Verifies hot-reload works for either dev workflow.
# Procedure:
# 1. Start the stack with:  ./dev.sh up  (local server)  or  ./dev.sh watch  (containerized)
# 2. Run this script to modify the code, rebuild, and expect a different response

URL="http://localhost:8081/dev/ping"
EXPECTED_BEFORE="pong-v1"
EXPECTED_AFTER="pong-v2"
FILE="../fc-service-server/src/main/java/eu/xfsc/fc/server/util/DevPingController.java"
TIMEOUT=60

echo "=== Hot-Reload Verification ==="

echo "1. Checking current response..."
RESPONSE=$(curl -sf "$URL" 2>/dev/null || echo "UNAVAILABLE")
echo "   Response: $RESPONSE"

if [ "$RESPONSE" != "$EXPECTED_BEFORE" ]; then
  echo "   ERROR: Expected '$EXPECTED_BEFORE', got '$RESPONSE'"
  echo "   Make sure the server is running: ./dev.sh up or ./dev.sh watch"
  exit 1
fi

echo "2. Modifying DevPingController (pong-v1 -> pong-v2)..."
sed -i.bak 's/pong-v1/pong-v2/' "$FILE"

echo "3. Building changed module..."
./dev.sh build -q
echo "   Build done. Waiting for reload..."

echo "4. Waiting for DevTools restart (up to ${TIMEOUT}s)..."
for i in $(seq 1 "$TIMEOUT"); do
  sleep 1
  RESPONSE=$(curl -sf "$URL" 2>/dev/null || echo "UNAVAILABLE")
  if [ "$RESPONSE" = "$EXPECTED_AFTER" ]; then
    echo "   Hot-reload detected after ${i}s!"
    echo "   Response: $RESPONSE"
    echo ""
    echo "5. Restoring original file..."
    mv "$FILE.bak" "$FILE"
    echo "=== SUCCESS: Hot-reload is working ==="
    exit 0
  fi
  printf "   [%2ds] %s\n" "$i" "$RESPONSE"
done

echo "   TIMEOUT: Hot-reload did not propagate within ${TIMEOUT}s"
echo "5. Restoring original file..."
mv "$FILE.bak" "$FILE"
exit 1