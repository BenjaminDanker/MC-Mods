#!/usr/bin/env bash
set -euo pipefail

[[ $# -eq 2 ]] || {
    echo "usage: verify_pet_database.sh CLIENT_CNF DATABASE" >&2
    exit 64
}
client_cnf=$1
database=$2
[[ $database =~ ^[A-Za-z0-9_]+$ ]] || {
    echo "database must contain only letters, numbers, and underscore" >&2
    exit 64
}
[[ -f $client_cnf && ! -L $client_cnf ]] || {
    echo "CLIENT_CNF must be a regular non-symlink file" >&2
    exit 66
}
command -v mariadb >/dev/null || {
    echo "mariadb client is required" >&2
    exit 69
}

schema_counts=$(mariadb \
    --defaults-extra-file="$client_cnf" \
    --batch --skip-column-names \
    --database="$database" \
    --execute="
        SELECT
          (SELECT COUNT(*) FROM information_schema.tables
           WHERE table_schema = DATABASE() AND engine = 'InnoDB'
             AND table_name IN (
               'pets','pet_traits','pet_mood','pet_sleep_state','pet_events',
               'long_term_memories','long_term_memory_source_events',
               'long_term_memory_revisions','trait_change_audit','subscriptions',
               'stripe_webhook_events','pet_recall_usage','ai_usage','jobs',
               'account_link_tokens','idempotency_requests','pending_adoptions')),
          (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'pet_recall_usage'
             AND column_name IN (
               'request_fingerprint','compensation_fingerprint','response_json')),
          (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'account_link_tokens'
             AND column_name IN (
               'stripe_checkout_session_id','checkout_started_at'));")
[[ $schema_counts == $'17\t3\t2' ]] || {
    echo "pet schema is incomplete: expected 17 InnoDB tables, 3 recall replay columns, and 2 Checkout-link columns" >&2
    exit 65
}

pending_adoption_columns=$(mariadb \
    --defaults-extra-file="$client_cnf" \
    --batch --skip-column-names \
    --database="$database" \
    --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pending_adoptions'
          AND column_name IN (
            'owner_uuid','intent_id','species','pet_name','state','created_at','expires_at',
            'checkout_started_at','hard_expires_at','account_link_hash',
            'stripe_checkout_session_id','checkout_launch_claimed_at','checkout_completed_at',
            'completed_pet_id','completed_at','notification_pending',
            'notification_acknowledged_at');")
[[ $pending_adoption_columns == 17 ]] || {
    echo "pending-adoption schema is incomplete: expected all 17 V005 columns" >&2
    exit 65
}

orphan_count=$(mariadb \
    --defaults-extra-file="$client_cnf" \
    --batch --skip-column-names \
    --database="$database" \
    --execute="
        SELECT COUNT(*)
        FROM pets p
        LEFT JOIN pet_traits t ON t.pet_id = p.pet_id
        LEFT JOIN pet_mood m ON m.pet_id = p.pet_id
        LEFT JOIN pet_sleep_state s ON s.pet_id = p.pet_id
        WHERE t.pet_id IS NULL OR m.pet_id IS NULL OR s.pet_id IS NULL;")
[[ $orphan_count == 0 ]] || {
    echo "restored database has pets missing trait, mood, or sleep rows" >&2
    exit 65
}
echo "pet schema and aggregate rows are structurally valid"
