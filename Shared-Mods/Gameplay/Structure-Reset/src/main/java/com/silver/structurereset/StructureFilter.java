package com.silver.structurereset;

import java.util.List;

/** Which structures are tracked: exact ids such as {@code minecraft:desert_pyramid}, or a whole namespace as {@code mss:*}. */
public record StructureFilter(List<String> patterns) {

    public boolean matches(String id) {
        for (String pattern : patterns) {
            if (pattern.equals(id)) {
                return true;
            }
            if (pattern.endsWith(":*") && id.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}
