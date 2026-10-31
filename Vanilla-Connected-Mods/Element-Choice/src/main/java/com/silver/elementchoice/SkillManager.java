package com.silver.elementchoice;

import com.silver.elementchoice.skills.AbilityCatalog;
import com.silver.elementchoice.skills.AbilityDef;
import com.silver.elementchoice.skills.Group;
import com.silver.elementchoice.skills.LevelScaling;
import com.silver.elementchoice.skills.Realm;
import com.silver.elementchoice.skills.SkillState;
import com.silver.elementchoice.store.ProgressStore;
import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.ability.element.Element;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifier;
import me.moros.bending.api.config.attribute.ModifierOperation;
import me.moros.bending.api.config.attribute.ModifyPolicy;
import me.moros.bending.api.registry.Registries;
import me.moros.bending.api.user.User;
import net.kyori.adventure.key.Key;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads each player's skill tree, keeps Bending (elements and ability strength) and the player's armor in
 * line with it, and handles purchases. Storage calls run off the server thread; everything that touches
 * players runs on it.
 */
final class SkillManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("element-choice");
    private static final Random RANDOM = new Random();
    private static final String NAMESPACE = "elementchoice";
    private static final Identifier ARMOR_ID = Identifier.fromNamespaceAndPath("element_choice", "resilience_armor");
    private static final Identifier TOUGHNESS_ID = Identifier.fromNamespaceAndPath("element_choice", "resilience_toughness");

    /** Ley Crystals come from Magitech's wells; where that mod is absent (the Magic server) attunement is not offered. */
    private static final Identifier CRYSTAL_ID = Identifier.fromNamespaceAndPath("magitech", "ley_crystal");

    /** Which page to show again after a purchase. */
    enum Page { MAP, AIR, WATER, EARTH, FIRE }

    private final MinecraftServer server;
    private final ProgressStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "element-choice-io");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, SkillState> cache = new ConcurrentHashMap<>();
    /** When set (Magitech), only players of this faction ("magic") get elements; everyone else has none and cannot buy anything. */
    private final String requiredFaction;
    private final java.util.Set<UUID> outsiders = ConcurrentHashMap.newKeySet();

    SkillManager(MinecraftServer server, ProgressStore store, String requiredFaction) {
        this.server = server;
        this.store = store;
        this.requiredFaction = requiredFaction == null ? "" : requiredFaction.trim();
    }

    /** True (and tells the player) when this server requires a faction the player does not belong to. */
    private boolean refuseOutsider(ServerPlayer player) {
        if (!outsiders.contains(player.getUUID())) {
            return false;
        }
        player.sendSystemMessage(Component.literal("Elemental magic belongs to the " + requiredFaction + " faction. Your path has no use for it.")
            .withStyle(ChatFormatting.RED));
        return true;
    }

    /** The current state for permission checks. A player not loaded yet has nothing, so managed abilities stay locked. */
    SkillState cached(UUID id) {
        return cache.get(id);
    }

    /** Storage replies run later on the server thread; one that lands after DISCONNECT must not re-cache the player. */
    private boolean online(UUID id) {
        return server.getPlayerList().getPlayer(id) != null;
    }

    void forget(UUID id) {
        outsiders.remove(id);
        cache.remove(id);
    }

    // ------------------------------------------------------------------ joining

    /** Called once Bending has loaded the player's profile: import, assign a first element, or offer the pick. */
    void onReady(ServerPlayer player) {
        UUID id = player.getUUID();
        String name = player.getName().getString();
        io.execute(() -> {
            try {
                if (!requiredFaction.isEmpty() && !store.inFaction(id, requiredFaction)) {
                    LOGGER.info("{} is not in the {} faction; no elements for them here", name, requiredFaction);
                    server.execute(() -> {
                        if (!online(id)) return;
                        outsiders.add(id);
                        cache.put(id, SkillState.parse(0, 0, ""));
                    });
                    return;
                }
                SkillState state = store.load(id, name);
                state = importExisting(id, name, state);
                if (!state.hasAnyRealm()) {
                    List<Realm> choices = state.firstPickChoices();
                    if (choices.isEmpty()) {
                        state = assignRandom(id, name, state);
                    } else {
                        SkillState pending = state;
                        server.execute(() -> {
                            if (!online(id)) return;
                            cache.put(id, pending);
                            SkillScreens.open(player, pending, "map", this);
                        });
                        return;
                    }
                }
                publish(player, state, null);
            } catch (Exception failure) {
                LOGGER.error("Could not load skills for {}", name, failure);
            }
        });
    }

    /** Players who chose an element before the skill tree existed keep it. */
    private SkillState importExisting(UUID id, String name, SkillState state) throws Exception {
        if (state.hasAnyRealm()) {
            return state;
        }
        User user = Registries.BENDERS.get(id);
        if (user == null || user.elements().isEmpty()) {
            return state;
        }
        EnumSet<Realm> realms = EnumSet.noneOf(Realm.class);
        int unlocks = state.unlocks();
        for (Element element : user.elements()) {
            Realm realm = Realm.fromId(element.name());
            if (realm != null) {
                store.unlock(id, name, realm);
                realms.add(realm);
                unlocks |= realm.unlockBit();
            }
        }
        SkillState imported = new SkillState(unlocks, state.points(), realms, state.levels(), state.resilience());
        if (store.spend(id, name, 0, state.serializeTree(), imported.serializeTree())) {
            LOGGER.info("Imported {}'s existing elements {}", name, realms);
            return imported;
        }
        return store.load(id, name);
    }

    private SkillState assignRandom(UUID id, String name, SkillState state) throws Exception {
        Realm[] realms = Realm.values();
        Realm pick = realms[RANDOM.nextInt(realms.length)];
        store.unlock(id, name, pick);
        SkillState next = state.withFirstRealm(pick);
        if (store.spend(id, name, 0, state.serializeTree(), next.serializeTree())) {
            LOGGER.info("{} had no element unlocks; the currents chose {}", name, pick);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                server.execute(() -> player.sendSystemMessage(Component.literal(
                    "You never earned a choice, so the currents chose for you: " + pick.display() + ".")
                    .withStyle(ChatFormatting.AQUA)));
            }
            return next;
        }
        return store.load(id, name);
    }

    /** Pays points for a completed advancement. Stored off-thread; the player is told once it is saved. */
    void awardPoints(ServerPlayer player, int points, String reason) {
        if (outsiders.contains(player.getUUID())) {
            return;
        }

        UUID id = player.getUUID();
        String name = player.getName().getString();
        io.execute(() -> {
            try {
                store.addPoints(id, name, points);
                LOGGER.info("{} earned {} skill point(s) for {}", name, points, reason);
                server.execute(() -> player.sendSystemMessage(Component.literal(
                    "+" + points + " skill point" + (points == 1 ? "" : "s") + " (" + reason + ")")
                    .withStyle(ChatFormatting.GOLD).append(BendingCommandTweaks.openTree())));
            } catch (Exception failure) {
                LOGGER.error("ALERT: could not pay {} point(s) to {} for {}", points, name, reason, failure);
            }
        });
    }

    // ------------------------------------------------------------------ screens and purchases

    void open(ServerPlayer player, Page page) {
        if (refuseOutsider(player)) {
            return;
        }

        UUID id = player.getUUID();
        String name = player.getName().getString();
        io.execute(() -> {
            try {
                SkillState state = store.load(id, name);
                server.execute(() -> {
                    if (!online(id)) return;
                    cache.put(id, state);
                    showPage(player, state, page);
                });
            } catch (Exception failure) {
                LOGGER.error("Could not open skills for {}", name, failure);
            }
        });
    }

    /** The Ley Crystal item, or null where it does not exist. */
    static Item crystal() {
        return BuiltInRegistries.ITEM.containsKey(CRYSTAL_ID) ? BuiltInRegistries.ITEM.getValue(CRYSTAL_ID) : null;
    }

    private static int crystalsHeld(ServerPlayer player, Item crystal) {
        int total = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(crystal)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes {@code amount} crystals from the player's inventory, all or nothing. */
    private static boolean takeCrystals(ServerPlayer player, Item crystal, int amount) {
        if (crystalsHeld(player, crystal) < amount) {
            return false;
        }
        var inventory = player.getInventory();
        int left = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(crystal)) {
                int used = Math.min(left, stack.getCount());
                stack.shrink(used);
                left -= used;
            }
        }
        return true;
    }

    private void showPage(ServerPlayer player, SkillState state, Page page) {
        SkillScreens.open(player, state, page == Page.MAP ? "map" : page.name().toLowerCase(Locale.ROOT), this);
    }

    void buyRealm(ServerPlayer player, Realm realm) {
        if (refuseOutsider(player)) {
            return;
        }

        change(player, Page.MAP, state -> {
            SkillState.Reason reason = state.hasAnyRealm() ? state.checkRealm(realm)
                : (state.unlocked(realm) ? SkillState.Reason.OK : SkillState.Reason.NOT_UNLOCKED);
            if (reason != SkillState.Reason.OK) {
                return Outcome.refused(realmRefusal(state, realm, reason));
            }
            if (!state.hasAnyRealm()) {
                SkillState next = state.withFirstRealm(realm);
                return Outcome.of(next, 0);
            }
            return Outcome.of(state.buyRealm(realm), state.realmCost());
        });
    }

    private static String refusalText(SkillState state, AbilityDef def, SkillState.Reason reason) {
        return switch (reason) {
            case MAX_LEVEL -> def.display() + " is at its maximum level.";
            case NOT_ENOUGH_POINTS -> def.display() + " needs " + state.abilityCost(def.id()) + " skill points.";
            case NOT_OWNED -> "You do not own " + def.realm().display() + ".";
            case GROUP_LOCKED -> def.display() + " is locked.";
            default -> "Not available.";
        };
    }

    void levelUp(ServerPlayer player, AbilityDef def) {
        if (refuseOutsider(player)) {
            return;
        }

        change(player, Page.valueOf(def.realm().name()), state -> {
            SkillState.Reason reason = state.checkAbility(def.id());
            if (reason != SkillState.Reason.OK) {
                return Outcome.refused(refusalText(state, def, reason));
            }
            return Outcome.of(state.levelUp(def.id()), state.abilityCost(def.id()));
        });
    }

    /**
     * Raises an element's attunement for Ley Crystals. The crystals are taken first and handed back if the stored tree changed in
     * the meantime, so a crystal is never lost to a refused purchase.
     */
    void attune(ServerPlayer player, Realm realm) {
        if (refuseOutsider(player)) {
            return;
        }
        Item crystal = crystal();
        if (crystal == null) {
            player.sendSystemMessage(Component.literal("Ley Crystals exist only on Magitech.").withStyle(ChatFormatting.RED));
            return;
        }
        UUID id = player.getUUID();
        String name = player.getName().getString();
        io.execute(() -> {
            try {
                SkillState state = store.load(id, name);
                SkillState.Reason reason = state.checkAttunement(realm);
                if (reason != SkillState.Reason.OK) {
                    String message = switch (reason) {
                        case NOT_OWNED -> "You do not own " + realm.display() + ".";
                        case MAX_LEVEL -> realm.display() + " is fully attuned.";
                        case CAPPED -> "Invest " + ((state.attunement(realm) + 1) * SkillState.ATTUNE_PER_INVESTED) + " skill points in "
                            + realm.display() + " to attune it further (you have " + state.invested(realm) + ").";
                        default -> "Not available.";
                    };
                    server.execute(() -> player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED)));
                    return;
                }
                int cost = state.attunementCost(realm);
                SkillState next = state.attune(realm);
                server.execute(() -> {
                    if (!takeCrystals(player, crystal, cost)) {
                        player.sendSystemMessage(Component.literal("You need " + cost + " Ley Crystals for that.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    io.execute(() -> pay(player, crystal, cost, state, next, realm));
                });
            } catch (Exception failure) {
                LOGGER.error("Could not attune {} for {}", realm, name, failure);
            }
        });
    }

    private void pay(ServerPlayer player, Item crystal, int cost, SkillState state, SkillState next, Realm realm) {
        UUID id = player.getUUID();
        String name = player.getName().getString();
        boolean saved = false;
        try {
            saved = store.spend(id, name, 0, state.serializeTree(), next.serializeTree());
        } catch (Exception failure) {
            LOGGER.error("ALERT: could not save {}'s attunement; their {} crystals are returned", name, cost, failure);
        }
        if (!saved) {
            server.execute(() -> {
                player.getInventory().placeItemBackInInventory(new ItemStack(crystal, cost));
                player.sendSystemMessage(Component.literal("Your skills changed while you were choosing. Your crystals are back; try again.")
                    .withStyle(ChatFormatting.RED));
            });
            return;
        }
        LOGGER.info("{} attuned {} to level {} for {} Ley Crystals", name, realm, next.attunement(realm), cost);
        publish(player, next, Page.valueOf(realm.name()));
    }

    void levelUpResilience(ServerPlayer player) {
        if (refuseOutsider(player)) {
            return;
        }

        change(player, Page.MAP, state -> {
            SkillState.Reason reason = state.checkResilience();
            if (reason != SkillState.Reason.OK) {
                return Outcome.refused(reason == SkillState.Reason.MAX_LEVEL ? "Resilience is at its maximum."
                    : "Resilience needs " + state.resilienceCost() + " skill points.");
            }
            return Outcome.of(state.levelUpResilience(), state.resilienceCost());
        });
    }

    private record Outcome(SkillState next, int cost, String refusal) {
        static Outcome of(SkillState next, int cost) {
            return new Outcome(next, cost, null);
        }

        static Outcome refused(String message) {
            return new Outcome(null, 0, message);
        }
    }

    private String realmRefusal(SkillState state, Realm realm, SkillState.Reason reason) {
        return switch (reason) {
            case NOT_UNLOCKED -> realm.display() + " is locked.";
            case NOT_ENOUGH_POINTS -> realm.display() + " costs " + state.realmCost() + " skill points.";
            default -> "You already have " + realm.display() + ".";
        };
    }

    private interface Change {
        Outcome apply(SkillState current);
    }

    /** Loads, applies one purchase atomically against the stored text, then shows the result. */
    private void change(ServerPlayer player, Page reopen, Change change) {
        UUID id = player.getUUID();
        String name = player.getName().getString();
        io.execute(() -> {
            try {
                SkillState state = store.load(id, name);
                Outcome outcome = change.apply(state);
                if (outcome.refusal() != null) {
                    server.execute(() -> player.sendSystemMessage(Component.literal(outcome.refusal()).withStyle(ChatFormatting.RED)));
                    return;
                }
                if (!store.spend(id, name, outcome.cost(), state.serializeTree(), outcome.next().serializeTree())) {
                    server.execute(() -> player.sendSystemMessage(Component.literal(
                        "Your skills changed while you were choosing. Open the tree again.").withStyle(ChatFormatting.RED)));
                    return;
                }
                LOGGER.info("{} spent {} point(s); tree is now {}", name, outcome.cost(), outcome.next().serializeTree());
                publish(player, outcome.next(), reopen);
            } catch (Exception failure) {
                LOGGER.error("Could not update skills for {}", name, failure);
            }
        });
    }

    // ------------------------------------------------------------------ applying a state

    /** Caches the state, applies it to Bending and the player, and optionally reopens a page. */
    private void publish(ServerPlayer player, SkillState state, Page reopen) {
        UUID id = player.getUUID();
        server.execute(() -> {
            if (!online(id)) return;
            cache.put(id, state);
            applyToBending(player, state);
            applyResilience(player, state);
            grantAdvancements(player, state);
            if (reopen != null) {
                showPage(player, state, reopen);
            }
        });
    }

    private void applyToBending(ServerPlayer player, SkillState state) {
        User user = Registries.BENDERS.get(player.getUUID());
        if (user == null) {
            return;
        }
        for (Realm realm : state.realms()) {
            Element element = Element.valueOf(realm.name());
            if (!user.hasElement(element)) {
                applying.add(player.getUUID());
                try {
                    user.addElement(element);
                } finally {
                    applying.remove(player.getUUID());
                }
            }
        }
        // The tree is the only source of elements: take back any that were handed out by other means.
        if (state.hasAnyRealm() && !ElementChoiceMod.isBendingAdmin(player)) {
            for (Element element : new java.util.ArrayList<>(user.elements())) {
                Realm realm = Realm.fromId(element.name());
                if (realm != null && !state.owns(realm)) {
                    user.removeElement(element);
                    LOGGER.info("Removed {} from {}: not in their skill tree", element.name(), player.getName().getString());
                }
            }
        }
        // Replace only our own modifiers; Bending's own (Ascendant State and so on) stay.
        user.attributeModifiers().remove(modifier -> modifier.policy().key().namespace().equals(NAMESPACE));
        for (AbilityDef def : AbilityCatalog.all()) {
            int level = state.level(def.id());
            if (level <= 1) {
                continue;
            }
            AbilityDescription description = Registries.ABILITIES.get(Key.key("bending", def.id()));
            if (description == null) {
                continue;
            }
            ModifyPolicy policy = new OwnPolicy(def.id(), description);
            add(user, policy, Attribute.DAMAGE, LevelScaling.damage(level));
            add(user, policy, Attribute.COOLDOWN, LevelScaling.cooldown(level));
            add(user, policy, Attribute.RANGE, LevelScaling.range(level));
            add(user, policy, Attribute.RADIUS, LevelScaling.radius(level));
            add(user, policy, Attribute.DURATION, LevelScaling.duration(level));
            add(user, policy, Attribute.SPEED, LevelScaling.speed(level));
        }
        for (Realm realm : state.realms()) {
            int attuned = state.attunement(realm);
            if (attuned > 0) {
                ModifyPolicy policy = new RealmPolicy(realm);
                add(user, policy, Attribute.DAMAGE, LevelScaling.attuneDamage(attuned));
                add(user, policy, Attribute.COOLDOWN, LevelScaling.attuneCooldown(attuned));
            }
        }
    }

    private static void add(User user, ModifyPolicy policy, Attribute attribute, double multiplier) {
        user.attributeModifiers().add(policy, attribute, Modifier.of(ModifierOperation.MULTIPLICATIVE, multiplier));
    }

    /** Matches exactly one ability; the key marks it as ours so it can be replaced later. */
    private record OwnPolicy(String abilityId, AbilityDescription description) implements ModifyPolicy {
        @Override
        public boolean shouldModify(AbilityDescription candidate) {
            return candidate.equals(description);
        }

        @Override
        public Key key() {
            return Key.key(NAMESPACE, abilityId);
        }
    }

    /** Matches every ability of one element; the key marks it as ours so it is replaced with the rest. */
    private record RealmPolicy(Realm realm) implements ModifyPolicy {
        @Override
        public boolean shouldModify(AbilityDescription candidate) {
            return candidate.elements().contains(Element.valueOf(realm.name()));
        }

        @Override
        public Key key() {
            return Key.key(NAMESPACE, "attune_" + realm.id());
        }
    }

    private void applyResilience(ServerPlayer player, SkillState state) {
        set(player.getAttribute(Attributes.ARMOR), ARMOR_ID, LevelScaling.resilienceArmor(state.resilience()));
        set(player.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_ID, LevelScaling.resilienceToughness(state.resilience()));
    }

    private static void set(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (amount > 0) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void grantAdvancements(ServerPlayer player, SkillState state) {
        for (Realm realm : state.realms()) {
            grant(player, "skilltree:" + realm.id());
        }
        EnumSet<Group> groups = EnumSet.noneOf(Group.class);
        for (AbilityDef def : AbilityCatalog.all()) {
            if (def.group() != Group.BASE && state.level(def.id()) >= 1) {
                groups.add(def.group());
            }
        }
        for (Group group : groups) {
            grant(player, "skilltree:" + group.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (state.hasAnyRealm()) {
            grant(player, "magicstory:awakened");
        }
    }

    /** Completes an advancement for this player directly (no console command); does nothing if it is already done. */
    private void grant(ServerPlayer player, String advancementId) {
        net.minecraft.advancements.AdvancementHolder holder =
            server.getAdvancements().get(net.minecraft.resources.Identifier.parse(advancementId));
        if (holder == null) {
            return; // the datapack that defines it is not on this server
        }
        net.minecraft.server.PlayerAdvancements advancements = player.getAdvancements();
        net.minecraft.advancements.AdvancementProgress progress = advancements.getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        List<String> remaining = new java.util.ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining) {
            advancements.award(holder, criterion);
        }
    }

    // ------------------------------------------------------------------ staff tools

    /**
     * Applies a staff edit to a player's stored tree: unlock bits first, then the new tree against whatever is stored.
     * Used by /skills admin, so testers can reach any element, point total or Magitech without playing for it.
     */
    void adminEdit(ServerPlayer target, java.util.function.Consumer<String> reply, List<Realm> unlock,
            java.util.function.UnaryOperator<SkillState> edit, String done) {
        UUID id = target.getUUID();
        String name = target.getName().getString();
        io.execute(() -> {
            try {
                for (Realm realm : unlock) {
                    store.unlock(id, name, realm);
                }
                SkillState state = store.load(id, name);
                SkillState next = edit.apply(state);
                if (!next.serializeTree().equals(state.serializeTree())
                        && !store.spend(id, name, 0, state.serializeTree(), next.serializeTree())) {
                    server.execute(() -> reply.accept("Their skills changed while you were editing; try again."));
                    return;
                }
                SkillState saved = store.load(id, name);
                LOGGER.info("Staff edit for {}: {}; tree is now {}", name, done, saved.serializeTree());
                publish(target, saved, null);
                server.execute(() -> reply.accept(done + " for " + name + "."));
            } catch (Exception failure) {
                LOGGER.error("ALERT: staff edit failed for {}", name, failure);
                server.execute(() -> reply.accept("That failed; see the server log."));
            }
        });
    }

    void adminPoints(ServerPlayer target, int delta, java.util.function.Consumer<String> reply) {
        UUID id = target.getUUID();
        String name = target.getName().getString();
        io.execute(() -> {
            try {
                store.addPoints(id, name, delta);
                SkillState saved = store.load(id, name);
                publish(target, saved, null);
                server.execute(() -> reply.accept((delta >= 0 ? "Gave " : "Took ") + Math.abs(delta) + " skill points; " + name + " now has " + saved.points() + "."));
            } catch (Exception failure) {
                LOGGER.error("ALERT: could not change {}'s skill points", name, failure);
                server.execute(() -> reply.accept("That failed; see the server log."));
            }
        });
    }

    void adminComplete(ServerPlayer target, String branch, java.util.function.Consumer<String> reply) {
        UUID id = target.getUUID();
        String name = target.getName().getString();
        io.execute(() -> {
            try {
                store.markComplete(id, name, branch);
                server.execute(() -> {
                    // The story function leaves this tag; /skills ascend looks for it.
                    target.addTag("progress_" + branch + "_completed");
                    reply.accept(name + " is recorded as having finished the " + branch + " story; /server magitech now opens for them.");
                });
            } catch (Exception failure) {
                LOGGER.error("ALERT: could not record {} completion for {}", branch, name, failure);
                server.execute(() -> reply.accept("That failed; see the server log."));
            }
        });
    }

    private final java.util.Set<UUID> applying = ConcurrentHashMap.newKeySet();

    boolean isApplying(UUID id) {
        return applying.contains(id);
    }

    /** An element was added to a player outside the tree (an admin ran /bending add): the tree takes it as owned. */
    void adoptElement(UUID id, Realm realm) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null || outsiders.contains(id)) {
            return;
        }
        adminEdit(player, text -> player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY)),
            List.of(realm), state -> state.withRealm(realm), realm.display() + " added to the skill tree");
    }

    void shutdown() {
        io.shutdownNow();
    }
}
