package com.silver.aipets.common.authority;

/** Expected business/CAS rejections; malformed command values throw at construction instead. */
public enum TransitionFailure {
    OWNER_MISMATCH,
    VERSION_MISMATCH,
    STATE_MISMATCH,
    TIMESTAMP_BEFORE_CURRENT,
    VERSION_EXHAUSTED,
    BACKEND_MISMATCH,
    DIMENSION_MISMATCH,
    ENTITY_UUID_MISMATCH,
    ENTITY_NOT_MATERIALIZED,
    ENTITY_UUID_NOT_FRESH,
    OUT_OF_RANGE,
    TRANSFER_ID_MISMATCH,
    DESTINATION_MISMATCH,
    TRANSFER_EXPIRED,
    TRANSFER_NOT_EXPIRED
}
