package com.silver.atlantis.spawn;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.CommandBlockExecutor;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tracking of command blocks and structure blocks containing mob spawn data,
 * and provides the /structuremob command to spawn mobs defined in those blocks.
 */
public final class SpawnCommandManager {

    private final Map<RegistryKey<World>, Set<BlockPos>> trackedBlocks = new ConcurrentHashMap<>();

    public void register() {
        // Track block entity load/unload
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (world instanceof ServerWorld serverWorld && shouldTrack(blockEntity)) {
                track(serverWorld, blockEntity.getPos());
            }
        });

        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (world instanceof ServerWorld serverWorld && shouldTrack(blockEntity)) {
                untrack(serverWorld, blockEntity.getPos());
            }
        });

        // Register command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> 
            registerCommand(dispatcher)
        );
    }

    private void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("structuremob")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> execute(context.getSource(), false))
                .then(CommandManager.literal("dryrun")
                    .executes(context -> execute(context.getSource(), true)))
        );
    }

    private int execute(ServerCommandSource source, boolean dryRun) {
        MinecraftServer server = source.getServer();
        int tracked = 0;
        int eligible = 0;
        int spawned = 0;
        List<String> errors = new ArrayList<>();

        for (var entry : trackedBlocks.entrySet()) {
            ServerWorld world = server.getWorld(entry.getKey());
            if (world == null) continue;

            Iterator<BlockPos> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                BlockEntity blockEntity = world.getBlockEntity(pos);

                // Extract spawn data from block entity
                Optional<SpawnData> dataOpt = extractSpawnData(blockEntity, pos);
                if (dataOpt.isEmpty()) {
                    if (blockEntity == null) {
                        iterator.remove(); // Block no longer exists
                    }
                    continue;
                }

                tracked++;
                SpawnData data = dataOpt.get();

                if (data.error != null) {
                    errors.add(formatError(world, pos, data.error));
                    continue;
                }

                if (data.customization == null) {
                    continue; // Skip (no spawn data)
                }

                eligible++;

                if (dryRun) {
                    spawned++;
                    continue;
                }

                // Actually spawn the mob
                Optional<Entity> entityOpt = MobSpawner.spawn(world, data.customization);
                if (entityOpt.isPresent()) {
                    spawned++;
                } else {
                    errors.add(formatError(world, pos, "Spawn failed"));
                }
            }
        }

        String summary = String.format(Locale.ROOT, 
            "%s %d mob(s) from %d eligible block(s); %d blocks tracked.",
            dryRun ? "Would spawn" : "Spawned",
            spawned, eligible, tracked
        );
        source.sendFeedback(() -> Text.literal(summary), false);

        for (String error : errors) {
            source.sendFeedback(() -> Text.literal(error), false);
        }

        return spawned;
    }

    private Optional<SpawnData> extractSpawnData(BlockEntity blockEntity, BlockPos pos) {
        if (blockEntity instanceof StructureBlockBlockEntity structureBlock) {
            return parseStructureBlock(structureBlock, pos);
        } else if (blockEntity instanceof CommandBlockBlockEntity commandBlock) {
            return parseCommandBlock(commandBlock, pos);
        }
        return Optional.empty();
    }

    private Optional<SpawnData> parseStructureBlock(StructureBlockBlockEntity structureBlock, BlockPos pos) {
        String metadata = structureBlock.getMetadata();
        if (metadata == null || metadata.isBlank()) {
            return Optional.of(new SpawnData(null, null)); // Skip
        }

        try {
            NbtCompound nbt = NbtHelper.fromNbtProviderString(metadata);
            if (!hasSpawnData(nbt)) {
                return Optional.of(new SpawnData(null, null)); // Skip
            }

            Optional<MobCustomization> custom = CommandBlockParser.parse(nbt, pos);
            if (custom.isEmpty()) {
                return Optional.of(new SpawnData(null, "Failed to parse spawn data"));
            }
            return Optional.of(new SpawnData(custom.get(), null));

        } catch (CommandSyntaxException e) {
            AtlantisMod.LOGGER.warn("Invalid NBT in structure block at {}: {}", pos, e.getMessage());
            return Optional.of(new SpawnData(null, "Invalid NBT: " + e.getMessage()));
        }
    }

    private Optional<SpawnData> parseCommandBlock(CommandBlockBlockEntity commandBlock, BlockPos pos) {
        CommandBlockExecutor executor = commandBlock.getCommandExecutor();
        if (executor == null) {
            return Optional.empty();
        }

        String command = executor.getCommand();
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }

        // Extract NBT payload from command
        Optional<String> payloadOpt = extractNbtPayload(command);
        if (payloadOpt.isEmpty()) {
            return Optional.of(new SpawnData(null, null)); // Skip
        }

        try {
            NbtCompound nbt = NbtHelper.fromNbtProviderString(payloadOpt.get());
            if (!hasSpawnData(nbt)) {
                return Optional.of(new SpawnData(null, null)); // Skip
            }

            Optional<MobCustomization> custom = CommandBlockParser.parse(nbt, pos);
            if (custom.isEmpty()) {
                return Optional.of(new SpawnData(null, "Failed to parse spawn data"));
            }
            return Optional.of(new SpawnData(custom.get(), null));

        } catch (CommandSyntaxException e) {
            AtlantisMod.LOGGER.warn("Invalid NBT in command block at {}: {}", pos, e.getMessage());
            return Optional.of(new SpawnData(null, "Invalid NBT: " + e.getMessage()));
        }
    }

    private Optional<String> extractNbtPayload(String command) {
        String trimmed = command.trim();
        
        // Check for Structure_Spawning prefix
        if (trimmed.startsWith("Structure_Spawning")) {
            return Optional.of(normalizeNbt("{" + trimmed + "}"));
        }

        // Check if it looks like spawn data
        if (looksLikeSpawnData(trimmed)) {
            return Optional.of(normalizeNbt(trimmed));
        }

        // Search for embedded NBT compound
        int braceIndex = trimmed.indexOf('{');
        while (braceIndex >= 0) {
            int endBrace = findMatchingBrace(trimmed, braceIndex);
            if (endBrace >= 0) {
                String candidate = trimmed.substring(braceIndex, endBrace + 1);
                if (looksLikeSpawnData(candidate)) {
                    return Optional.of(normalizeNbt(candidate));
                }
            }
            braceIndex = trimmed.indexOf('{', braceIndex + 1);
        }

        return Optional.empty();
    }

    private boolean looksLikeSpawnData(String input) {
        if (!input.startsWith("{")) {
            return false;
        }
        return input.contains("Structure_Spawning") || input.contains("id:");
    }

    private boolean hasSpawnData(NbtCompound nbt) {
        if (nbt.contains("Structure_Spawning")) {
            return true;
        }
        return nbt.contains("id");
    }

    private String normalizeNbt(String input) {
        if (!input.contains("\\")) {
            return input;
        }

        StringBuilder result = new StringBuilder(input.length());
        boolean escaping = false;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            if (escaping) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    default -> result.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }

    private int findMatchingBrace(String input, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;

        for (int i = start; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    private String formatError(ServerWorld world, BlockPos pos, String error) {
        return String.format(Locale.ROOT, "Error at %s (%d, %d, %d): %s",
            world.getRegistryKey().getValue(), pos.getX(), pos.getY(), pos.getZ(), error);
    }

    private void track(ServerWorld world, BlockPos pos) {
        trackedBlocks
            .computeIfAbsent(world.getRegistryKey(), k -> ConcurrentHashMap.newKeySet())
            .add(pos.toImmutable());
    }

    private void untrack(ServerWorld world, BlockPos pos) {
        Set<BlockPos> positions = trackedBlocks.get(world.getRegistryKey());
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                trackedBlocks.remove(world.getRegistryKey());
            }
        }
    }

    private boolean shouldTrack(BlockEntity blockEntity) {
        return blockEntity instanceof StructureBlockBlockEntity 
            || blockEntity instanceof CommandBlockBlockEntity;
    }

    private record SpawnData(MobCustomization customization, String error) {}
}
