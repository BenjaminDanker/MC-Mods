package com.silver.elementchoice.skills;

import java.util.List;

/**
 * One Bending ability as the skill tree sees it. {@code free} abilities (starters and each element's
 * defense) are level 1 as soon as the element is owned; the rest must be bought.
 * {@code extraPermissions} are Bending permission paths such as "lava" that this ability also needs.
 */
public record AbilityDef(String id, String display, Realm realm, Group group, boolean free, boolean defense,
        List<String> extraPermissions) {
    public static final int MAX_LEVEL = 5;

    /** Points to go from {@code level - 1} to {@code level}: the Nth level costs N. */
    public static int costToReach(int level) {
        return level;
    }

    /** Total points spent to hold {@code level}; the free first level of a free ability costs nothing. */
    public int paidFor(int level) {
        int total = level * (level + 1) / 2;
        return free && level >= 1 ? total - 1 : total;
    }
}
