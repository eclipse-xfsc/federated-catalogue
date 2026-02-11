#!/usr/bin/env bash
set -euo pipefail

# Convenience wrapper for common docker compose dev workflows.
# Works with Git Bash on Windows and any POSIX shell on macOS/Linux.
#
# Usage: ./dev.sh <command>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file dev.env"
COMPOSE_PROD="docker compose --env-file dev.env"

usage() {
  cat <<EOF
Usage: ./dev.sh <command>

Commands:
  up          Start infrastructure only (run fc-server locally)
  watch       Start full stack with hot-reload (containerized server)
  full        Start full stack without hot-reload
  down        Stop and remove all containers
  build       Build the fc-service-server JAR (skips tests) -> You need to run this after changes to hot-reload the application!
  test        Run the hot-reload verification script
  logs        Tail fc-server logs
  ps          Show running containers
EOF
}

case "${1:-}" in
  up)
    $COMPOSE_DEV up "${@:2}"
    ;;
  watch)
    $COMPOSE_DEV --profile full watch "${@:2}"
    ;;
  full)
    $COMPOSE_PROD up "${@:2}"
    ;;
  down)
    $COMPOSE_DEV --profile full down "${@:2}"
    ;;
  build)
    # `mvn package` covers both scenarios (bare-metal with spring-boot dev-tools and containerized).
    # For local mode with devtools, `mvn compile` would be enough here.
    (cd .. && mvn package -pl fc-service-server -am -DskipTests -Dcheckstyle.skip "${@:2}")
    ;;
  test)
    ./test-hot-reload.sh
    ;;
  logs)
    $COMPOSE_DEV --profile full logs -f server "${@:2}"
    ;;
  ps)
    $COMPOSE_DEV --profile full ps "${@:2}"
    ;;
  *)
    usage
    exit 1
    ;;
esac