#!/usr/bin/env bash
set -euo pipefail
source "$HOME/.config/pet-companion/pet-service.env"
source "$HOME/.config/pet-companion/dev-player.env"
[[ "$DEV_PLAYER_DB_JDBC_URL" == 'jdbc:mariadb://127.0.0.1:3306/pet_companion_dev_player' ]]
export MYSQL_PWD="$DEV_PLAYER_DB_PASSWORD"
mariadb -h 127.0.0.1 -u pet_dev_player -N pet_companion_dev_player <<'SQL'
SELECT 'pets',COUNT(*) FROM pets;
SELECT 'events',COUNT(*) FROM pet_events;
SELECT 'active_cards',COUNT(*) FROM long_term_memories WHERE active=1;
SELECT 'ready_cards',COUNT(*) FROM long_term_memories WHERE active=1 AND embedding_status='READY';
SELECT 'successful_consolidations',COUNT(*) FROM jobs
 WHERE job_type='SLEEP_CONSOLIDATION' AND status='SUCCEEDED';
SQL
unset MYSQL_PWD DEV_PLAYER_DB_PASSWORD
printf 'header = "api-key: %s"\n' "$PET_QDRANT_API_KEY" |
  curl --fail --silent --show-error --config - \
    -H 'Content-Type: application/json' \
    -d '{"exact":true}' \
    'http://127.0.0.1:6333/collections/pet_companion_dev_player_memories/points/count' |
  python3 -c 'import json,sys; d=json.load(sys.stdin); print("qdrant_points",d["result"]["count"])'
