package com.silver.structurereset;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Restores beaten structures a day after they are beaten. See {@link ResetManager}. */
public final class StructureResetMod implements ModInitializer {
    private volatile ResetManager manager;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ResetConfig config = ResetConfig.load(FabricLoader.getInstance().getConfigDir().resolve("structure-reset.json"));
            manager = new ResetManager(server, config);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) {
                manager.save();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) {
                manager.tick();
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (manager != null) {
                manager.onDeath(entity);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("structurereset")
                .then(Commands.literal("status").executes(context -> reply(context.getSource(),
                    manager == null ? "not running" : manager.status())))
                .then(Commands.literal("here").executes(context -> here(context.getSource(), Action.INFO)))
                .then(Commands.literal("reset").executes(context -> here(context.getSource(), Action.RESET)))
                .then(Commands.literal("forget").executes(context -> here(context.getSource(), Action.FORGET)))));
    }

    private enum Action { INFO, RESET, FORGET }

    private int here(CommandSourceStack source, Action action) {
        if (manager == null) {
            return reply(source, "not running");
        }
        var pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        Instance instance = manager.containing(source.getLevel(), pos);
        if (instance == null) {
            instance = manager.discoverAt(source.getLevel(), pos);
        }
        if (instance == null) {
            return reply(source, "No tracked structure here.");
        }
        return switch (action) {
            case INFO -> reply(source, manager.describe(instance));
            case RESET -> reply(source, manager.forceReset(instance) ? "Restoring shortly." : "Busy; try again in a moment.");
            case FORGET -> reply(source, manager.forget(instance) ? "Forgotten." : "Busy; try again in a moment.");
        };
    }

    private static int reply(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
