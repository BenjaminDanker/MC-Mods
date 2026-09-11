#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "usage: usage_cost_report.sh CLIENT_CNF DATABASE [DAYS]" >&2
    exit 64
}

[[ $# -ge 2 && $# -le 3 ]] || usage
client_cnf=$1
database=$2
days=${3:-30}

[[ -f $client_cnf && ! -L $client_cnf ]] || {
    echo "CLIENT_CNF must be a regular non-symlink file" >&2
    exit 66
}
client_mode=$(stat -c '%a' -- "$client_cnf")
[[ $client_mode == 400 || $client_mode == 440 || $client_mode == 600 || $client_mode == 640 ]] || {
    echo "CLIENT_CNF must be mode 0400, 0440, 0600, or 0640" >&2
    exit 77
}
[[ $database =~ ^[A-Za-z0-9_]+$ ]] || {
    echo "DATABASE must contain only letters, numbers, and underscore" >&2
    exit 64
}
[[ $days =~ ^[0-9]+$ && $days -ge 1 && $days -le 365 ]] || {
    echo "DAYS must be an integer between 1 and 365" >&2
    exit 64
}
command -v mariadb >/dev/null || {
    echo "mariadb is required" >&2
    exit 69
}

# Aggregate only bounded usage columns. No owner UUID, pet UUID, prompt, or reply text is
# selected, so this report is safe to copy into an operating-cost decision record.
query=$(cat <<SQL
SELECT metric, value
FROM (
  SELECT 'window_days' AS metric, CAST(${days} AS CHAR) AS value
  UNION ALL
  SELECT 'active_subscribers_now', CAST(COUNT(*) AS CHAR)
    FROM subscriptions
   WHERE ai_access_enabled=TRUE
     AND ((status IN ('ACTIVE','TRIALING')
           AND (cancel_at_period_end=FALSE OR current_period_end IS NULL
                OR current_period_end>UTC_TIMESTAMP(6)))
       OR (status='PAST_DUE' AND grace_ends_at IS NOT NULL
           AND grace_ends_at>UTC_TIMESTAMP(6)))
  UNION ALL
  SELECT 'successful_dialogue_calls', CAST(COUNT(*) AS CHAR)
    FROM ai_usage
   WHERE operation='DIALOGUE' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'successful_consolidation_calls', CAST(COUNT(*) AS CHAR)
    FROM ai_usage
   WHERE operation='CONSOLIDATION' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'successful_embedding_calls', CAST(COUNT(*) AS CHAR)
    FROM ai_usage
   WHERE operation='EMBEDDING' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'dialogue_owners', CAST(COUNT(DISTINCT owner_uuid) AS CHAR)
    FROM ai_usage
   WHERE operation='DIALOGUE' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'average_input_tokens', CAST(COALESCE(ROUND(AVG(input_tokens),2),0) AS CHAR)
    FROM ai_usage
   WHERE operation='DIALOGUE' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'average_output_tokens', CAST(COALESCE(ROUND(AVG(output_tokens),2),0) AS CHAR)
    FROM ai_usage
   WHERE operation='DIALOGUE' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'estimated_dialogue_cost_usd', CAST(COALESCE(ROUND(SUM(estimated_cost),8),0) AS CHAR)
    FROM ai_usage
   WHERE operation='DIALOGUE' AND status='SUCCEEDED'
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'estimated_total_ai_cost_usd', CAST(COALESCE(ROUND(SUM(estimated_cost),8),0) AS CHAR)
    FROM ai_usage
   WHERE status IN ('SUCCEEDED','FAILED','TIMED_OUT','REJECTED')
     AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY
  UNION ALL
  SELECT 'estimated_cost_per_active_subscriber_usd', CAST(COALESCE(ROUND(
      (SELECT SUM(estimated_cost) FROM ai_usage
        WHERE operation='DIALOGUE' AND status='SUCCEEDED'
          AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY)
      / NULLIF((SELECT COUNT(*) FROM subscriptions
        WHERE ai_access_enabled=TRUE
          AND ((status IN ('ACTIVE','TRIALING')
                AND (cancel_at_period_end=FALSE OR current_period_end IS NULL
                     OR current_period_end>UTC_TIMESTAMP(6)))
            OR (status='PAST_DUE' AND grace_ends_at IS NOT NULL
                AND grace_ends_at>UTC_TIMESTAMP(6)))),0),8),0) AS CHAR)
  UNION ALL
  SELECT 'projected_30_day_cost_per_active_subscriber_usd', CAST(COALESCE(ROUND(
      ((SELECT SUM(estimated_cost) FROM ai_usage
        WHERE operation='DIALOGUE' AND status='SUCCEEDED'
          AND created_at >= UTC_TIMESTAMP(6) - INTERVAL ${days} DAY) * 30 / ${days})
      / NULLIF((SELECT COUNT(*) FROM subscriptions
        WHERE ai_access_enabled=TRUE
          AND ((status IN ('ACTIVE','TRIALING')
                AND (cancel_at_period_end=FALSE OR current_period_end IS NULL
                     OR current_period_end>UTC_TIMESTAMP(6)))
            OR (status='PAST_DUE' AND grace_ends_at IS NOT NULL
                AND grace_ends_at>UTC_TIMESTAMP(6)))),0),8),0) AS CHAR)
) report;
SQL
)

mariadb --defaults-extra-file="$client_cnf" --batch --raw --skip-column-names "$database" \
    --execute="$query"
