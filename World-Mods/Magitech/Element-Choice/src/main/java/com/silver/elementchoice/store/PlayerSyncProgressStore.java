package com.silver.elementchoice.store;

import com.silver.elementchoice.skills.Realm;
import com.silver.elementchoice.skills.SkillState;
import com.silver.playersync.PlayerSync;

import java.util.UUID;

/** The real store: the same Player Sync table that already carries boss progress between servers. */
public final class PlayerSyncProgressStore implements ProgressStore {
    @Override
    public SkillState load(UUID id, String name) throws Exception {
        PlayerSync.ElementProgress progress = PlayerSync.elements(name, id.toString());
        return SkillState.parse(progress.unlocks(), progress.skillPoints(), progress.skillTree());
    }

    @Override
    public boolean spend(UUID id, String name, int cost, String expectedTree, String newTree) throws Exception {
        return PlayerSync.spendSkillPoints(name, id.toString(), cost, expectedTree, newTree);
    }

    @Override
    public boolean unlock(UUID id, String name, Realm element) throws Exception {
        return PlayerSync.unlockElement(name, id.toString(), element.unlockBit());
    }

    @Override
    public void addPoints(UUID id, String name, int delta) throws Exception {
        PlayerSync.addSkillPoints(name, id.toString(), delta);
    }

    @Override
    public void markComplete(UUID id, String name, String branch) throws Exception {
        PlayerSync.completeBranch(name, id.toString(), branch);
    }

    @Override
    public boolean inFaction(UUID id, String faction) throws Exception {
        var done = PlayerSync.completions(id.toString());
        return "magic".equals(faction) ? done.magic() && !done.tech() : "tech".equals(faction) && done.tech() && !done.magic();
    }
}
