package com.silver.spawnprotect.claims;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.silver.authorization.fabric.AuthorizationChecks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /claimlist: staff overview of OpenPAC claims on this server. Claims of one player that touch are shown as one region
 * (whatever their claim type), claims that are apart stay separate, and each region has a teleport button.
 * Every branch needs commands.admin.openpac; the authorization mod classifies "/claimlist" the same way.
 */
public final class ClaimListCommand {
    private static final String PERMISSION = "commands.admin.openpac";
    private static final int PLAYERS_PER_PAGE = 8;
    private static final int REGIONS_PER_PAGE = 8;

    private record DimensionRegion(Identifier dimension, ClaimRegions.Region region) {
    }

    private record Owner(UUID id, String name, List<DimensionRegion> regions) {
        int chunks() {
            return regions.stream().mapToInt(r -> r.region().chunks()).sum();
        }
    }

    private ClaimListCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var player = Commands.argument("owner", StringArgumentType.word())
            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                collect(context.getSource().getServer()).stream().map(Owner::name), builder))
            .executes(context -> showOwner(context, 1))
            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(context -> showOwner(context, IntegerArgumentType.getInteger(context, "page"))));
        dispatcher.register(Commands.literal("claimlist")
            .requires(source -> AuthorizationChecks.has(source, PERMISSION))
            .executes(context -> showOwners(context, 1))
            .then(Commands.literal("page").then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(context -> showOwners(context, IntegerArgumentType.getInteger(context, "page")))))
            .then(Commands.literal("player").then(player))
            .then(Commands.literal("tp")
                .then(Commands.argument("dimension", DimensionArgument.dimension())
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(ClaimListCommand::teleport))))));
    }

    // ---- reading OpenPAC ----------------------------------------------------------------------------

    private static List<Owner> collect(MinecraftServer server) {
        List<Owner> owners = new ArrayList<>();
        OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerInfoStream().forEach(info -> {
            Map<Identifier, Set<Long>> byDimension = new LinkedHashMap<>();
            info.getStream().forEach(entry -> {
                Set<Long> chunks = byDimension.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>());
                entry.getValue().getStream().forEach(list -> list.getStream().forEach(pos -> chunks.add(ClaimRegions.key(pos.x(), pos.z()))));
            });
            List<DimensionRegion> regions = new ArrayList<>();
            byDimension.forEach((dimension, chunks) -> ClaimRegions.connected(chunks).forEach(region -> regions.add(new DimensionRegion(dimension, region))));
            if (regions.isEmpty()) {
                return;
            }
            regions.sort(Comparator.comparingInt((DimensionRegion r) -> r.region().chunks()).reversed());
            String name = info.getPlayerUsername();
            if (name == null || name.isBlank()) {
                name = info.getClaimsName() == null || info.getClaimsName().isBlank() ? "Server" : info.getClaimsName();
            }
            // Names become command arguments, so keep only word characters (the server owner is called "Server" with quotes).
            owners.add(new Owner(info.getPlayerId(), name.replaceAll("[^A-Za-z0-9_.-]+", "_").replaceAll("^_+|_+$", ""), regions));
        });
        owners.sort(Comparator.comparingInt(Owner::chunks).reversed().thenComparing(Owner::name));
        return owners;
    }

    // ---- output -------------------------------------------------------------------------------------

    private static int showOwners(CommandContext<CommandSourceStack> context, int page) {
        List<Owner> owners = collect(context.getSource().getServer());
        int regions = owners.stream().mapToInt(o -> o.regions().size()).sum();
        int chunks = owners.stream().mapToInt(Owner::chunks).sum();
        if (owners.isEmpty()) {
            context.getSource().sendSystemMessage(Component.literal("No claims on this server.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        int pages = Math.max(1, (owners.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
        page = Math.min(page, pages);
        context.getSource().sendSystemMessage(Component.literal("Claims: " + owners.size() + " owners, " + regions + " regions, "
            + chunks + " chunks").withStyle(ChatFormatting.GOLD));
        for (int i = (page - 1) * PLAYERS_PER_PAGE; i < Math.min(owners.size(), page * PLAYERS_PER_PAGE); i++) {
            Owner owner = owners.get(i);
            context.getSource().sendSystemMessage(Component.literal(" " + owner.name()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  " + owner.regions().size() + (owner.regions().size() == 1 ? " region, " : " regions, ")
                    + owner.chunks() + " chunks ").withStyle(ChatFormatting.GRAY))
                .append(button("[View]", "/claimlist player " + owner.name(), "Show " + owner.name() + "'s claims")));
        }
        pager(context.getSource(), page, pages, "/claimlist page ");
        return owners.size();
    }

    private static int showOwner(CommandContext<CommandSourceStack> context, int page) {
        String wanted = StringArgumentType.getString(context, "owner");
        Owner owner = collect(context.getSource().getServer()).stream()
            .filter(o -> o.name().equalsIgnoreCase(wanted)).findFirst().orElse(null);
        if (owner == null) {
            context.getSource().sendFailure(Component.literal(wanted + " has no claims on this server."));
            return 0;
        }
        int pages = Math.max(1, (owner.regions().size() + REGIONS_PER_PAGE - 1) / REGIONS_PER_PAGE);
        page = Math.min(page, pages);
        context.getSource().sendSystemMessage(Component.literal(owner.name() + ": " + owner.regions().size() + " regions, "
            + owner.chunks() + " chunks (touching claims count as one)").withStyle(ChatFormatting.GOLD));
        for (int i = (page - 1) * REGIONS_PER_PAGE; i < Math.min(owner.regions().size(), page * REGIONS_PER_PAGE); i++) {
            DimensionRegion entry = owner.regions().get(i);
            ClaimRegions.Region region = entry.region();
            int x = region.centerX() * 16 + 8;
            int z = region.centerZ() * 16 + 8;
            String bounds = (region.minX() * 16) + "," + (region.minZ() * 16) + " to " + (region.maxX() * 16 + 15) + "," + (region.maxZ() * 16 + 15);
            context.getSource().sendSystemMessage(Component.literal(" #" + (i + 1) + " ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(shortName(entry.dimension())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  " + region.chunks() + " chunks  " + bounds + " ").withStyle(ChatFormatting.GRAY))
                .append(button("[Teleport]", "/claimlist tp " + entry.dimension() + " " + x + " " + z,
                    "Teleport to " + x + ", " + z + " in " + shortName(entry.dimension()))));
        }
        pager(context.getSource(), page, pages, "/claimlist player " + owner.name() + " ");
        return owner.regions().size();
    }

    private static String shortName(Identifier dimension) {
        return dimension.getNamespace().equals("minecraft") ? dimension.getPath() : dimension.toString();
    }

    private static MutableComponent button(String label, String command, String hover) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withUnderlined(true)
            .withClickEvent(new ClickEvent.RunCommand(command))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    private static void pager(CommandSourceStack source, int page, int pages, String prefix) {
        if (pages <= 1) {
            return;
        }
        MutableComponent line = Component.literal(" ");
        if (page > 1) {
            line.append(button("[< Prev]", prefix + (page - 1), "Page " + (page - 1))).append(Component.literal(" "));
        }
        line.append(Component.literal("page " + page + "/" + pages).withStyle(ChatFormatting.GRAY));
        if (page < pages) {
            line.append(Component.literal(" ")).append(button("[Next >]", prefix + (page + 1), "Page " + (page + 1)));
        }
        source.sendSystemMessage(line);
    }

    // ---- teleport -----------------------------------------------------------------------------------

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        level.getChunk(x >> 4, z >> 4);
        int y = safeY(level, x, z);
        player.fallDistance = 0;
        player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
        context.getSource().sendSystemMessage(Component.literal("Teleported to " + x + ", " + y + ", " + z + " in "
            + shortName(level.dimension().identifier()) + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * The topmost free air at this column. Dimensions with a sky use the heightmap, which already sits just above
     * the highest block that stops movement (a tree top, or the air over a lake). Roofed dimensions such as the
     * Nether have no sky, so they are scanned from under the ceiling down for the first spot with ground and room.
     */
    private static int safeY(ServerLevel level, int x, int z) {
        if (!level.dimensionType().hasCeiling()) {
            return Math.max(level.getMinY() + 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        }
        int top = Math.min(level.getMaxY(), level.getMinY() + level.dimensionType().logicalHeight() - 2);
        for (int y = top; y > level.getMinY() + 1; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (clear(level, feet) && clear(level, feet.above()) && standable(level, feet.below())) {
                return y;
            }
        }
        return Math.max(level.getMinY() + 1, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean standable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
