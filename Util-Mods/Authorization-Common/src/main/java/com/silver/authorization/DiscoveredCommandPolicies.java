package com.silver.authorization;

/** Central root/family and intentional child overrides for the runtime command catalog. */
public final class DiscoveredCommandPolicies {
    private DiscoveredCommandPolicies() { }

    public static CommandPolicyRegistry create() {
        CommandPolicyRegistry.Builder policies = CommandPolicyRegistry.builder();

        add(policies, CommandClassification.PUBLIC, null, "minecraft", "minecraft",
                "/help", "/list", "/me", "/msg", "/tell", "/w", "/teammsg", "/tm",
                "/random roll", "/random value", "/trigger");
        add(policies, CommandClassification.PERMISSION, "commands.admin.vanilla", "minecraft", "minecraft",
                "/list uuids", "/random reset");

        add(policies, CommandClassification.PUBLIC, null, "chronicle", "chronicle",
                "/history", "/history mine", "/history privacy", "/history privacy public",
                "/history privacy mysterious", "/history privacy anonymous", "/history privacy secret");
        add(policies, CommandClassification.PUBLIC, null, "chronicle", "chronicle", "/history admin");
        add(policies, CommandClassification.PERMISSION, "chronicle.history.staff.view_concealed", "chronicle", "chronicle",
                "/history admin concealed");
        add(policies, CommandClassification.PERMISSION, "chronicle.history.admin.reannounce", "chronicle", "chronicle",
                "/history admin reannounce");
        add(policies, CommandClassification.PERMISSION, "chronicle.history.admin.manage", "chronicle", "chronicle",
                "/history admin exclude", "/history admin include", "/history admin exclusions",
                "/history admin reset", "/history admin remove");

        // Ordinary gameplay/information commands are public. Management and world-mutating
        // vanilla command families are explicitly centralized below instead of inheriting OP.
        add(policies, CommandClassification.PUBLIC, null, "minecraft", "minecraft",
                "/random");
        add(policies, CommandClassification.PERMISSION, "commands.admin.vanilla", "minecraft", "minecraft",
                "/advancement", "/attribute", "/ban", "/ban-ip", "/banlist", "/bossbar",
                "/clear", "/clone", "/damage", "/data", "/datapack", "/debug", "/defaultgamemode",
                "/deop", "/difficulty", "/effect", "/enchant", "/execute", "/experience", "/xp",
                "/fill", "/fillbiome", "/forceload", "/function", "/gamemode", "/gamerule", "/give",
                "/item", "/kick", "/kill", "/loot", "/particle", "/place", "/playsound", "/recipe",
                "/reload", "/ride", "/save-all", "/save-off", "/save-on", "/say", "/schedule",
                "/scoreboard", "/setblock", "/setworldspawn", "/spawnpoint", "/spectate", "/spreadplayers",
                "/seed", "/stop", "/summon", "/tag", "/team", "/teleport", "/tp", "/tellraw", "/test", "/tick",
                "/time", "/title", "/transfer", "/weather", "/whitelist", "/worldborder", "/op",
                "/pardon", "/pardon-ip", "/locate", "/waypoint");

        // OpenPAC owns claim/party semantics. Gameplay roots remain public; known administrative
        // branches are overridden with a central permission and the mod retains ownership checks.
        add(policies, CommandClassification.PUBLIC, null, "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims", "/oparties");
        add(policies, CommandClassification.PERMISSION, "commands.admin.openpac",
                "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims player",
                "/opac");
        add(policies, CommandClassification.PERMISSION, "xaero.pac_admin_mode",
                "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims admin-mode", "/oparties admin-mode", "/oclaims clear for");
        add(policies, CommandClassification.PERMISSION, "xaero.pac_claims_moderator_mode",
                "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims moderator-mode", "/oclaims interrupt for", "/oclaims player interrupt for");
        add(policies, CommandClassification.PERMISSION, "xaero.pac_claims_impersonation",
                "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims impersonate", "/oparties impersonate",
                "/oclaims claim as", "/oclaims unclaim as", "/oclaims forceload as",
                "/oclaims unforceload as", "/oclaims party claim as", "/oclaims party unclaim as",
                "/oclaims party forceload as", "/oclaims party unforceload as",
                "/oclaims player claim as", "/oclaims player unclaim as",
                "/oclaims player forceload as", "/oclaims player unforceload as");
        add(policies, CommandClassification.PERMISSION, "xaero.pac_server_claims",
                "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims server", "/oclaims server-claim-mode");
        add(policies, CommandClassification.PERMISSION, "xaero.pac_claims_teleport",
                "open-parties-and-claims", "openpartiesandclaims", "/oclaims teleport");
        // OpenPAC's separate /opac namespace changes server configuration and config groups;
        // only its harmless help branch is public.
        add(policies, CommandClassification.PUBLIC, null, "open-parties-and-claims", "openpartiesandclaims",
                "/opac help");
        // More-specific player-facing OpenPAC actions override the player-management branch.
        add(policies, CommandClassification.PUBLIC, null, "open-parties-and-claims", "openpartiesandclaims",
                "/oclaims claim", "/oclaims claim in", "/oclaims claim with",
                "/oclaims clear", "/oclaims clear confirm", "/oclaims default-claim-mode",
                "/oclaims forceload", "/oclaims forceload in", "/oclaims interrupt",
                "/oclaims non-ally-mode", "/oclaims player claim", "/oclaims player claim in",
                "/oclaims player claim with", "/oclaims player forceload",
                "/oclaims player forceload in", "/oclaims player interrupt",
                "/oclaims player sub-claim current", "/oclaims player sub-claim use",
                "/oclaims player unclaim", "/oclaims player unclaim in",
                "/oclaims player unforceload", "/oclaims player unforceload in",
                "/oclaims player-claim-mode", "/oclaims sub-claim current", "/oclaims sub-claim use",
                "/oclaims transfer", "/oclaims transfer-accept", "/oclaims unclaim",
                "/oclaims unclaim in", "/oclaims unforceload", "/oclaims unforceload in",
                "/oparties create", "/oparties join");

        // Keep the root public for bare-command usage, then apply the capability for each
        // actual feature root so inherited PUBLIC cannot shadow Pet's permission checks.
        add(policies, CommandClassification.PUBLIC, null, "pet-companion", "pet_companion", "/pet");
        add(policies, CommandClassification.PERMISSION, "aipets.use", "pet-companion", "pet_companion",
                "/pet help", "/pet status", "/pet link", "/pet billing", "/pet portal",
                "/pet place", "/pet pickup", "/pet adopt subscribe");
        add(policies, CommandClassification.PERMISSION, "aipets.adopt", "pet-companion", "pet_companion",
                "/pet adopt");
        add(policies, CommandClassification.PERMISSION, "aipets.recall", "pet-companion", "pet_companion",
                "/pet recall");
        add(policies, CommandClassification.PERMISSION, "aipets.compass", "pet-companion", "pet_companion",
                "/pet compass");
        add(policies, CommandClassification.PERMISSION, "aipets.admin.*", "pet-companion", "pet_companion",
                "/pet admin");
        add(policies, CommandClassification.PERMISSION, "aipets.admin.inspect", "pet-companion", "pet_companion",
                "/pet admin inspect");
        add(policies, CommandClassification.PERMISSION, "aipets.admin.recover", "pet-companion", "pet_companion",
                "/pet admin recover", "/pet admin recall-reset");
        add(policies, CommandClassification.PERMISSION, "aipets.admin.memory", "pet-companion", "pet_companion",
                "/pet admin history");
        add(policies, CommandClassification.PERMISSION, "aipets.admin.reconcile", "pet-companion", "pet_companion",
                "/pet admin reconcile");

        add(policies, CommandClassification.PERMISSION, "mpds.inventory.metadata.modify",
                "mpds", "mpds", "/mpdsremovecustomidself");
        add(policies, CommandClassification.INTERNAL, null, "mpds", "mpds", "/mpdscomplete");
        policies.add(CommandOrigin.FABRIC_BACKEND, "/return", CommandClassification.INTERNAL,
                null, "Minecraft", "minecraft");
        // The same literal is a different command on Velocity: an eligible player uses it
        // to return from the holding lobby. Origin scoping prevents the vanilla INTERNAL
        // rule from leaking into the proxy command tree.
        policies.add(CommandOrigin.VELOCITY_PROXY, "/return", CommandClassification.PUBLIC,
                null, "WakeUpLobby", "wakeuplobby");
        add(policies, CommandClassification.INTERNAL, null, "network-authorization", "network_authorization",
                "/auth-tree-dump");
        add(policies, CommandClassification.PERMISSION, "mpds.skip.manage", "mpds", "mpds", "/updateskip");
        add(policies, CommandClassification.PERMISSION, "mpds.skip.inspect", "mpds", "mpds", "/showskip");
        add(policies, CommandClassification.PERMISSION, "mpds.progression.modify", "mpds", "mpds",
                "/mpdsdefeated", "/mpdsstage1");
        add(policies, CommandClassification.PERMISSION, "mpds.reward.grant", "mpds", "mpds", "/mpdsgrantbossreward");
        add(policies, CommandClassification.PERMISSION, "mpds.inventory.metadata.modify", "mpds", "mpds",
                "/mpdssoulboundmax", "/mpdsremovecustomid");
        add(policies, CommandClassification.PERMISSION, "mpds.inventory.metadata.inspect", "mpds", "mpds",
                "/mpdsgetsoulboundmax");

        add(policies, CommandClassification.PERMISSION, "portal.registry.view", "server-portals", "server_portals",
                "/serverportals list");
        add(policies, CommandClassification.PERMISSION, "portal.registry.manage", "server-portals", "server_portals",
                "/serverportals", "/serverportals register", "/serverportals unregister");
        add(policies, CommandClassification.PERMISSION, "skyislands.inspect", "sky-islands", "skyislands",
                "/skyislands", "/skyislands dragons", "/skyislands dragons all", "/skyislands dragons loaded",
                "/skyislands dragons virtual", "/skyislands giants", "/skyislands giants all",
                "/skyislands giants loaded", "/skyislands giants projectiles", "/skyislands giants virtual");
        add(policies, CommandClassification.PERMISSION, "enderfight.reset", "ender-fight", "enderfight",
                "/enderfight", "/enderfight reset");
        add(policies, CommandClassification.PERMISSION, "villagerinterface.devtest", "villager-interface",
                "villager_interface", "/villagerinterface", "/villagerinterface devtest");
        add(policies, CommandClassification.PERMISSION, "dimensions.manage", "Disable Dimensions",
                "disable_dimensions", "/dimensions", "/disabledimensions");
        add(policies, CommandClassification.PERMISSION, "atlantis.manage", "Atlantis", "atlantis", "/atlantis");

        // Installed operator/build/debug utilities. Root mappings intentionally cover their
        // complete Brigadier descendants; exceptional child permissions can override them later.
        add(policies, CommandClassification.PERMISSION, "commands.admin.worldedit", "worldedit", "worldedit",
                "/worldedit", "/we", "/brush", "/mask", "/gmask", "/limit", "/fast", "/undo", "/redo",
                "/clearhistory", "/schematic", "/schem", "/tool", "/toggleplace", "/unstuck", "/ascend",
                "/descend", "/ceil", "/thru", "/jumpto", "/up", "/down", "//", "///");
        // WorldEdit registers historical aliases as separate Brigadier roots, not descendants
        // of /worldedit. These aliases are present in the installed 7.4.x runtime catalog.
        add(policies, CommandClassification.PERMISSION, "commands.admin.worldedit", "WorldEdit", "worldedit",
                "/.s", "/asc", "/biomeinfo", "/biomelist", "/biomels", "/br", "/butcher",
                "/chunkinfo", "/clearclipboard", "/counter", "/cs", "/cycler", "/delchunks",
                "/deltree", "/desc", "/draw", "/ex", "/ext", "/extinguish", "/farwand",
                "/fixlava", "/fixwater", "/flood", "/floodfill", "/forestgen", "/green", "/j",
                "/listchunks", "/lrbuild", "/luc", "/material", "/navwand", "/none", "/opm",
                "/pickaxe", "/placement", "/pumpkins", "/range", "/rem", "/rement", "/remove",
                "/removeabove", "/removebelow", "/removenear", "/repl", "/replacenear", "/restore",
                "/searchitem", "/selwand", "/size", "/snap", "/snapshot", "/snow", "/sp",
                "/superpickaxe", "/thaw", "/toggleeditwand", "/tracemask", "/track", "/tree");
        add(policies, CommandClassification.PERMISSION, "commands.admin.spark", "spark", "spark", "/spark");
        add(policies, CommandClassification.PERMISSION, "commands.admin.servercore", "ServerCore", "servercore",
                "/servercore", "/server-core");
        add(policies, CommandClassification.PERMISSION, "commands.admin.viaversion", "ViaVersion", "viaversion",
                "/viaver", "/viaversion", "/vvfabric", "/vi");
        add(policies, CommandClassification.PERMISSION, "commands.admin.carpet", "Carpet", "carpet",
                "/carpet", "/script", "/distance", "/log", "/mobcaps", "/perimeterinfo", "/profile", "/info");
        add(policies, CommandClassification.PERMISSION, "commands.admin.carpet", "Carpet", "carpet", "/spawn");
        add(policies, CommandClassification.PERMISSION, "commands.admin.collective", "Collective", "collective",
                "/collective");
        add(policies, CommandClassification.PERMISSION, "commands.admin.polymer", "Polymer", "polymer",
                "/polymer");
        add(policies, CommandClassification.PERMISSION, "commands.admin.utilities", "server utilities", "utilities",
                "/sparkc", "/servercoreconfig", "/fabric", "/mods", "/perf", "/debugstick",
                "/chunky", "/c2me", "/fetchprofile", "/jfr", "/stopwatch", "/swing",
                "!", ",", ".", ";");
        add(policies, CommandClassification.PERMISSION, "commands.admin.servercore", "ServerCore", "servercore",
                "/sc");
        add(policies, CommandClassification.PERMISSION, "commands.admin.carpet", "Carpet", "carpet", "/player");
        add(policies, CommandClassification.PERMISSION, "commands.admin.carpet", "Carpet", "carpet",
                "/entityviewdistance");
        add(policies, CommandClassification.PERMISSION, "commands.admin.utilities",
                "server diagnostics", "fabric", "/version");
        add(policies, CommandClassification.PERMISSION, "commands.admin.vanilla", "minecraft", "minecraft",
                "/dialog", "/rotate", "/setidletimeout", "/statistics", "/stopsound");

        // WorldEdit exposes many flat slash-prefixed Brigadier command roots (//set, //wand,
        // //paste, and their single-slash aliases). One root-family mapping covers all of them.
        policies.addRootPrefix("/", CommandClassification.PERMISSION, "commands.admin.worldedit",
                "WorldEdit", "worldedit");

        return policies.build();
    }

    private static void add(CommandPolicyRegistry.Builder builder, CommandClassification classification,
                            String permission, String source, String modId, String... paths) {
        for (String path : paths) builder.add(path, classification, permission, source, modId);
    }
}
