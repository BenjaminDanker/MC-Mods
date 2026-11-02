package com.silver.authorization;

import java.util.Locale;
import java.util.Objects;

/** A permission node or a supported terminal wildcard pattern. */
public record PermissionPattern(String value) implements Comparable<PermissionPattern> {
    public PermissionPattern {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (value.length() > PermissionNode.MAX_LENGTH) {
            throw new IllegalArgumentException("Permission pattern is too long");
        }
        if (value.equals("*")) {
            // The network-wide wildcard is the only wildcard without a prefix.
        } else if (value.endsWith(".*")) {
            PermissionNode.of(value.substring(0, value.length() - 2));
        } else {
            PermissionNode.of(value);
        }
    }

    public static PermissionPattern of(String value) {
        Objects.requireNonNull(value, "value");
        return new PermissionPattern(value.toLowerCase(Locale.ROOT));
    }

    public boolean matches(PermissionNode node) {
        Objects.requireNonNull(node, "node");
        if (value.equals("*")) return true;
        if (!value.endsWith(".*")) return value.equals(node.value());
        String prefix = value.substring(0, value.length() - 2);
        return node.value().startsWith(prefix + ".");
    }

    /** Number of literal dotted components before a possible wildcard. */
    public int specificityDepth() {
        if (value.equals("*")) return 0;
        String prefix = value.endsWith(".*") ? value.substring(0, value.length() - 2) : value;
        int dots = 0;
        for (int i = 0; i < prefix.length(); i++) {
            if (prefix.charAt(i) == '.') dots++;
        }
        return dots + 1;
    }

    public boolean isExact() {
        return !value.equals("*") && !value.endsWith(".*");
    }

    /**
     * Deterministic capability used when a policy explicitly requires an entire namespace.
     * A grant must cover a child in that namespace; a grant to one sibling capability alone
     * does not expose the parent command family.
     */
    public PermissionNode policyGateNode() {
        if (isExact()) return PermissionNode.of(value);
        if (value.equals("*")) return PermissionNode.of("authorization.policy_probe");
        return PermissionNode.of(value.substring(0, value.length() - 2) + ".__policy_probe__");
    }

    @Override
    public int compareTo(PermissionPattern other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
