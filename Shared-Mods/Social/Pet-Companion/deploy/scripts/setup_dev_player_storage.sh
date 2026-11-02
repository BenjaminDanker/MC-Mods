#!/usr/bin/env bash
set -euo pipefail

# Run only on raspberrypi as the operator after copying db/migrations beside this script.
state_dir="$HOME/.config/pet-companion"
env_file="$state_dir/dev-player.env"
schema='pet_companion_dev_player'
account='pet_dev_player'
collection='pet_companion_dev_player_memories'
if [[ -e "$env_file" ]]; then
  echo "Existing isolated credentials retained at $env_file"
  exit 0
fi
mkdir -p "$state_dir"
chmod 700 "$state_dir"
password="$(openssl rand -hex 32)"
sudo -n mariadb <<SQL
CREATE DATABASE IF NOT EXISTS \`$schema\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$account'@'127.0.0.1' IDENTIFIED BY '$password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON \`$schema\`.* TO '$account'@'127.0.0.1';
SQL
umask 077
cat >"$env_file" <<ENV
DEV_PLAYER_DB_JDBC_URL=jdbc:mariadb://127.0.0.1:3306/$schema
DEV_PLAYER_DB_USER=$account
DEV_PLAYER_DB_PASSWORD=$password
DEV_PLAYER_QDRANT_URL=http://127.0.0.1:6333
DEV_PLAYER_QDRANT_COLLECTION=$collection
DEV_PLAYER_STATE_DIR=$HOME/.local/state/pet-dev-player
ENV
chmod 600 "$env_file"
echo "Created isolated schema and scoped account; credentials stored mode 0600 at $env_file"
