#!/usr/bin/env bash
set -euo pipefail

repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
test_root=$(mktemp -d)
cleanup() {
    [[ -n ${test_root:-} && $test_root == /tmp/* ]] && rm -rf -- "$test_root"
}
trap cleanup EXIT HUP INT TERM

fake_bin="$test_root/bin"
backup_dir="$test_root/backups"
client_cnf="$test_root/client.cnf"
restore_capture="$test_root/restored.sql"
mkdir -p -- "$fake_bin" "$backup_dir"
printf '%s\n' '[client]' 'user=test' 'password=not-a-real-secret' > "$client_cnf"
chmod 0600 "$client_cnf"

cat > "$fake_bin/mariadb-dump" <<'FAKE_DUMP'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' \
  'DROP TABLE IF EXISTS pets;' \
  'CREATE TABLE pets (pet_id VARCHAR(36) PRIMARY KEY);' \
  "INSERT INTO pets VALUES ('11111111-1111-1111-1111-111111111111');"
FAKE_DUMP

cat > "$fake_bin/mariadb" <<'FAKE_CLIENT'
#!/usr/bin/env bash
set -euo pipefail
arguments=$*
if [[ $arguments == *information_schema.tables* ]]; then
    printf '16\t3\t2\n'
elif [[ $arguments == *'LEFT JOIN pet_traits'* ]]; then
    printf '0\n'
else
    cat > "$RESTORE_CAPTURE"
fi
FAKE_CLIENT
cat > "$fake_bin/stat" <<'FAKE_STAT'
#!/usr/bin/env bash
set -euo pipefail
if [[ $* == *client.cnf* && $* == *'%a'* ]]; then
    printf '600\n'
else
    /usr/bin/stat "$@"
fi
FAKE_STAT
cat > "$fake_bin/install" <<'FAKE_INSTALL'
#!/usr/bin/env bash
set -euo pipefail
if [[ " $* " == *' -d '* ]]; then
    mkdir -p -- "${@: -1}"
else
    /usr/bin/install "$@"
fi
FAKE_INSTALL
chmod 0755 "$fake_bin/mariadb-dump" "$fake_bin/mariadb" \
    "$fake_bin/stat" "$fake_bin/install"

export PATH="$fake_bin:$PATH"
export RESTORE_CAPTURE="$restore_capture"

backup_path=$("$repository_root/deploy/scripts/backup_pet_database.sh" \
    "$backup_dir" "$client_cnf" pet_restore_test)
[[ -f $backup_path && -f $backup_path.sha256 ]]
"$repository_root/deploy/scripts/verify_pet_backup.sh" "$backup_path" >/dev/null

tampered="$backup_dir/tampered.sql.gz"
cp -- "$backup_path" "$tampered"
cp -- "$backup_path.sha256" "$tampered.sha256"
printf 'tamper' >> "$tampered"
if "$repository_root/deploy/scripts/verify_pet_backup.sh" "$tampered" >/dev/null 2>&1; then
    echo "tampered backup unexpectedly verified" >&2
    exit 1
fi

if "$repository_root/deploy/scripts/restore_pet_database.sh" \
        "$backup_path" "$client_cnf" pet_restore_drill >/dev/null 2>&1; then
    echo "restore unexpectedly ran without explicit confirmation" >&2
    exit 1
fi
"$repository_root/deploy/scripts/restore_pet_database.sh" \
    "$backup_path" "$client_cnf" pet_restore_drill --confirm-restore >/dev/null
grep -q 'CREATE TABLE pets' "$restore_capture"

if "$repository_root/deploy/scripts/backup_pet_database.sh" \
        "$backup_dir" "$client_cnf" 'invalid-name' >/dev/null 2>&1; then
    echo "invalid database name unexpectedly passed" >&2
    exit 1
fi

echo "backup tools self-test passed"
