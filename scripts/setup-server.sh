#!/usr/bin/env bash

set -euo pipefail

INSTALL_DIR="${1:-/opt/lovebox-telegram-sender}"
APP_DIR="${INSTALL_DIR%/}"
DATA_DIR="${APP_DIR}/data"
ENV_FILE="${APP_DIR}/.env"
COMPOSE_FILE="${APP_DIR}/docker-compose.yml"

PLACEHOLDER_VALUES=(
  "replace-me"
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
  mkdir -p "${DATA_DIR}/pictures"
}

write_compose_file() {
  cat > "${COMPOSE_FILE}" <<'EOF'
services:
  lovebox-telegram-sender:
    image: ${LOVEBOX_TELEGRAM_SENDER_IMAGE:-patertuck/lovebox-telegram-sender:main}
    container_name: lovebox-telegram-sender
    env_file:
      - ./.env
    environment:
      MESSAGES_DATABASE_PATH: /app/data/messages.db
      MESSAGES_PICTURES_PATH: /app/data/pictures
    volumes:
      - ./data:/app/data
    restart: unless-stopped
EOF
}

write_env_template() {
  cat > "${ENV_FILE}" <<'EOF'
# Docker Image
LOVEBOX_TELEGRAM_SENDER_IMAGE="patertuck/lovebox-telegram-sender:main"

# Lovebox Login
LOVEBOX_ENABLED=true
LOVEBOX_EMAIL="me@email.com"
LOVEBOX_PASSWORD="mySecret"

# Lovebox Setting
LOVEBOX_SIGNATURE="Signature"
LOVEBOX_DEVICE_ID="42fab8322d8cec91"
LOVEBOX_BOX_ID="417a114e58e15a0214cf3612"

# Telegram Bot Settings
BOT_USERNAME="Lovebox_bot"
BOT_TOKEN="replace-me"
BOT_ALLOWED_CHAT_ID="8782720476"

# Scheduled messages
MESSAGES_DATABASE_PATH="/app/data/messages.db"
MESSAGES_PICTURES_PATH="/app/data/pictures"
EOF
}

install_runtime_files() {
  if [[ ! -f "${COMPOSE_FILE}" ]]; then
    write_compose_file
    echo "Created ${COMPOSE_FILE}"
  fi

  if [[ ! -f "${ENV_FILE}" ]]; then
    write_env_template
    echo "Created ${ENV_FILE}"
    echo "Fill in the required values, place your database at ${DATA_DIR}/messages.db, then rerun this script."
    exit 1
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
    "MESSAGES_DATABASE_PATH="
    "MESSAGES_PICTURES_PATH="
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
  start_container
  print_next_steps
}

main "$@"
