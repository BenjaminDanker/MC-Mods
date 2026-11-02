#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "Run this installer with sudo on the VPS." >&2
    exit 1
fi

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 /path/to/edited-pet-stripe-caddyfile" >&2
    exit 1
fi

source_file=$1
if [ ! -r "$source_file" ]; then
    echo "Caddyfile not found: $source_file" >&2
    exit 1
fi

apt-get update
apt-get install -y debian-keyring debian-archive-keyring apt-transport-https curl gnupg
install -d -m 0755 /etc/apt/keyrings
if [ ! -s /etc/apt/keyrings/caddy-stable-archive-keyring.gpg ]; then
    curl -1sLf https://dl.cloudsmith.io/public/caddy/stable/gpg.key \
        | gpg --dearmor -o /etc/apt/keyrings/caddy-stable-archive-keyring.gpg
fi
printf '%s\n' \
    'deb [signed-by=/etc/apt/keyrings/caddy-stable-archive-keyring.gpg] https://dl.cloudsmith.io/public/caddy/stable/deb/debian any-version main' \
    > /etc/apt/sources.list.d/caddy-stable.list
apt-get update
apt-get install -y caddy

install -d -m 0755 /etc/caddy
if [ -f /etc/caddy/Caddyfile ]; then
    cp -a /etc/caddy/Caddyfile "/etc/caddy/Caddyfile.backup.$(date -u +%Y%m%d%H%M%S)"
fi
install -o root -g root -m 0644 "$source_file" /etc/caddy/Caddyfile
/usr/bin/caddy validate --config /etc/caddy/Caddyfile
systemctl enable --now caddy
systemctl reload caddy
systemctl --no-pager --full status caddy | head -n 20
