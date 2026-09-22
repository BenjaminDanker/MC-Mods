package com.silver.authorization.fabric;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.silver.authorization.CommandClassification;
import com.silver.authorization.CommandCatalogEntry;
import com.silver.authorization.CommandPath;
import com.silver.authorization.CommandOrigin;
import com.silver.authorization.CommandPolicyDecision;
import com.silver.authorization.CommandPolicyRegistry;
import com.silver.authorization.DiscoveredCommandPolicies;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/** Runtime bridge from Brigadier to the shared immutable command policy. */
public final class CommandPolicyRuntime {
    private static final CommandPolicyRegistry POLICIES = DiscoveredCommandPolicies.create();
    private static final Map<CommandNode<CommandSourceStack>, CommandPath> NODE_PATHS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<CommandPath, Boolean> CATALOG = new TreeMap<>();
    private static volatile Logger logger;

    private CommandPolicyRuntime() { }

    static void initialize(Logger log) { logger = log; }

    public static void indexDispatcher(CommandNode<CommandSourceStack> root) {
        NODE_PATHS.clear();
        synchronized (CATALOG) { CATALOG.clear(); }
        for (CommandNode<CommandSourceStack> child : root.getChildren()) {
            index(child, List.of());
        }
        log().info("[CommandPolicy] Indexed {} literal command paths; {} have explicit policies",
                catalogPaths().size(), POLICIES.entries().size());
    }

    private static void index(CommandNode<CommandSourceStack> node, List<String> parent) {
        List<String> literals = parent;
        if (node instanceof LiteralCommandNode<?>) {
            literals = new ArrayList<>(parent);
            literals.add(node.getName());
        }
        if (!literals.isEmpty()) {
            CommandPath path;
            try {
                path = CommandPath.ofLiterals(literals);
            } catch (IllegalArgumentException malformed) {
                log().warn("[CommandPolicy] Ignoring unsupported literal path {}: {}", literals, malformed.getMessage());
                return;
            }
            NODE_PATHS.putIfAbsent(node, path);
            synchronized (CATALOG) {
                CATALOG.merge(path, node.getCommand() != null, Boolean::logicalOr);
            }
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) index(child, literals);
    }

    static Optional<CommandPath> path(CommandNode<?> node) {
        @SuppressWarnings("unchecked")
        CommandNode<CommandSourceStack> typed = (CommandNode<CommandSourceStack>) node;
        return Optional.ofNullable(NODE_PATHS.get(typed));
    }

    static Collection<CommandPath> catalogPaths() {
        synchronized (CATALOG) { return List.copyOf(CATALOG.keySet()); }
    }

    static List<CommandCatalogEntry> catalogEntries() {
        synchronized (CATALOG) {
            return CATALOG.entrySet().stream().map(entry -> {
                String[] source = sourceFor(entry.getKey());
                return new CommandCatalogEntry(entry.getKey(), entry.getValue(),
                        java.util.Optional.ofNullable(source[0]), java.util.Optional.ofNullable(source[1]));
            }).toList();
        }
    }

    private static String[] sourceFor(CommandPath path) {
        var explicit = POLICIES.entry(CommandOrigin.FABRIC_BACKEND, path).orElse(null);
        if (explicit != null) return new String[]{explicit.source().orElse(null), explicit.modId().orElse(null)};
        String root = path.root();
        if (root.startsWith("/")) return new String[]{"worldedit", "worldedit"};
        return switch (root) {
            case ".s", "asc", "biomeinfo", "biomelist", "biomels", "br", "butcher", "chunkinfo",
                    "clearclipboard", "counter", "cs", "cycler", "delchunks", "deltree", "desc", "draw",
                    "ex", "ext", "extinguish", "farwand", "fixlava", "fixwater", "flood", "floodfill",
                    "forestgen", "green", "j", "listchunks", "lrbuild", "luc", "material", "navwand",
                    "none", "opm", "pickaxe", "placement", "pumpkins", "range", "rem", "rement", "remove",
                    "removeabove", "removebelow", "removenear", "repl", "replacenear", "restore", "searchitem",
                    "selwand", "size", "snap", "snapshot", "snow", "sp", "superpickaxe", "thaw",
                    "toggleeditwand", "tracemask", "track", "tree" -> new String[]{"WorldEdit", "worldedit"};
            case "pet" -> new String[]{"Pet Companion", "pet_companion"};
            case "mpdscomplete", "mpdsremovecustomidself", "updateskip", "showskip", "mpdsdefeated",
                    "mpdsstage1", "mpdsgrantbossreward", "mpdssoulboundmax", "mpdsremovecustomid",
                    "mpdsgetsoulboundmax" -> new String[]{"MPDS", "mpds"};
            case "oclaims", "opac", "oparties" -> new String[]{"Open Parties and Claims", "openpartiesandclaims"};
            case "script", "distance", "log", "mobcaps", "perimeterinfo", "profile", "info", "entityviewdistance" ->
                    new String[]{"Carpet", "carpet"};
            case "spark" -> new String[]{"spark", "spark"};
            case "polymer" -> new String[]{"Polymer", "polymer"};
            case "collective" -> new String[]{"Collective", "collective"};
            case "servercore" -> new String[]{"ServerCore", "servercore"};
            case "viaver", "viaversion", "vvfabric", "vi" -> new String[]{"ViaVersion", "viaversion"};
            case "serverportals" -> new String[]{"MCServerPortals", "server_portals"};
            case "skyislands" -> new String[]{"Sky Islands", "skyislands"};
            case "dimensions", "disabledimensions" -> new String[]{"Disable Dimensions", "disable_dimensions"};
            case "atlantis" -> new String[]{"Atlantis", "atlantis"};
            case "enderfight" -> new String[]{"Ender Fight", "enderfight"};
            case "villagerinterface" -> new String[]{"Villager Interface", "villager_interface"};
            default -> vanillaRoot(root) ? new String[]{"Minecraft", "minecraft"} : new String[]{null, null};
        };
    }

    private static boolean vanillaRoot(String root) {
        return java.util.Set.of("advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear",
                "clone", "damage", "data", "datapack", "debug", "defaultgamemode", "deop", "dialog",
                "difficulty", "effect", "enchant", "execute", "experience", "xp", "fill", "fillbiome",
                "forceload", "function", "gamemode", "gamerule", "give", "help", "item", "kick", "kill",
                "list", "locate", "loot", "me", "msg", "particle", "place", "playsound", "random", "recipe",
                "reload", "ride", "save-all", "save-off", "save-on", "say", "schedule", "scoreboard", "seed",
                "setblock", "setworldspawn", "spawnpoint", "spectate", "spreadplayers", "stop", "stopsound",
                "summon", "tag", "team", "teammsg", "teleport", "tp", "tell", "tellraw", "test", "tick", "time",
                "title", "trigger", "transfer", "weather", "whitelist", "worldborder", "op", "pardon", "pardon-ip", "w")
                .contains(root);
    }

    static boolean nodeVisible(CommandNode<CommandSourceStack> node, CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return node.getRequirement().test(source);
        Optional<CommandPath> path = path(node);
        if (path.isEmpty()) return node.getRequirement().test(source);
        CommandPolicyDecision decision = decide(source, path.orElseThrow());
        return decision.visible();
    }

    public static Optional<Boolean> visibleOverride(CommandNode<?> node, Object source) {
        if (!(source instanceof CommandSourceStack stack) || stack.getPlayer() == null) return Optional.empty();
        Optional<CommandPath> path = path(node);
        return path.map(value -> decide(stack, value).visible());
    }

    public static boolean beforeExecution(ParseResults<CommandSourceStack> parsed) {
        CommandSourceStack source = parsed.getContext().getSource();
        ServerPlayer player = source.getPlayer();
        List<CommandPath> parsedPaths = paths(parsed);
        if (parsedPaths.isEmpty()) return true;
        CommandPath path = parsedPaths.get(parsedPaths.size() - 1);

        // A redirected command such as `/execute run mpdscomplete` may reach Brigadier with
        // the target command's own node path. Never let a wrapper hide an INTERNAL target.
        if (player != null) {
            for (CommandPath candidate : parsedPaths) {
                CommandPolicyDecision candidateDecision = decide(source, candidate);
                if (candidateDecision.classification() == CommandClassification.INTERNAL) {
                    sendDenial(source, candidateDecision);
                    log().warn("[CommandPolicy] Player {} attempted INTERNAL command path {} on {}",
                            player.getUUID(), candidate, NetworkAuthorizationMod.serverId());
                    return false;
                }
            }
        }

        CommandPolicyDecision decision = decide(source, path);
        if (decision.classification() == CommandClassification.UNMAPPED) {
            log().warn("[CommandPolicy] UNMAPPED execution attempt actor={} path={} server={} ownerOverride={}",
                    player == null ? source.getTextName() : player.getUUID(), path,
                    NetworkAuthorizationMod.serverId(), decision.ownerOverride());
            if (!decision.allowed()) {
                if (player != null) source.sendFailure(Component.literal("This command has no authorization policy."));
                else sendDenial(source, decision);
                return false;
            }
            if (player != null) source.sendSystemMessage(Component.literal(
                    "Warning: this command has no authorization mapping; OWNER override used."));
            return true;
        }

        if (player == null && !AuthorizationChecks.isDedicatedConsole(source)
                && decision.classification() == CommandClassification.INTERNAL
                && !isServerFunctionSource(source)) {
            sendDenial(source, decision);
            return false;
        }
        if (!decision.allowed()) {
            sendDenial(source, decision);
            return false;
        }
        return true;
    }

    private static List<CommandPath> paths(ParseResults<CommandSourceStack> parsed) {
        List<CommandPath> paths = new ArrayList<>();
        for (var parsedNode : parsed.getContext().getNodes()) {
            Optional<CommandPath> path = path(parsedNode.getNode());
            if (path.isPresent() && (paths.isEmpty() || !paths.get(paths.size() - 1).equals(path.orElseThrow()))) {
                paths.add(path.orElseThrow());
            }
        }
        return paths;
    }

    private static boolean isServerFunctionSource(CommandSourceStack source) {
        // Vanilla function execution uses the server command source with function permission
        // level (not OWNER); command blocks and RCON use distinct source names.
        return source.getEntity() == null && "Server".equals(source.getTextName())
                && source.permissions() != net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER;
    }

    private static void sendDenial(CommandSourceStack source, CommandPolicyDecision decision) {
        String message = decision.classification() == CommandClassification.INTERNAL
                ? "This command is internal and cannot be executed by a player."
                : "You do not have permission to use this command."
                        + decision.requiredPermission().map(permission -> " Required: " + permission).orElse("");
        source.sendFailure(Component.literal(message));
    }

    static CommandPolicyDecision decide(CommandSourceStack source, CommandPath path) {
        ServerPlayer player = source.getPlayer();
        boolean isPlayer = player != null;
        boolean console = AuthorizationChecks.isDedicatedConsole(source);
        boolean internalExecution = !isPlayer && isServerFunctionSource(source);
        return POLICIES.decide(CommandOrigin.FABRIC_BACKEND, path, isPlayer,
                isPlayer && AuthorizationChecks.isOwner(player),
                internalExecution, console, permission -> isPlayer && AuthorizationChecks.has(player, permission));
    }

    static List<CommandPolicyDecision> describePlayer(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack();
        return catalogPaths().stream().map(path -> decide(source, path)).toList();
    }

    private static Logger log() {
        Logger current = logger;
        return current == null ? org.slf4j.LoggerFactory.getLogger("network_authorization") : current;
    }

}
