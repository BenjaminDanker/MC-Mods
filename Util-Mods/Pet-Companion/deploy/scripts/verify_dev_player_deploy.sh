#!/usr/bin/env bash
set -euo pipefail
source "$HOME/.config/pet-companion/pet-service.env"
printf 'header = "Authorization: Bearer %s"\n' "$PET_SERVICE_TOKEN" |
  curl --fail --silent --show-error --config - \
    "http://${PET_SERVICE_BIND_ADDRESS}:${PET_SERVICE_PORT}/health/ready"
echo
systemctl --user is-active pet-companion.service
sha256sum "$HOME/pet-companion/lib/pet-service-0.1.0-SNAPSHOT.jar"
journalctl --user -u pet-companion.service -n 25 --no-pager |
  grep -E 'service_startup|ERROR|Exception|migration|IllegalState|ready' || true
