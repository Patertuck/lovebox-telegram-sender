#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

INSTALL_DIR="${1:-/opt/lovebox-telegram-sender}"
APP_DIR="${INSTALL_DIR%/}"
DATA_DIR="${APP_DIR}/data"
ENV_FILE="${APP_DIR}/.env"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"
ENV_TEMPLATE="${REPO_ROOT}/.env.example"
COMPOSE_TEMPLATE="${REPO_ROOT}/docker-compose.yml"

PLACEHOLDER_VALUES=(
  "replace-me"
  "change-me"
  "me@email.com"
  "mySecret"
  "Lovebox_bot"
  "Signature"
  "42fab8322d8cec91"
  "417a114e58e15a0214cf3612"
)

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

require_docker_compose() {
  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose plugin is required. Install it and retry." >&2
    exit 1
  fi
}

ensure_directory_structure() {
  mkdir -p "${DATA_DIR}"
}

install_runtime_files() {
  if [[ ! -f "${COMPOSE_FILE}" ]]; then
    cp "${COMPOSE_TEMPLATE}" "${COMPOSE_FILE}"
    echo "Copied docker-compose.yml to ${COMPOSE_FILE}"
  fi

  if [[ ! -f "${ENV_FILE}" ]]; then
    cp "${ENV_TEMPLATE}" "${ENV_FILE}"
    echo "Created ${ENV_FILE} from .env.example"
    echo "Fill in the required values, place your database at ${DATA_DIR}/messages.db, then rerun this script."
    exit 1
  fi
}

ensure_port_is_free() {
  if command -v ss >/dev/null 2>&1; then
    if ss -ltn '( sport = :8080 )' | tail -n +2 | grep -q .; then
      echo "Port 8080 is already in use. Free the port or change the compose port mapping before continuing." >&2
      exit 1
    fi
    return
  fi

  if command -v netstat >/dev/null 2>&1; then
    if netstat -ltn 2>/dev/null | awk '{print $4}' | grep -Eq '(^|:)8080$'; then
      echo "Port 8080 is already in use. Free the port or change the compose port mapping before continuing." >&2
      exit 1
    fi
  fi
}

check_env_placeholders() {
  local placeholder
  for placeholder in "${PLACEHOLDER_VALUES[@]}"; do
    if grep -Fq "${placeholder}" "${ENV_FILE}"; then
      echo "Found placeholder value '${placeholder}' in ${ENV_FILE}. Update the file with real values before starting the container." >&2
      exit 1
    fi
  done
}

check_required_env_keys() {
  local required_keys=(
    "LOVEBOX_TELEGRAM_SENDER_IMAGE="
    "LOVEBOX_ENABLED="
    "LOVEBOX_EMAIL="
    "LOVEBOX_PASSWORD="
    "LOVEBOX_SIGNATURE="
    "LOVEBOX_DEVICE_ID="
    "LOVEBOX_BOX_ID="
    "BOT_USERNAME="
    "BOT_TOKEN="
    "BOT_ALLOWED_CHAT_ID="
    "INTEGRATION_SCHEDULER_TOKEN="
    "INTEGRATION_MESSAGES_DB_PATH="
  )

  local key
  for key in "${required_keys[@]}"; do
    if ! grep -Eq "^${key}" "${ENV_FILE}"; then
      echo "Missing required entry '${key%*=}' in ${ENV_FILE}." >&2
      exit 1
    fi
  done
}

check_database_file() {
  local db_file="${DATA_DIR}/messages.db"
  if [[ ! -f "${db_file}" ]]; then
    echo "Missing database file: ${db_file}" >&2
    echo "Copy your existing messages.db into ${DATA_DIR}/messages.db and rerun this script." >&2
    exit 1
  fi
}

start_container() {
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" pull
  docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d
}

print_next_steps() {
  cat <<EOF

Deployment completed in ${APP_DIR}

Useful commands:
  cd ${APP_DIR}
  docker compose ps
  docker compose logs -f lovebox-telegram-sender
  docker compose restart lovebox-telegram-sender

EOF
}

main() {
  require_command docker
  require_docker_compose
  ensure_directory_structure
  install_runtime_files
  check_required_env_keys
  check_env_placeholders
  check_database_file
  ensure_port_is_free
  start_container
  print_next_steps
}

main "$@"
