package com.silver.aipets.common.authority;

/** Detailed reconciliation decision suitable for structured duplicate/stale-entity logs. */
public enum EntityAuthorityDecision {
    AUTHORITATIVE(false),
    DISCARD_PET_ID_MISMATCH(true),
    DISCARD_OWNER_MISMATCH(true),
    DISCARD_NOT_PLACED(true),
    DISCARD_BACKEND_MISMATCH(true),
    DISCARD_DIMENSION_MISMATCH(true),
    DISCARD_ENTITY_UUID_MISMATCH(true),
    DISCARD_RECORD_VERSION_AHEAD(true);

    private final boolean discard;

    EntityAuthorityDecision(boolean discard) {
        this.discard = discard;
    }

    public boolean shouldDiscard() {
        return discard;
    }
}
