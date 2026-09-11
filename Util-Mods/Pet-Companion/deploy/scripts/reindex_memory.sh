#!/usr/bin/env bash
set -euo pipefail

# Operator-only trigger. The service remains authoritative: this command only asks it
# to page active relational cards and enqueue idempotent embedding jobs.
: "${PET_SERVICE_BASE_URI:?PET_SERVICE_BASE_URI must be set (for example http://127.0.0.1:8787)}"
: "${PET_SERVICE_TOKEN:?PET_SERVICE_TOKEN must be set in the protected operator environment}"

base_uri=${PET_SERVICE_BASE_URI%/}
page_size=${PET_REINDEX_PAGE_SIZE:-250}

[[ $base_uri =~ ^https?://[^[:space:]@]+$ && $base_uri != *\?* && $base_uri != *#* ]] || {
    echo "PET_SERVICE_BASE_URI must be an http(s) origin without credentials, query, or fragment" >&2
    exit 64
}
[[ $page_size =~ ^[0-9]+$ && $page_size -ge 1 && $page_size -le 1000 ]] || {
    echo "PET_REINDEX_PAGE_SIZE must be an integer between 1 and 1000" >&2
    exit 64
}
[[ ${#PET_SERVICE_TOKEN} -ge 32 ]] || {
    echo "PET_SERVICE_TOKEN must contain at least 32 characters" >&2
    exit 64
}
command -v curl >/dev/null || {
    echo "curl is required" >&2
    exit 69
}

# Feed the bearer header through curl's stdin config rather than placing the secret in
# the process argument list. The response is the bounded JSON count from the service.
response=$(printf '%s\n' \
    "url = \"${base_uri}/v1/admin/memory/reindex?pageSize=${page_size}\"" \
    'request = "POST"' \
    "header = \"Authorization: Bearer ${PET_SERVICE_TOKEN}\"" \
    'header = "Content-Length: 0"' \
    'header = "Accept: application/json"' \
    | curl --config - --fail --silent --show-error --connect-timeout 5 --max-time 30)
printf '%s\n' "$response"
