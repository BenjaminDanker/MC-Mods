#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "usage: backup_pet_database.sh BACKUP_DIR CLIENT_CNF DATABASE" >&2
    exit 64
}

[[ $# -eq 3 ]] || usage
backup_dir=$1
client_cnf=$2
database=$3

[[ $database =~ ^[A-Za-z0-9_]+$ ]] || {
    echo "database must contain only letters, numbers, and underscore" >&2
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
command -v mariadb-dump >/dev/null || {
    echo "mariadb-dump is required" >&2
    exit 69
}
command -v gzip >/dev/null || {
    echo "gzip is required" >&2
    exit 69
}
command -v sha256sum >/dev/null || {
    echo "sha256sum is required" >&2
    exit 69
}

umask 077
install -d -m 0700 -- "$backup_dir"
backup_dir=$(realpath -e -- "$backup_dir")
timestamp=$(date -u +'%Y%m%dT%H%M%SZ')
final_name="pet-companion-${database}-${timestamp}.sql.gz"
final_path="$backup_dir/$final_name"
checksum_path="$final_path.sha256"
[[ ! -e $final_path && ! -e $checksum_path ]] || {
    echo "refusing to overwrite an existing backup" >&2
    exit 73
}

temporary_sql=$(mktemp --tmpdir="$backup_dir" ".pet-companion-${timestamp}.XXXXXX.sql")
temporary_gzip="$temporary_sql.gz"
cleanup() {
    rm -f -- "$temporary_sql" "$temporary_gzip"
}
trap cleanup EXIT HUP INT TERM

# --single-transaction + --quick provides a consistent, streamed snapshot for the
# all-InnoDB pet schema without holding table locks for the duration of the dump.
mariadb-dump \
    --defaults-extra-file="$client_cnf" \
    --single-transaction \
    --quick \
    --skip-lock-tables \
    --default-character-set=utf8mb4 \
    --hex-blob \
    --routines \
    --events \
    --triggers \
    "$database" > "$temporary_sql"

[[ -s $temporary_sql ]] || {
    echo "mariadb-dump produced an empty backup" >&2
    exit 74
}
gzip -9 -- "$temporary_sql"
chmod 0600 -- "$temporary_gzip"
mv -- "$temporary_gzip" "$final_path"
(
    cd -- "$backup_dir"
    sha256sum -- "$final_name" > "$final_name.sha256"
)
chmod 0600 -- "$checksum_path"
trap - EXIT HUP INT TERM

echo "$final_path"
