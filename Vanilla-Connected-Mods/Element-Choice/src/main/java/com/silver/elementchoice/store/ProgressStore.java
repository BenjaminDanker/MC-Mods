package com.silver.elementchoice.store;

import com.silver.elementchoice.skills.Realm;
import com.silver.elementchoice.skills.SkillState;

import java.util.UUID;

/** Where a player's element unlocks, skill points and purchased nodes live. */
public interface ProgressStore {
    SkillState load(UUID id, String name) throws Exception;

    /**
     * Spends {@code cost} points and replaces the purchased-node text, but only if the stored text still
     * equals {@code expectedTree}. Returns false when someone else changed it or the points are short.
     */
    boolean spend(UUID id, String name, int cost, String expectedTree, String newTree) throws Exception;

    /** Records that a player may choose this element. Returns true when it was not already recorded. */
    boolean unlock(UUID id, String name, Realm element) throws Exception;

    void addPoints(UUID id, String name, int delta) throws Exception;

    /** Records that this player finished a branch story ("magic" or "tech"), which opens Magitech to them. */
    default void markComplete(UUID id, String name, String branch) throws Exception {
    }

    /** Whether this player belongs to the named faction ("magic" or "tech"), i.e. finished that story and not the other. */
    default boolean inFaction(UUID id, String faction) throws Exception {
        return true;
    }
}
