package com.silver.authorization;

public enum SnapshotApplyResult {
    APPLIED,
    NO_ACTIVE_SESSION,
    WRONG_SESSION,
    WRONG_SERVER,
    WRONG_SUBJECT,
    INVALID_SIGNATURE,
    NOT_YET_VALID,
    EXPIRED,
    INVALID_LEASE,
    STALE_REVISION,
    REVISION_CONFLICT,
    REPLAY
}
