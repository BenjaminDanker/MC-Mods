package com.silver.chronicle.common;

public enum PrivacyMode {
    PUBLIC("public"), MYSTERIOUS("mysterious"), ANONYMOUS("anonymous"), SECRET("secret");

    private final String id;
    PrivacyMode(String id) { this.id = id; }
    public String id() { return id; }

    public static PrivacyMode parse(String id) {
        if (id == null) return null;
        for (PrivacyMode mode : values()) if (mode.id.equalsIgnoreCase(id)) return mode;
        return null;
    }
}
