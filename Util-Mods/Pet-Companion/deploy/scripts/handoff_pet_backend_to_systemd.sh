#!/usr/bin/env bash
set -euo pipefail

backend="${1:?backend required}"
manual_pid="${2:?manual screen PID required}"
case "$backend" in
  cave) port=25572 ;;
  desert) port=25571 ;;
  magic) port=25573 ;;
  ocean) port=25569 ;;
  sky-island) port=25568 ;;
  vanilla) port=25567 ;;
  *) echo "Unknown backend: $backend" >&2; exit 2 ;;
esac
[[ "$manual_pid" =~ ^[0-9]+$ ]]
unit="$backend-mc-server.service"
session="$manual_pid.$backend-mc-server"
status_probe="$(dirname "$0")/check_minecraft_status.py"
test -f "$status_probe"
screen -ls | grep -Fq "$session"
test "$(systemctl show "$unit" -p MainPID --value)" != "$manual_pid"

# Stop only the manually launched instance. The enabled systemd unit is already
# retrying and takes ownership after the world's lock is released.
screen -S "$session" -p 0 -X stuff $'\025stop\r'
for ((attempt=0; attempt<180; attempt++)); do
  if ! screen -ls | grep -Fq "$session"; then break; fi
  sleep 1
done
if screen -ls | grep -Fq "$session"; then
  echo "$backend manual session did not stop" >&2
  exit 1
fi

for ((attempt=0; attempt<240; attempt++)); do
  main_pid="$(systemctl show "$unit" -p MainPID --value)"
  if [[ "$main_pid" =~ ^[1-9][0-9]*$ ]] \
      && test "$main_pid" != "$manual_pid" \
      && python3 "$status_probe" "$port" >/dev/null 2>&1; then
    echo "$backend systemd-owned and Minecraft-status responsive: main_pid=$main_pid"
    exit 0
  fi
  sleep 1
done
echo "$backend did not become systemd-owned and status responsive" >&2
exit 1
