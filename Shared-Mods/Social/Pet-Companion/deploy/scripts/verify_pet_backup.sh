#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 1 ]] || {
    echo "usage: verify_pet_backup.sh BACKUP.sql.gz" >&2
    exit 64
}
backup=$1
checksum="$backup.sha256"

[[ -f $backup && ! -L $backup && -s $backup ]] || {
    echo "backup must be a non-empty regular non-symlink file" >&2
    exit 66
}
[[ -f $checksum && ! -L $checksum ]] || {
    echo "checksum sidecar is missing or unsafe" >&2
    exit 66
}
command -v gzip >/dev/null || {
    echo "gzip is required" >&2
    exit 69
}
command -v sha256sum >/dev/null || {
    echo "sha256sum is required" >&2
    exit 69
}

expected=$(awk 'NR == 1 { print $1 }' "$checksum")
[[ $expected =~ ^[0-9a-f]{64}$ ]] || {
    echo "checksum sidecar is malformed" >&2
    exit 65
}
actual=$(sha256sum -- "$backup" | awk '{ print $1 }')
[[ $actual == "$expected" ]] || {
    echo "backup checksum mismatch" >&2
    exit 65
}
gzip -t -- "$backup"
echo "backup checksum and gzip stream are valid"
