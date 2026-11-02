#!/usr/bin/env bash
set -euo pipefail

# Operator-only on raspberrypi. No HTTP development route is exposed.
service_home="$HOME/pet-companion"
source "$HOME/.config/pet-companion/pet-service.env"
DEV_PLAYER_QDRANT_API_KEY="${PET_QDRANT_API_KEY:-}"
unset PET_DB_JDBC_URL PET_DB_USER PET_DB_PASSWORD PET_SERVICE_TOKEN
unset PET_ACCOUNT_LINK_PEPPER PET_STRIPE_SECRET_KEY PET_STRIPE_WEBHOOK_SECRET
source "$HOME/.config/pet-companion/dev-player.env"
export DEV_PLAYER_DB_JDBC_URL DEV_PLAYER_DB_USER DEV_PLAYER_DB_PASSWORD
export DEV_PLAYER_QDRANT_URL DEV_PLAYER_QDRANT_COLLECTION DEV_PLAYER_QDRANT_API_KEY
export DEV_PLAYER_STATE_DIR PET_OPENAI_API_KEY PET_OPENAI_BASE_URL
export PET_OPENROUTER_API_KEY PET_OPENROUTER_BASE_URL PET_OPENROUTER_FALLBACK
export PET_DIALOGUE_MODEL PET_CONSOLIDATION_MODEL PET_EMBEDDING_MODEL PET_PLANNER_MODEL
export PET_EMBEDDING_DIMENSION PET_MODERATION_MODEL PET_MODERATION_BLOCKED_CATEGORIES PET_MEMORY_MODEL
export PET_DIALOGUE_REASONING_EFFORT
# DEV_PLAYER_<NAME> (for example DEV_PLAYER_DIALOGUE_MODEL) overrides PET_<NAME> for one run.
candidate="$service_home/dev-player/pet-service-candidate.jar"
candidate_common="$service_home/dev-player/pet-common-candidate.jar"
if [[ "${DEV_PLAYER_CANDIDATE:-0}" == '1' ]]; then
  if [[ ! -f "$candidate" || ! -f "$candidate_common" ]]; then
    echo 'Requested Dev Player candidate jars are missing; refusing installed fallback.' >&2
    exit 1
  fi
  classpath="$candidate:$candidate_common:$service_home/lib/*"
else
  classpath="$service_home/lib/*"
fi
exec /usr/lib/jvm/temurin-25-jdk-arm64/bin/java -cp "$classpath" \
  com.silver.aipets.service.devplayer.DevPlayerCli "$@"
