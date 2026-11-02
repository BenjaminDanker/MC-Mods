#!/usr/bin/env bash
set -euo pipefail

expected="${1:?expected sha256 required}"
[[ "$expected" =~ ^[a-f0-9]{64}$ ]]
home_dir="$HOME/pet-companion"
jar="$home_dir/lib/pet-service-0.1.0-SNAPSHOT.jar"
candidate="$home_dir/dev-player/pet-service-candidate.jar"
[[ -f "$jar" && -f "$candidate" ]]
[[ "$(sha256sum "$candidate" | cut -d' ' -f1)" == "$expected" ]]

# The temporary pathname is in the same filesystem as the running jar.
cp "$candidate" "$jar.next"
[[ "$(sha256sum "$jar.next" | cut -d' ' -f1)" == "$expected" ]]
mv -f "$jar.next" "$jar"
sync "$jar"
systemctl --user restart pet-companion.service
systemctl --user is-active --quiet pet-companion.service
[[ "$(sha256sum "$jar" | cut -d' ' -f1)" == "$expected" ]]
echo "Deployed service jar atomically; sha256=$expected"
