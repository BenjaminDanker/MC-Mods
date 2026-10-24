package com.silver.elementchoice;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.silver.elementchoice.skills.AbilityCatalog;
import com.silver.elementchoice.skills.AbilityDef;
import com.silver.elementchoice.skills.Realm;
import com.silver.elementchoice.skills.SkillState;
import com.silver.elementchoice.store.MemoryProgressStore;
import com.silver.elementchoice.store.MpdsProgressStore;
import com.silver.elementchoice.store.ProgressStore;
import me.moros.bending.api.GameProvider;
import me.moros.bending.api.event.ElementChangeEvent;
import me.moros.bending.api.registry.Registries;
import me.moros.bending.api.user.User;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.permission.v1.PermissionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elemental progression for the Magic server. Bending has four elements; this mod decides which elements,
 * abilities and levels each player has, from a skill tree stored in MPDS. Players open the tree from a
 * dialog, never by typing out commands.
 */
public final class ElementChoiceMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("element-choice");
    /** Bending loads a player's profile asynchronously, so wait before deciding what they own. */
    private static final int ASSESS_AFTER_TICKS = 100;
    /** Bending permission paths of abilities that come from addon jars, which Bending's own defaults do not know. */
    private static final Set<String> ADDON_PATHS = Set.of("ability.skyglide", "ability.tideward", "ability.updraft", "ability.gills", "ability.deepsight", "ability.cinderveil");

    private final Map<UUID, Integer> sinceJoin = new ConcurrentHashMap<>();
    private static volatile ElementChoiceMod instance;
    private volatile SkillManager skills;
    private volatile MinecraftServer serverRef;
    /** True when Open Parties and Claims is installed, so claims can be checked. */
    private final boolean claimsGuarded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("openpartiesandclaims");
    private final Ascension ascension = new Ascension();
    /** Set once the story is finished: the tag the Magic story's final function gives. */
    private static final String MAGIC_COMPLETED_TAG = "mpds_magic_completed";

    /**
     * For other mods: whether this player began the Ascension rite and this cast is a pinnacle of their build. Returns the
     * pinnacle ("fire", "water", "earth", "air" or "avatar") and ends the rite, or empty when the cast does not count.
     */
    public static java.util.Optional<String> pinnacleCast(ServerPlayer player, String abilityId) {
        ElementChoiceMod mod = instance;
        if (mod == null || mod.skills == null) {
            return java.util.Optional.empty();
        }
        long now = player.level().getServer().getTickCount();
        if (!mod.ascension.armed(player.getUUID(), now)) {
            return java.util.Optional.empty();
        }
        SkillState state = mod.skills.cached(player.getUUID());
        java.util.Optional<String> pinnacle = state == null ? java.util.Optional.empty() : state.pinnacleOf(abilityId);
        if (pinnacle.isPresent()) {
            mod.ascension.clear(player.getUUID());
        }
        return pinnacle;
    }

    private int beginAscension(ServerPlayer player) {
        SkillManager manager = skills;
        SkillState state = manager == null ? null : manager.cached(player.getUUID());
        var say = (java.util.function.Consumer<String>) text ->
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
        if (!requiredFaction().isEmpty()) {
            say.accept("You are already past the gate.");
        } else if (state == null) {
            say.accept("Your skills are still loading. Try again in a moment.");
        } else if (!player.entityTags().contains(MAGIC_COMPLETED_TAG)) {
            say.accept("The way to Magitech opens only for those who have finished the story of Magic.");
        } else if (!state.hasPinnacle()) {
            say.accept("You have not reached a peak yet. Take any ability to level 5, or awaken the Ascendant State, then return.");
        } else {
            ascension.arm(player.getUUID(), player.level().getServer().getTickCount());
            say.accept("You gather everything you are. Within 30 seconds, cast your greatest ability: one at level 5, or the Ascendant State.");
        }
        return 1;
    }

    /** A tagged boss dies: the killer and every player within 48 blocks earn the configured points. */
    private void onBossDeath(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
        SkillManager manager = skills;
        if (manager == null || !(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        int points = AdvancementRewards.current().bossPoints(entity.entityTags());
        if (points <= 0) {
            return;
        }
        String name = entity.hasCustomName() ? entity.getCustomName().getString() : entity.getType().getDescription().getString();
        Set<ServerPlayer> winners = new java.util.HashSet<>();
        if (source.getEntity() instanceof ServerPlayer killer) {
            winners.add(killer);
        }
        for (ServerPlayer near : level.players()) {
            if (near.distanceToSqr(entity) <= 48 * 48) {
                winners.add(near);
            }
        }
        winners.forEach(player -> manager.awardPoints(player, points, "Defeated " + name));
    }

    /** Called by the advancement mixin when a player completes an advancement. */
    public static void onAdvancementCompleted(ServerPlayer player, net.minecraft.advancements.AdvancementHolder holder) {
        ElementChoiceMod mod = instance;
        if (mod == null || mod.skills == null) {
            return;
        }
        var display = holder.value().display();
        if (display.isEmpty()) {
            return;
        }
        String id = holder.id().toString();
        int points = AdvancementRewards.current().pointsFor(id, display.get().getType().name(), holder.value().isRoot());
        if (points > 0) {
            mod.skills.awardPoints(player, points, display.get().getTitle().getString());
        }
    }

    @Override
    public void onInitialize() {
        instance = this;
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("polymer-resource-pack")) {
            // Where a Polymer pack is generated (Magitech), the skill screens' art travels in it; elsewhere the Magic pack carries it.
            try {
                Class.forName("eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils")
                    .getMethod("addModAssets", String.class).invoke(null, "element-choice");
            } catch (ReflectiveOperationException failure) {
                LOGGER.error("ALERT: could not add the skill screen art to the Polymer pack", failure);
            }
        }
        if (claimsGuarded) {
            // Bending locks its protection registry once it has started, so this has to happen while mods are still initialising.
            ClaimWatch.register();
        }
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register(this::onBossDeath);
        AdvancementRewards.load();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverRef = server;
            ProgressStore store = chooseStore();
            skills = new SkillManager(server, store, requiredFaction());
            GameProvider.get().eventBus().subscribe(ElementChangeEvent.class, this::guardChange);
            BendingCommandTweaks.install(server);
            if (claimsGuarded) {
                ClaimWatch.attach(server);
            } else {
                LOGGER.warn("Open Parties and Claims is not installed, so bending works in every claim");
            }
            for (var ability : Registries.ABILITIES) {
                String id = ability.key().value();
                if (AbilityCatalog.byId(id) == null && !ability.hidden()) {
                    LOGGER.info("Bending ability '{}' is not in the skill tree catalog; it stays unrestricted", id);
                }
            }
            LOGGER.info("Element Choice active (store={})", store.getClass().getSimpleName());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (skills != null) {
                skills.shutdown();
            }
        });
        // A datapack reload rebuilds the command tree, so the tweaks go back on.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (skills != null) {
                BendingCommandTweaks.install(server);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((connection, sender, joined) ->
            sinceJoin.put(connection.getPlayer().getUUID(), 0));
        ServerPlayConnectionEvents.DISCONNECT.register((connection, left) -> {
            UUID id = connection.getPlayer().getUUID();
            sinceJoin.remove(id);
            if (skills != null) {
                skills.forget(id);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        // Bending asks the permission system before every managed ability. Answer "no" until it is learned.
        PermissionEvents.ON_REQUEST.register(new PermissionEvents.OnRequest() {
            @Override
            public <T> T handlePermissionRequest(PermissionContext context, PermissionNode<T> node) {
                return gateBending(context, node);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var page = Commands.literal("page");
            var realm = Commands.literal("realm");
            for (Realm element : Realm.values()) {
                page.then(Commands.literal(element.id()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null && skills != null) {
                        skills.open(player, SkillManager.Page.valueOf(element.name()));
                    }
                    return 1;
                }));
                realm.then(Commands.literal(element.id()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null && skills != null) {
                        skills.buyRealm(player, element);
                    }
                    return 1;
                }));
            }
            var up = Commands.literal("up");
            for (AbilityDef def : AbilityCatalog.all()) {
                up.then(Commands.literal(def.id()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null && skills != null) {
                        skills.levelUp(player, def);
                    }
                    return 1;
                }));
            }
            var attune = Commands.literal("attune");
            for (Realm element : Realm.values()) {
                attune.then(Commands.literal(element.id()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null && skills != null) {
                        skills.attune(player, element);
                    }
                    return 1;
                }));
            }
            var res = Commands.literal("res").executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                if (player != null && skills != null) {
                    skills.levelUpResilience(player);
                }
                return 1;
            });
            // Staff diagnostic: why can (or can't) this player use an ability?
            var check = Commands.literal("check").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("ability", StringArgumentType.word()).executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    String name = StringArgumentType.getString(context, "ability").toLowerCase(Locale.ROOT);
                    User user = Registries.BENDERS.get(target.getUUID());
                    var description = user == null ? null
                        : Registries.ABILITIES.get(net.kyori.adventure.key.Key.key("bending", name));
                    SkillState state = skills == null ? null : skills.cached(target.getUUID());
                    String report = description == null
                        ? "Unknown ability or player not loaded: " + name
                        : target.getName().getString() + " / " + name + ": elements=" + description.elements()
                            + " needs=" + description.permissions() + " permission=" + user.hasPermission(description)
                            + " canBend=" + user.canBend(description) + " level=" + (state == null ? "?" : state.level(name));
                    LOGGER.info(report);
                    context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(report));
                    return 1;
                })));
            dispatcher.register(Commands.literal("skills").executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                if (player != null && skills != null) {
                    skills.open(player, SkillManager.Page.MAP);
                }
                return 1;
            }).then(adminCommands()).then(page).then(realm).then(up).then(attune).then(res).then(check).then(Commands.literal("ascend").executes(context -> {
                ServerPlayer player = context.getSource().getPlayer();
                return player == null ? 0 : beginAscension(player);
            })));
        });

        // No elytra on Magic: flight comes from Air Mastery instead.
        EntityElytraEvents.ALLOW.register(entity -> false);
    }

    /** Answers "no" for a managed Bending permission until the player has it; null leaves Bending's default. */
    private <T> T gateBending(PermissionContext context, PermissionNode<T> node) {
        var key = node.key();
        SkillManager manager = skills;
        if (!"bending".equals(key.getNamespace()) || manager == null) {
            return null;
        }
        String path = key.getPath();
        if (claimsGuarded && path.startsWith("ability.")
                && context.get(PermissionContext.ENTITY) instanceof ServerPlayer caster && ClaimWatch.forbidden(caster)) {
            return castBoolean(node, false);
        }
        if (!managed(path)) {
            return null;
        }
        var entity = context.get(PermissionContext.ENTITY);
        SkillState state = entity == null ? null : manager.cached(entity.getUUID());
        if (state == null || !state.allowsPath(path)) {
            return castBoolean(node, false);
        }
        // Abilities added by addons are unknown to Bending's own permission defaults, so say yes for them.
        return ADDON_PATHS.contains(path) ? castBoolean(node, true) : null;
    }

    private static boolean managed(String path) {
        if (path.startsWith("ability.")) {
            return AbilityCatalog.byId(path.substring("ability.".length())) != null;
        }
        return !AbilityCatalog.byExtraPermission(path).isEmpty();
    }

    private static <T> T castBoolean(PermissionNode<T> node, boolean value) {
        try {
            return node.cast(Boolean.valueOf(value));
        } catch (ClassCastException notABooleanNode) {
            return null;
        }
    }

    /** config/element-choice.json {"requiredFaction": "magic"} on Magitech; empty or missing everywhere else. */
    private static String requiredFaction() {
        java.nio.file.Path file = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("element-choice.json");
        try {
            if (!java.nio.file.Files.exists(file)) {
                java.nio.file.Files.writeString(file, "{\n  \"requiredFaction\": \"\"\n}\n");
                return "";
            }
            var json = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(file), com.google.gson.JsonObject.class);
            return json.has("requiredFaction") ? json.get("requiredFaction").getAsString() : "";
        } catch (java.io.IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; no faction requirement applies", file, failure);
            return "";
        }
    }

    private static ProgressStore chooseStore() {
        if ("memory".equalsIgnoreCase(System.getProperty("elementchoice.store"))) {
            return new MemoryProgressStore(Integer.getInteger("elementchoice.unlocks", 0),
                Integer.getInteger("elementchoice.points", 0));
        }
        return new MpdsProgressStore();
    }

    private static final java.util.List<String> ELEMENTS = java.util.List.of("air", "water", "earth", "fire", "all");

    private static java.util.List<Realm> realmsNamed(String name) {
        if (name.equals("all")) {
            return java.util.List.of(Realm.values());
        }
        Realm realm = Realm.fromId(name);
        return realm == null ? java.util.List.of() : java.util.List.of(realm);
    }

    /**
     * /skills admin: staff tools for testing the tree. The authorization mod classifies "/skills admin" as a
     * commands.admin.bending command, so players never see or run it; the requires() repeats that locally.
     */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> adminCommands() {
        java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> staff =
            source -> com.silver.authorization.fabric.AuthorizationChecks.has(source, "commands.admin.bending");
        var player = Commands.argument("player", EntityArgument.player());
        var unlock = Commands.literal("unlock").then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("element", StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ELEMENTS, builder))
                .executes(context -> adminElements(context, false))));
        var give = Commands.literal("element").then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("element", StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(ELEMENTS, builder))
                .executes(context -> adminElements(context, true))));
        var points = Commands.literal("points").then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(-100000, 100000))
                .executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    int amount = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount");
                    if (skills != null) {
                        skills.adminPoints(target, amount, text -> context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(text)));
                    }
                    return 1;
                })));
        var max = Commands.literal("max").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            if (skills != null) {
                skills.adminEdit(target, text -> context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(text)),
                    java.util.List.of(Realm.values()), state -> SkillState.maxed(state.unlocks(), state.points()),
                    "Every element, ability, Resilience and attunement maxed");
            }
            return 1;
        }));
        var reset = Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            if (skills != null) {
                skills.adminEdit(target, text -> context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(text)),
                    java.util.List.of(), SkillState::withoutProgress, "Skill tree cleared (unlocks and points kept)");
            }
            return 1;
        }));
        var complete = Commands.literal("complete").then(Commands.argument("player", EntityArgument.player())
            .then(Commands.argument("branch", StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("magic", "tech"), builder))
                .executes(context -> {
                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                    String branch = StringArgumentType.getString(context, "branch").toLowerCase(Locale.ROOT);
                    if (!branch.equals("magic") && !branch.equals("tech")) {
                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Branch must be magic or tech."));
                        return 0;
                    }
                    if (skills != null) {
                        skills.adminComplete(target, branch, text -> context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(text)));
                    }
                    return 1;
                })));
        return Commands.literal("admin").requires(staff).then(points).then(unlock).then(give).then(max).then(reset).then(complete);
    }

    private int adminElements(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context, boolean own)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        java.util.List<Realm> realms = realmsNamed(StringArgumentType.getString(context, "element").toLowerCase(Locale.ROOT));
        if (realms.isEmpty()) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Element must be air, water, earth, fire or all."));
            return 0;
        }
        if (skills != null) {
            skills.adminEdit(target, text -> context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(text)), realms,
                state -> {
                    SkillState next = state;
                    if (own) {
                        for (Realm realm : realms) {
                            next = next.withRealm(realm);
                        }
                    }
                    return next;
                }, own ? "Elements given" : "Elements unlocked");
        }
        return 1;
    }

    /** Staff whom the authorization mod allows to run /bending choose; everyone else earns elements in the skill tree. */
    static boolean isBendingAdmin(ServerPlayer player) {
        return player != null && com.silver.authorization.fabric.AuthorizationChecks.has(player, "commands.admin.bending");
    }

    private void guardChange(ElementChangeEvent event) {
        SkillManager manager = skills;
        if (event.type() == ElementChangeEvent.ElementAction.ADD && manager != null && !manager.isApplying(event.user().uuid())) {
            // Added outside the tree (/bending add by staff): the tree adopts it, so the map shows it as owned.
            Realm realm = Realm.fromId(event.element().name());
            if (realm != null) {
                manager.adoptElement(event.user().uuid(), realm);
            }
        }
        if (event.type() == ElementChangeEvent.ElementAction.CHOOSE) {
            MinecraftServer running = serverRef;
            ServerPlayer player = running == null ? null : running.getPlayerList().getPlayer(event.user().uuid());
            if (isBendingAdmin(player)) {
                return;
            }
            event.cancelled(true);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Elements are earned in your skill tree, not chosen by command.")
                    .withStyle(net.minecraft.ChatFormatting.RED).append(BendingCommandTweaks.openTree()));
            }
        }
    }



    private void tick(MinecraftServer server) {
        SkillManager manager = skills;
        if (manager == null) {
            return;
        }
        if (claimsGuarded && server.getTickCount() % 10 == 0) {
            ClaimWatch.tick(server);
        }
        sinceJoin.replaceAll((id, ticks) -> ticks + 1);
        sinceJoin.entrySet().removeIf(entry -> {
            if (entry.getValue() < ASSESS_AFTER_TICKS) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            User user = player == null ? null : Registries.BENDERS.get(entry.getKey());
            if (player != null && user == null && entry.getValue() < ASSESS_AFTER_TICKS * 4) {
                return false;
            }
            if (player != null && user != null) {
                manager.onReady(player);
            }
            return true;
        });
    }
}
