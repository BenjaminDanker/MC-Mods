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
               'account_link_tokens','idempotency_requests','pending_adoptions',
               'pet_decisions','pet_relationship_evidence','pet_open_threads')),
          (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'pet_recall_usage'
             AND column_name IN (
               'request_fingerprint','compensation_fingerprint','response_json')),
          (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'account_link_tokens'
             AND column_name IN (
               'stripe_checkout_session_id','checkout_started_at'));" )
[[ $schema_counts == $'20\t3\t2' ]] || {
    echo "pet schema is incomplete: expected 20 InnoDB tables, 3 recall replay columns, and 2 Checkout-link columns" >&2
    exit 65
}

relationship_columns=$(mariadb --defaults-extra-file="$client_cnf" --batch --skip-column-names \
    --database="$database" --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='pet_relationship_evidence'
        AND column_name IN ('interaction_id','event_id','pet_id','relationship_type','strength','occurred_at');")
[[ $relationship_columns == 6 ]] || {
    echo "relationship evidence V013 columns are missing" >&2
    exit 65
}

organic_columns=$(mariadb --defaults-extra-file="$client_cnf" --batch --skip-column-names \
    --database="$database" --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND
          ((table_name='pet_traits' AND column_name IN ('self_model_json','self_model_version','self_model_updated_at'))
           OR (table_name='pet_mood' AND column_name IN ('affect_valence','affect_valence_intensity','affect_alertness',
             'affect_alertness_intensity','affect_social_stance','affect_social_intensity','affect_trigger',
             'affect_target_uuid','affect_updated_at','affect_event_id','owner_intent_guess','owner_emotion_guess',
             'owner_interpretation_confidence','owner_interpretation_event_id','owner_interpretation_updated_at'))
           OR (table_name='pet_events' AND column_name IN ('memory_kind','source_provenance','confidence','salience',
             'emotional_significance','participants_json','location_json','memory_candidate_text',
             'memory_candidate_confidence'))
           OR (table_name='long_term_memories' AND column_name IN ('memory_kind','confidence','salience',
             'emotional_significance','supersedes_memory_id')));")
[[ $organic_columns == 32 ]] || {
    echo "organic companion V014 columns are incomplete" >&2
    exit 65
}
thread_columns=$(mariadb --defaults-extra-file="$client_cnf" --batch --skip-column-names \
    --database="$database" --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='pet_open_threads'
          AND column_name IN ('thread_id','pet_id','summary','source_kind','source_event_id','thread_key',
            'location_server','location_dimension','location_cue','state','created_at',
            'last_referenced_at','next_cue','expires_at','version','updated_at');")
[[ $thread_columns == 16 ]] || {
    echo "organic companion V014 open-thread columns are incomplete" >&2
    exit 65
}
sleep_columns=$(mariadb --defaults-extra-file="$client_cnf" --batch --skip-column-names \
    --database="$database" --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='pet_sleep_state'
        AND column_name IN ('fatigue','fatigue_updated_at','awake_since');")
[[ $sleep_columns == 3 ]] || {
    echo "need-based sleep V011 columns are missing" >&2
    exit 65
}

intention_columns=$(mariadb --defaults-extra-file="$client_cnf" --batch --skip-column-names \
    --database="$database" --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='pet_decisions'
        AND column_name IN ('motivation_memory_id','entity_uuid','decision_version');")
[[ $intention_columns == 3 ]] || {
    echo "bounded-intention V012 columns are missing" >&2
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

replacement_state_count=$(mariadb \
    --defaults-extra-file="$client_cnf" \
    --batch --skip-column-names \
    --database="$database" \
    --execute="SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'pending_adoptions'
          AND column_name = 'state'
          AND LOCATE('REPLACEMENT_PENDING', UPPER(column_type)) > 0
          AND LOCATE('WAITING_FOR_ENTITLEMENT', UPPER(column_type)) > 0;")
[[ $replacement_state_count == 1 ]] || {
    echo "pending-adoption schema is incomplete: V008 replacement states are missing" >&2
    exit 65
}

experience_schema=$(mariadb \
    --defaults-extra-file="$client_cnf" \
    --batch --skip-column-names \
    --database="$database" \
    --execute="SELECT
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema=DATABASE() AND table_name='long_term_memories'
           AND column_name IN ('provenance','fact_key')),
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema=DATABASE() AND table_name='ai_usage'
           AND column_name='operation'
           AND LOCATE('PLANNING',UPPER(column_type))>0);")
[[ $experience_schema == $'2\t1' ]] || {
    echo "experience schema is incomplete: apply V009 through V011" >&2
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
