package com.silver.aipets.fabric.permission;

/** Explicit permission nodes and their vanilla operator-level fallback. */
public enum PetPermission {
    USE("aipets.use", 0),
    ADOPT("aipets.adopt", 0),
    CHAT("aipets.chat", 0),
    COMPASS("aipets.compass", 0),
    RECALL("aipets.recall", 0),
    ADMIN_INSPECT("aipets.admin.inspect", 3),
    ADMIN_RECOVER("aipets.admin.recover", 3),
    ADMIN_SUBSCRIPTION("aipets.admin.subscription", 3),
    ADMIN_MEMORY("aipets.admin.memory", 3),
    ADMIN_RECONCILE("aipets.admin.reconcile", 3);

    private final String node;
    private final int defaultRequiredLevel;

    PetPermission(String node, int defaultRequiredLevel) {
        this.node = node;
        this.defaultRequiredLevel = defaultRequiredLevel;
    }

    public String node() {
        return node;
    }

    public int defaultRequiredLevel() {
        return defaultRequiredLevel;
    }
}
