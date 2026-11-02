package com.silver.magitech;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import me.moros.bending.api.GameProvider;
import me.moros.bending.api.event.AbilityActivationEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ley wells: shrines that give Ley Crystals to benders. Using Bending abilities while standing in a well charges it; once
 * charged it drops crystals and falls quiet for a while. Only Bending can charge it, so only the Magic faction gathers here.
 */
final class Wells {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final Identifier WELL = Identifier.fromNamespaceAndPath("magitech", "ley_well");

    private final MinecraftServer server;
    private final MagitechConfig config;
    private final Path file;
    private final Map<String, WellRules.State> states = new HashMap<>();
    private long lastQuietNoticeMillis;

    Wells(MinecraftServer server, MagitechConfig config) {
        this.server = server;
        this.config = config;
        this.file = server.getWorldPath(LevelResource.ROOT).resolve("magitech").resolve("wells.json");
        load();
        GameProvider.get().eventBus().subscribe(AbilityActivationEvent.class, this::onActivation);
        LOGGER.info("Ley wells active: {} activations to charge, {} minute rest", config.chargeNeeded, config.cooldownMinutes);
    }

    private void onActivation(AbilityActivationEvent event) {
        if (!event.ability().canBind()) {
            return;
        }
        UUID id = event.user().uuid();
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Structure well = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(WELL);
        if (well == null) {
            return;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), well);
        if (start == null || !start.isValid()) {
            return;
        }
        ChunkPos origin = start.getChunkPos();
        String key = level.dimension().identifier() + "|" + origin.x() + "," + origin.z();
        WellRules.State state = states.computeIfAbsent(key, ignored -> new WellRules.State());
        long now = System.currentTimeMillis();
        switch (state.channel(id, now, config.chargeNeeded, config.countGapMillis, config.cooldownMillis(), config.coopWindowSeconds * 1000L)) {
            case COUNTED -> {
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.4, 0.8, 0.4, 0.4);
                player.sendSystemMessage(Component.literal("The well hums (" + state.charge + "/" + config.chargeNeeded + ")")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            case YIELDED -> giveCrystals(level, start, player, state.participants);
            case COOLING -> {
                if (now - lastQuietNoticeMillis > 3000) {
                    lastQuietNoticeMillis = now;
                    long minutes = Math.max(1, (state.readyAtMillis - now) / 60_000L);
                    player.sendSystemMessage(Component.literal("The well is quiet. It stirs again in about " + minutes + " minute(s).")
                        .withStyle(ChatFormatting.GRAY), true);
                }
            }
            case TOO_SOON -> { }
        }
    }

    private void giveCrystals(ServerLevel level, StructureStart start, ServerPlayer player, java.util.List<UUID> group) {
        // The crystals appear at the bender's feet; the shrine's box is not an exact marker of its core.
        BlockPos core = player.blockPosition();
        int count = config.minYield + level.getRandom().nextInt(Math.max(1, config.maxYield - config.minYield + 1));
        Standing standing = Standing.get();
        if (standing != null && "magic".equals(standing.favoured())) {
            count++; // the old city favours Magic this week
        }
        drop(level, player, count);
        if (standing != null) {
            standing.award(player, StandingRules.CRYSTAL * count, "crystal");
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
        level.playSound(null, core, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 2.0f, 0.8f);
        player.sendSystemMessage(Component.literal("The well gives up " + count + " Ley Crystal" + (count == 1 ? "" : "s") + ".")
            .withStyle(ChatFormatting.AQUA));
        LOGGER.info("{} drew {} Ley Crystal(s) from the well at {}", player.getName().getString(), count, core);
        shareWithGroup(level, player, group, count);
        save();
    }

    /** Every other bender who helped charge the well draws extra crystals of their own, up to the well's cap. */
    private void shareWithGroup(ServerLevel level, ServerPlayer lead, java.util.List<UUID> group, int base) {
        int extras = WellRules.extraCrystals(group.size(), config.coopBonusPerBender, base, config.coopYieldCap);
        for (UUID id : group) {
            if (extras <= 0) {
                return;
            }
            ServerPlayer helper = server.getPlayerList().getPlayer(id);
            if (helper == null || helper == lead || helper.level() != level) {
                continue;
            }
            int give = Math.min(extras, config.coopBonusPerBender);
            drop(level, helper, give);
            extras -= give;
            if (Standing.get() != null) {
                Standing.get().award(helper, StandingRules.CRYSTAL * give, "crystal");
            }
            helper.sendSystemMessage(Component.literal("Channelling together draws " + give + " more Ley Crystal" + (give == 1 ? "" : "s") + " to you.")
                .withStyle(ChatFormatting.AQUA));
            LOGGER.info("{} shared in the well's yield with {} (+{})", helper.getName().getString(), lead.getName().getString(), give);
        }
    }

    private static void drop(ServerLevel level, ServerPlayer to, int count) {
        ItemEntity drop = new ItemEntity(level, to.getX(), to.getY() + 0.5, to.getZ(), new ItemStack(MagitechItems.LEY_CRYSTAL, count));
        drop.setDeltaMovement(0, 0.25, 0);
        level.addFreshEntity(drop);
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                JsonObject json = new Gson().fromJson(Files.readString(file), JsonObject.class);
                json.entrySet().forEach(entry -> {
                    WellRules.State state = new WellRules.State();
                    state.readyAtMillis = entry.getValue().getAsLong();
                    states.put(entry.getKey(), state);
                });
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("ALERT: could not read {}; wells start fresh", file, failure);
        }
    }

    void save() {
        try {
            JsonObject json = new JsonObject();
            long now = System.currentTimeMillis();
            states.forEach((key, state) -> {
                if (state.readyAtMillis > now) {
                    json.addProperty(key, state.readyAtMillis);
                }
            });
            Files.createDirectories(file.getParent());
            Files.writeString(file, new Gson().toJson(json));
        } catch (IOException failure) {
            LOGGER.error("ALERT: could not save {}", file, failure);
        }
    }
}
