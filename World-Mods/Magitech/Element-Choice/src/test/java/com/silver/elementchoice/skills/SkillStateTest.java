package com.silver.elementchoice.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillStateTest {
    private static final int ALL = 15;

    private static SkillState owning(int points, Realm... realms) {
        EnumSet<Realm> set = EnumSet.noneOf(Realm.class);
        for (Realm realm : realms) {
            set.add(realm);
        }
        return new SkillState(ALL, points, set, Map.of(), 0);
    }

    private static SkillState spend(SkillState state, String... abilities) {
        for (String id : abilities) {
            state = state.levelUp(id);
        }
        return state;
    }

    @Test
    void owningElementsCostsNothingThenEightTwelveSixteen() {
        assertEquals(0, owning(0).realmCost());
        assertEquals(8, owning(20, Realm.FIRE).realmCost());
        assertEquals(12, owning(20, Realm.FIRE, Realm.EARTH).realmCost());
        assertEquals(16, owning(20, Realm.FIRE, Realm.EARTH, Realm.WATER).realmCost());
    }

    @Test
    void anElementNeedsABossUnlockBeforeItCanBeBought() {
        var state = new SkillState(Realm.FIRE.unlockBit(), 50, EnumSet.of(Realm.FIRE), Map.of(), 0);
        assertEquals(SkillState.Reason.NOT_UNLOCKED, state.checkRealm(Realm.WATER));
        assertEquals(SkillState.Reason.OK, state.withUnlock(Realm.WATER).checkRealm(Realm.WATER));
        var broke = new SkillState(Realm.FIRE.unlockBit() | Realm.WATER.unlockBit(), 7, EnumSet.of(Realm.FIRE), Map.of(), 0);
        assertEquals(SkillState.Reason.NOT_ENOUGH_POINTS, broke.checkRealm(Realm.WATER));
    }

    @Test
    void theFirstElementBringsItsStarterKitAndDefenseForFree() {
        var fire = SkillState.empty().withFirstRealm(Realm.FIRE);
        assertEquals(1, fire.level("fireblast"));
        assertEquals(1, fire.level("firejet"));
        assertEquals(1, fire.level("fireshield"));
        assertEquals(0, fire.level("blaze"), "other basics must be bought");
        assertEquals(0, fire.level("airblast"), "another element's starter is not free");
        assertEquals(0, fire.invested(Realm.FIRE), "free levels cost nothing");
    }

    @Test
    void everyElementHasAFreeDefense() {
        for (Realm realm : Realm.values()) {
            var state = SkillState.empty().withFirstRealm(realm);
            assertTrue(AbilityCatalog.forRealm(realm).stream().anyMatch(def -> def.defense() && state.level(def.id()) == 1),
                    realm + " needs a defense available the moment you own it");
        }
    }

    @Test
    void theNthLevelCostsNPointsAndAMaxedAbilityCostsFifteen() {
        var state = owning(100, Realm.WATER);
        assertEquals(1, state.abilityCost("torrent"));
        state = spend(state, "torrent");
        assertEquals(2, state.abilityCost("torrent"));
        state = spend(state, "torrent", "torrent", "torrent", "torrent");
        assertEquals(5, state.level("torrent"));
        assertEquals(100 - 15, state.points());
        assertEquals(SkillState.Reason.MAX_LEVEL, state.checkAbility("torrent"));
        assertEquals(15, state.invested(Realm.WATER));
    }

    @Test
    void freeAbilitiesStartAtLevelOneSoTheirFirstUpgradeCostsTwo() {
        var state = owning(20, Realm.EARTH);
        assertEquals(2, state.abilityCost("earthblast"));
        state = spend(state, "earthblast");
        assertEquals(2, state.level("earthblast"));
        assertEquals(2, state.invested(Realm.EARTH));
        assertEquals(18, state.points());
    }

    @Test
    void advancedFamiliesOpenOnlyAfterEnoughPointsAreInvestedInTheirElement() {
        var water = owning(500, Realm.WATER);
        assertEquals(SkillState.Reason.GROUP_LOCKED, water.checkAbility("icespike"));
        // 10 points: torrent 1..4 = 10
        water = spend(water, "torrent", "torrent", "torrent", "torrent");
        assertEquals(10, water.invested(Realm.WATER));
        assertEquals(SkillState.Reason.OK, water.checkAbility("icespike"));
        assertEquals(SkillState.Reason.OK, water.checkAbility("healingwaters"));
        assertEquals(SkillState.Reason.GROUP_LOCKED, water.checkAbility("bloodbending"), "blood needs 15");
    }

    @Test
    void lightningIsACombinedAirAndFireFamilyShownOnBothPages() {
        assertTrue(AbilityCatalog.shownOn(Realm.AIR).stream().anyMatch(def -> def.id().equals("lightning")));
        assertTrue(AbilityCatalog.shownOn(Realm.FIRE).stream().anyMatch(def -> def.id().equals("lightning")));
        assertTrue(AbilityCatalog.shownOn(Realm.WATER).stream().noneMatch(def -> def.id().equals("lightning")));
        var fireOnly = spend(owning(500, Realm.FIRE), "blaze", "blaze", "blaze", "blaze");
        assertEquals(10, fireOnly.invested(Realm.FIRE));
        assertEquals(SkillState.Reason.GROUP_LOCKED, fireOnly.checkAbility("lightning"), "needs Air as well");
        var both = spend(owning(500, Realm.FIRE, Realm.AIR), "blaze", "blaze", "blaze", "blaze",
                "airspout", "airspout", "airspout", "airspout");
        assertEquals(SkillState.Reason.OK, both.checkAbility("lightning"));
        var airHeavy = spend(owning(500, Realm.FIRE, Realm.AIR), "blaze", "blaze", "blaze",
                "airspout", "airspout", "airspout", "airspout", "airspout");
        assertEquals(SkillState.Reason.GROUP_LOCKED, airHeavy.checkAbility("lightning"), "Fire needs 10 too");
    }

    @Test
    void comboFamiliesNeedBothElementsInvestedAndOwned() {
        var earthOnly = spend(owning(500, Realm.EARTH), "earthshot", "earthshot", "earthshot", "earthshot", "earthshot",
                "earthline", "earthline", "earthline");
        assertTrue(earthOnly.invested(Realm.EARTH) >= 12);
        assertEquals(SkillState.Reason.GROUP_LOCKED, earthOnly.checkAbility("lavadisk"), "needs Fire as well");
        var both = spend(owning(500, Realm.EARTH, Realm.FIRE),
                "earthshot", "earthshot", "earthshot", "earthshot", "earthshot", "earthline", "earthline", "earthline",
                "blaze", "blaze", "blaze", "blaze");
        assertTrue(both.invested(Realm.FIRE) >= 8);
        assertEquals(SkillState.Reason.OK, both.checkAbility("lavadisk"));
    }

    @Test
    void avatarStateNeedsFifteenInEachOfTheFourElements() {
        var state = owning(1000, Realm.AIR, Realm.WATER, Realm.EARTH, Realm.FIRE);
        assertEquals(SkillState.Reason.GROUP_LOCKED, state.checkAbility("avatarstate"));
        for (String id : new String[] {"airspout", "waterspout", "earthshot", "blaze"}) {
            state = spend(state, id, id, id, id, id);
        }
        assertEquals(15, state.invested(Realm.AIR));
        assertEquals(SkillState.Reason.OK, state.checkAbility("avatarstate"));
    }

    @Test
    void anAbilityOfAnElementYouDoNotOwnIsRefused() {
        assertEquals(SkillState.Reason.NOT_OWNED, owning(50, Realm.FIRE).checkAbility("torrent"));
        assertEquals(SkillState.Reason.UNKNOWN, owning(50, Realm.FIRE).checkAbility("nonsense"));
    }

    @Test
    void resilienceCostsTheLevelNumberAndCapsAtTen() {
        var state = owning(100, Realm.FIRE);
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, state.resilienceCost());
            state = state.levelUpResilience();
        }
        assertEquals(100 - 55, state.points());
        assertEquals(SkillState.Reason.MAX_LEVEL, state.checkResilience());
        assertThrows(IllegalStateException.class, state::levelUpResilience);
    }

    @Test
    void permissionPathsFollowAbilityLevelsAndTheLavaAndMetalNodes() {
        var fire = SkillState.empty().withFirstRealm(Realm.FIRE);
        assertTrue(fire.allowsPath("ability.fireblast"));
        assertFalse(fire.allowsPath("ability.blaze"));
        assertTrue(fire.allowsPath("ability.somethingelse"), "unmanaged abilities are left to Bending");
        assertFalse(fire.allowsPath("lava"));
        assertFalse(fire.allowsPath("metal"));
        var earth = owning(500, Realm.EARTH);
        earth = spend(earth, "earthshot", "earthshot", "earthshot", "earthshot");
        earth = spend(earth, "metalcable");
        assertTrue(earth.allowsPath("metal") && earth.allowsPath("ability.metalcable"));
        assertFalse(earth.allowsPath("lava"));
    }

    @Test
    void storageTextRoundTripsEverything() {
        var state = spend(owning(40, Realm.WATER, Realm.FIRE), "torrent", "torrent", "blaze").levelUpResilience();
        String text = state.serializeTree();
        assertEquals("water,fire,a:torrent=2,a:blaze=1,res=1", text);
        var parsed = SkillState.parse(ALL, state.points(), text);
        assertEquals(state.serializeTree(), parsed.serializeTree());
        assertEquals(2, parsed.level("torrent"));
        assertEquals(1, parsed.resilience());
        assertEquals(state.realms(), parsed.realms());
    }

    @Test
    void theOlderFamilyFormatStillLoadsAsLevelOneAbilities() {
        var parsed = SkillState.parse(ALL, 3, "water,fire,ice,lava,bogus");
        assertTrue(parsed.owns(Realm.WATER) && parsed.owns(Realm.FIRE));
        assertEquals(1, parsed.level("icespike"));
        assertEquals(1, parsed.level("lavadisk"));
        assertEquals(0, parsed.level("bloodbending"));
        assertEquals(0, SkillState.parse(0, 0, "").realms().size());
        assertEquals(0, SkillState.parse(0, 0, null).realms().size());
        assertEquals(SkillState.parse(0, 0, "res=99,a:torrent=9").resilience(), SkillState.RESILIENCE_MAX);
        assertEquals(AbilityDef.MAX_LEVEL, SkillState.parse(0, 0, "a:torrent=9").levels().get("torrent"));
    }

    @Test
    void firstPickChoicesAreTheBossUnlocks() {
        var fresh = new SkillState(Realm.WATER.unlockBit() | Realm.EARTH.unlockBit(), 0, EnumSet.noneOf(Realm.class), Map.of(), 0);
        assertEquals(java.util.List.of(Realm.WATER, Realm.EARTH), fresh.firstPickChoices());
        var picked = fresh.withFirstRealm(Realm.EARTH);
        assertTrue(picked.owns(Realm.EARTH));
        assertEquals(picked.realms(), picked.withFirstRealm(Realm.WATER).realms(), "a second first pick is ignored");
        assertTrue(SkillState.empty().withFirstRealm(Realm.AIR).unlocked(Realm.AIR), "a random pick records its own unlock");
    }

    @Test
    void theCatalogIsConsistent() {
        for (AbilityDef def : AbilityCatalog.all()) {
            assertNotNull(def.realm());
            assertEquals(def, AbilityCatalog.byId(def.id()));
        }
        assertEquals(4, AbilityCatalog.all().stream().filter(AbilityDef::defense).count(), "one defense per element");
    }

    @Test
    void levelScalingStaysWithinSaneBounds() {
        assertEquals(1.0, LevelScaling.damage(1));
        assertEquals(1.4, LevelScaling.damage(5), 1e-9);
        assertEquals(0.8, LevelScaling.cooldown(5), 1e-9);
        assertTrue(LevelScaling.cooldown(50) >= 0.6);
        assertEquals(7.5, LevelScaling.resilienceArmor(10), 1e-9);
    }

    @Test
    void aPinnacleIsAnAbilityAtItsHighestLevelOrTheAvatarState() {
        var fire = owning(100, Realm.FIRE);
        assertTrue(fire.pinnacleOf("fireblast").isEmpty(), "a free level-1 ability is no peak");
        assertFalse(fire.hasPinnacle());
        var maxed = spend(fire, "blaze", "blaze", "blaze", "blaze", "blaze");
        assertEquals("fire", maxed.pinnacleOf("blaze").orElseThrow());
        assertTrue(maxed.pinnacleOf("firebreath").isEmpty());
        assertTrue(maxed.hasPinnacle());
        var almost = spend(fire, "blaze", "blaze", "blaze", "blaze");
        assertTrue(almost.pinnacleOf("blaze").isEmpty(), "level 4 is not the peak");
        assertTrue(maxed.pinnacleOf("nosuchability").isEmpty());
    }

    @Test
    void anyBuildCanReachAPinnacleWithoutAllFourElements() {
        var water = owning(100, Realm.WATER);
        var peak = spend(water, "torrent", "torrent", "torrent", "torrent", "torrent");
        assertEquals("water", peak.pinnacleOf("torrent").orElseThrow());
    }

    @Test
    void theAvatarStateIsItsOwnPinnacle() {
        var avatar = new SkillState(ALL, 0, EnumSet.allOf(Realm.class), Map.of("avatarstate", 1), 0);
        assertEquals("avatar", avatar.pinnacleOf("avatarstate").orElseThrow());
        assertTrue(owning(0, Realm.FIRE).pinnacleOf("avatarstate").isEmpty());
    }

    @Test
    void attunementIsCappedByInvestmentSoSpecialistsGoFurthest() {
        var fire = owning(200, Realm.FIRE);
        assertEquals(0, fire.attunementCap(Realm.FIRE), "nothing invested yet");
        assertEquals(SkillState.Reason.CAPPED, fire.checkAttunement(Realm.FIRE));
        // blaze 1..5 costs 15 points: one attunement level
        var invested = spend(fire, "blaze", "blaze", "blaze", "blaze", "blaze");
        assertEquals(1, invested.attunementCap(Realm.FIRE));
        assertEquals(SkillState.Reason.OK, invested.checkAttunement(Realm.FIRE));
        var attuned = invested.attune(Realm.FIRE);
        assertEquals(1, attuned.attunement(Realm.FIRE));
        assertEquals(SkillState.Reason.CAPPED, attuned.checkAttunement(Realm.FIRE), "level 2 needs 20 invested");
        assertEquals(SkillState.Reason.NOT_OWNED, invested.checkAttunement(Realm.WATER));
    }

    @Test
    void attunementCostsRiseAndStopAtFive() {
        var state = new SkillState(ALL, 0, EnumSet.of(Realm.FIRE), Map.of("blaze", 5, "firebreath", 5, "fireburst", 5, "firewall", 5, "flamerush", 5), 0,
            Map.of(Realm.FIRE, 0));
        int total = 0;
        for (int level = 0; level < SkillState.ATTUNE_MAX; level++) {
            assertEquals(SkillState.Reason.OK, state.checkAttunement(Realm.FIRE), "level " + (level + 1));
            total += state.attunementCost(Realm.FIRE);
            state = state.attune(Realm.FIRE);
        }
        assertEquals(20, total, "2+3+4+5+6 crystals for the whole climb");
        assertEquals(SkillState.Reason.MAX_LEVEL, state.checkAttunement(Realm.FIRE));
        assertEquals(0, state.attunementCost(Realm.FIRE));
    }

    @Test
    void attunementSurvivesTheStoredText() {
        var state = new SkillState(ALL, 5, EnumSet.of(Realm.FIRE, Realm.WATER), Map.of("blaze", 5, "torrent", 4), 2, Map.of(Realm.FIRE, 3, Realm.WATER, 1));
        String text = state.serializeTree();
        assertTrue(text.contains("att:fire=3") && text.contains("att:water=1"), text);
        var loaded = SkillState.parse(ALL, 5, text);
        assertEquals(3, loaded.attunement(Realm.FIRE));
        assertEquals(1, loaded.attunement(Realm.WATER));
        assertEquals(0, loaded.attunement(Realm.EARTH));
        assertEquals(2, loaded.resilience());
        assertEquals(5, loaded.level("blaze"));
        // other purchases keep it
        assertEquals(3, loaded.levelUpResilience().attunement(Realm.FIRE));
    }

    @Test
    void attunementScalingStaysModest() {
        assertEquals(1.2, LevelScaling.attuneDamage(5), 1e-9);
        assertEquals(0.85, LevelScaling.attuneCooldown(5), 1e-9);
    }
}
