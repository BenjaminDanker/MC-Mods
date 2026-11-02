#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 4 && $4 == "--confirm-restore" ]] || {
    echo "usage: restore_pet_database.sh BACKUP.sql.gz CLIENT_CNF TARGET_DATABASE --confirm-restore" >&2
    exit 64
}
backup=$1
client_cnf=$2
target_database=$3
[[ $target_database =~ ^[A-Za-z0-9_]+$ ]] || {
    echo "target database must contain only letters, numbers, and underscore" >&2
    exit 64
}
[[ -f $client_cnf && ! -L $client_cnf ]] || {
    echo "CLIENT_CNF must be a regular non-symlink file" >&2
    exit 66
}
client_mode=$(stat -c '%a' -- "$client_cnf")
[[ $client_mode == 400 || $client_mode == 440
        || $client_mode == 600 || $client_mode == 640 ]] || {
    echo "CLIENT_CNF must be mode 0400, 0440, 0600, or 0640" >&2
    exit 77
}
command -v mariadb >/dev/null || {
    echo "mariadb client is required" >&2
    exit 69
}

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
"$script_dir/verify_pet_backup.sh" "$backup"

# The explicit confirmation is intentionally required because a normal logical dump
# contains DROP/CREATE statements and can replace newer target data.
gzip -dc -- "$backup" | mariadb \
    --defaults-extra-file="$client_cnf" \
    --database="$target_database"
"$script_dir/verify_pet_database.sh" "$client_cnf" "$target_database"
echo "restore completed and structural verification passed for $target_database"
