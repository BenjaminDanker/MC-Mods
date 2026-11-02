#!/usr/bin/env bash
set -euo pipefail
source "$HOME/.config/pet-companion/dev-player.env"
[[ "$DEV_PLAYER_DB_JDBC_URL" == 'jdbc:mariadb://127.0.0.1:3306/pet_companion_dev_player' ]]
[[ "$DEV_PLAYER_DB_USER" == 'pet_dev_player' ]]
export MYSQL_PWD="$DEV_PLAYER_DB_PASSWORD"
for migration in "$(dirname "$0")"/V[0-9][0-9][0-9]__*.sql; do
  version="$(basename "$migration" | cut -c 2-4)"
  if [[ "$version" < "${1:-001}" ]]; then continue; fi
  echo "Applying $(basename "$migration") to isolated Dev Player schema"
  mariadb -h 127.0.0.1 -u "$DEV_PLAYER_DB_USER" pet_companion_dev_player <"$migration"
done
unset MYSQL_PWD DEV_PLAYER_DB_PASSWORD
