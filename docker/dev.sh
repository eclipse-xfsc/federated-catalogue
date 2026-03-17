#!/usr/bin/env bash
set -euo pipefail

# Convenience wrapper for common docker compose dev workflows.
# Works with Git Bash on Windows and any POSIX shell on macOS/Linux.
#
# Usage: ./dev.sh <command>

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_DEV="docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file dev.env"
COMPOSE_FULL="docker compose --env-file dev.env"
COMPOSE_STRICT="docker compose -f docker-compose.yml -f docker-compose.strict.yml --env-file dev.env"

require_jar() {
  local jar
  jar=$(ls ../fc-service-server/target/fc-service-server-*.jar 2>/dev/null | head -1)
  if [ -z "$jar" ]; then
    echo "Error: No fc-service-server JAR found. Run './dev.sh build' first."
    exit 1
  fi
}

usage() {
  cat <<EOF
Usage: ./dev.sh <command> [options]

COMMANDS:
  up          Start infrastructure only (postgres, neo4j, keycloak, nats)
              Use this to run fc-server locally with Maven
              Example: ./dev.sh up

  run         Run fc-server locally with Spring Boot (requires 'up' first)
              Starts Spring Boot with dev profile and devtools hot-reload
              Example: ./dev.sh run

  watch       Start full stack with hot-reload enabled (containerized server)
              The server container automatically restarts when the JAR changes
              Example: ./dev.sh build && ./dev.sh watch
                       # Edit code, then: ./dev.sh build

  full        Start full stack (requires ./dev.sh build first)
              Always rebuilds container images from locally-built JARs
              Example: ./dev.sh build && ./dev.sh full

  strict      Start full stack with strict config (Gaia-X on, schema validation on)
              Use for running @cfg.strict BDD tests
              Example: ./dev.sh strict

  full-original Start full stack with original docker-compose (with overrides for 2.0.0 image)
              Example: ./dev.sh full-original

  down        Stop and remove all containers
              Example: ./dev.sh down

  clean       Stop containers and remove all volumes (wipes PostgreSQL, Neo4j data)
              Use this to get a truly fresh start (e.g. after schema/data changes)
              Example: ./dev.sh clean

  build       Build the fc-service-server JAR (skips tests)
              Required after code changes to trigger hot-reload
              Example: ./dev.sh build

  test        Run the hot-reload verification script

  logs        Tail fc-server logs
              Example: ./dev.sh logs
                       ./dev.sh logs -f --tail=100

  ps          Show running containers
              Example: ./dev.sh ps

  help        Show this help message

WORKFLOWS:
  Development Workflow 1 — Run fc-server locally with Spring Boot devtools:
    1. ./dev.sh up       (in terminal 1)
    2. ./dev.sh run      (in terminal 2 - with automatic hot-reload)

  Development Workflow 2 — Containerized server with hot-reload:
    1. ./dev.sh build && ./dev.sh watch
    2. Edit code
    3. ./dev.sh build  (server restarts automatically)

  Development Workflow 3 — Full stack without hot-reload:
    1. ./dev.sh build && ./dev.sh full

  Development Workflow 4 - Full stack with original 2.0.0 image (with overrides):
    1. ./dev.sh full-original

NOTES:
  - All commands pass additional options to docker compose
  - Use ./dev.sh down to clean up when switching workflows
  - Use ./dev.sh clean to wipe DB volumes (removes stale SHACL shapes, etc.)
  - full/strict/full-original always rebuild images (--build) to avoid stale containers
  - The 'watch' command requires Docker Compose v2.22.0+
  - Build artifacts are cached; use 'mvn clean' if needed

For more information, see the README.md file.
EOF
}

case "${1:-}" in
  up)
    # Start infrastructure only by scaling server and portal to 0
    echo "Start infrastructure only (postgres, neo4j, keycloak, nats) for use with manual strat of Spring Boot devtools"
    $COMPOSE_DEV up --scale server=0 --scale portal=0 "${@:2}"
    ;;
  run)
    echo "Starting fc-server with Spring Boot devtools (dev profile)..."
    echo "Hot-reload is enabled - changes will be picked up automatically"
    echo "Press Ctrl+C to stop"
    echo ""
    (cd .. && mvn spring-boot:run -pl fc-service-server -Dspring-boot.run.profiles=dev "${@:2}")
    ;;
  watch)
    echo "Starting full stack with hot-reload enabled (containerized server)..."
    echo "The server container will automatically restart when the JAR changes"
    $COMPOSE_DEV watch "${@:2}"
    ;;
  full)
    require_jar
    echo "Starting full stack (rebuilding images from locally-built JARs)..."
    $COMPOSE_FULL up --build "${@:2}"
    ;;
  strict)
    require_jar
    echo "Starting full stack with strict config (Gaia-X on, schema validation on)..."
    $COMPOSE_STRICT up --build "${@:2}"
    ;;
  full-original)
    echo "Starting full stack with original docker-compose (with overrides for 2.0.0 image)"
    $COMPOSE_FULL -f docker-compose.yml -f docker-compose.original.yml up --build "${@:2}"
    ;;
  down)
    echo "Stopping and removing all containers..."
    $COMPOSE_FULL down "${@:2}"
    ;;
  clean)
    echo "Stopping containers and removing all volumes (fresh start)..."
    $COMPOSE_FULL down -v "${@:2}"
    ;;
  build)
    echo "Building fc-service-server JAR (skipping tests)..."
    # `mvn package` covers both scenarios (bare-metal with spring-boot dev-tools and containerized).
    # For local mode with devtools, `mvn compile` would be enough here.
    (cd .. && mvn clean package -pl fc-service-server,fc-demo-portal -am -DskipTests -Dcheckstyle.skip "${@:2}")
    ;;
  test)
    echo "Running hot-reload verification tests..."
    ./test-hot-reload.sh
    ;;
  logs)
    echo "Tailing fc-server logs..."
    $COMPOSE_DEV logs -f server "${@:2}"
    ;;
  ps)
    echo "Showing running containers..."
    $COMPOSE_DEV ps "${@:2}"
    ;;
  help|--help|-h)
    usage
    exit 0
    ;;
  *)
    echo "Error: Unknown command '${1:-}'"
    echo ""
    usage
    exit 1
    ;;
esac