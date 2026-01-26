package com.silver.atlantis.find;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;

import java.util.Locale;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Stateful search that advances under a time budget each tick.
 */
final class FlatAreaSearchTask {

    private enum Stage {
        PICK_CANDIDATE,
        EVALUATE,
        DONE
    }

    private final UUID requesterId;
    private final FlatAreaSearchConfig config;
    private final BlockPos avoidCenter;

    private final long startedAtMillis;
    private final Random random;

    private long lastStatusMillis = 0L;
    private int lastStatusAttempt = -1;
    private Stage lastStatusStage = null;

    private Stage stage = Stage.PICK_CANDIDATE;
    private int attempt = 0;

    private final Set<Long> seenCenters = new HashSet<>();

    private BlockPos spawn;
    private BlockPos currentCenter;

    private SurfaceHeightSampler sampler;

    private WindowEvaluationJob currentEval;
    private FlatAreaSearchResult bestNearMiss;
    private FlatAreaSearchResult bestSuccess;

    FlatAreaSearchTask(ServerCommandSource source, FlatAreaSearchConfig config, BlockPos avoidCenter) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = config;
        this.avoidCenter = avoidCenter;
        this.startedAtMillis = System.currentTimeMillis();

        MinecraftServer server = source.getServer();
        long seed = server.getOverworld() != null ? server.getOverworld().getRandom().nextLong() : System.nanoTime();
        this.random = new Random(seed);

        send(source.getServer(), "Flat-area search started (runs over multiple ticks)...");
    }

    boolean tick(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            send(server, "Flat-area search failed: overworld missing");
            return true;
        }

        if (sampler == null) {
            sampler = WorldgenSurfaceHeightSampler.forWorld(world);
        }

        if (spawn == null) {
            WorldProperties.SpawnPoint spawnPoint = server.getSpawnPoint();
            if (spawnPoint == null) {
                spawnPoint = world.getLevelProperties().getSpawnPoint();
            }
            spawn = spawnPoint != null ? spawnPoint.getPos() : BlockPos.ORIGIN;
        }

        long deadline = System.nanoTime() + config.tickTimeBudgetNanos();

        while (System.nanoTime() < deadline) {
            if (stage == Stage.DONE) {
                return true;
            }

            int maxAttempts = config.maxAttempts();
            if (maxAttempts > 0 && attempt >= maxAttempts && stage == Stage.PICK_CANDIDATE) {
                finish(server, bestNearMiss, true);
                return true;
            }

            switch (stage) {
                case PICK_CANDIDATE -> {
                    attempt++;

                    // Avoid re-testing the exact same 510x510 window center.
                    currentCenter = sampleUniqueCenter();
                    currentEval = WindowEvaluationJob.create(currentCenter, config.windowSizeBlocks(), config.stepBlocks(), attempt);
                    stage = Stage.EVALUATE;

                    // Immediate status on new candidate (distance + coords).
                    sendCandidateStatus(server, currentCenter, stage, config.stepBlocks());
                }
                case EVALUATE -> {
                    if (currentEval == null) {
                        stage = Stage.PICK_CANDIDATE;
                        break;
                    }

                    maybeSendProgress(server);

                    int currentThreshold = config.thresholdForAttempt(attempt);
                    if (!currentEval.advance(sampler, deadline, currentThreshold)) {
                        return false; // continue next tick
                    }

                    FlatAreaSearchResult result = currentEval.finishResult();
                    currentEval = null;

                    if (result == null) {
                        stage = Stage.PICK_CANDIDATE;
                        break;
                    }

                    if (bestNearMiss == null || result.avgAbsDeviation() < bestNearMiss.avgAbsDeviation()) {
                        bestNearMiss = result;
                    }

                    if (result.avgAbsDeviation() > currentThreshold) {
                        stage = Stage.PICK_CANDIDATE;
                        break;
                    }

                    // Single-step search: accept immediately once a candidate passes.
                    finish(server, result, false);
                    bestSuccess = result;
                    stage = Stage.DONE;
                    return true;
                }
                case DONE -> {
                    return true;
                }
            }
        }

        return false;
    }

    BlockPos getResultCenterOrNull() {
        if (bestSuccess != null) {
            return bestSuccess.center();
        }
        if (bestNearMiss != null) {
            return bestNearMiss.center();
        }
        return null;
    }

    private BlockPos sampleUniqueCenter() {
        // Try a few times to avoid duplicates; if all collide, accept.
        for (int i = 0; i < 25; i++) {
            BlockPos candidate = DistanceBiasedCandidateSampler.sampleCenter(spawn, random, config, attempt);
            if (avoidCenter != null && candidate.getX() == avoidCenter.getX() && candidate.getZ() == avoidCenter.getZ()) {
                continue;
            }
            long key = packXZ(candidate.getX(), candidate.getZ());
            if (seenCenters.add(key)) {
                return candidate;
            }
        }

        BlockPos fallback = DistanceBiasedCandidateSampler.sampleCenter(spawn, random, config, attempt);
        seenCenters.add(packXZ(fallback.getX(), fallback.getZ()));
        return fallback;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private void maybeSendProgress(MinecraftServer server) {
        if (currentEval == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // Send immediately on stage/attempt change, otherwise every ~1500ms.
        boolean stageChanged = lastStatusStage != stage;
        boolean attemptChanged = lastStatusAttempt != attempt;
        boolean timeElapsed = (now - lastStatusMillis) >= 1500L;

        if (!stageChanged && !attemptChanged && !timeElapsed) {
            return;
        }

        lastStatusMillis = now;
        lastStatusAttempt = attempt;
        lastStatusStage = stage;

        int done = currentEval.completedSamplesThisPass();
        int total = currentEval.totalSamples();
        String pass = currentEval.passName();
        double pct = currentEval.progressPercent();

        if (config.maxAttempts() > 0) {
            send(server, String.format(Locale.ROOT,
                "Attempt %d/%d | %s (step=%d) | %s %d/%d (%.1f%%)",
                attempt,
                config.maxAttempts(),
                stage.name(),
                currentEval.step(),
                pass,
                done,
                total,
                pct
            ));
        } else {
            send(server, String.format(Locale.ROOT,
                "Attempt %d | %s (step=%d) | %s %d/%d (%.1f%%)",
                attempt,
                stage.name(),
                currentEval.step(),
                pass,
                done,
                total,
                pct
            ));
        }
    }

    private void sendCandidateStatus(MinecraftServer server, BlockPos center, Stage stage, int step) {
        long dx = (long) center.getX() - spawn.getX();
        long dz = (long) center.getZ() - spawn.getZ();
        long dist2 = dx * dx + dz * dz;
        int dist = (int) Math.round(Math.sqrt(dist2));

        int currentThreshold = config.thresholdForAttempt(attempt);
        if (config.maxAttempts() > 0) {
            send(server, String.format(Locale.ROOT,
                "Attempt %d/%d candidate: x=%d z=%d (distance≈%d from spawn) | stage=%s step=%d | threshold=%d",
                attempt,
                config.maxAttempts(),
                center.getX(),
                center.getZ(),
                dist,
                stage.name(),
                step,
                currentThreshold
            ));
        } else {
            send(server, String.format(Locale.ROOT,
                "Attempt %d candidate: x=%d z=%d (distance≈%d from spawn) | stage=%s step=%d | threshold=%d",
                attempt,
                center.getX(),
                center.getZ(),
                dist,
                stage.name(),
                step,
                currentThreshold
            ));
        }
    }

    private void finish(MinecraftServer server, FlatAreaSearchResult result, boolean isNearMiss) {
        long elapsed = System.currentTimeMillis() - startedAtMillis;
        if (result == null) {
            send(server, "Flat-area search: no candidates evaluated");
            return;
        }

        int thresholdUsed = config.thresholdForAttempt(result.attempts());

        String msg = String.format(Locale.ROOT,
            "%s flat area center: x=%d y=%d z=%d | mean=%.2f avgAbsDev=%.2f (<=%d) | min=%d max=%d | samples=%d | attempts=%d | %dms",
            isNearMiss ? "Best near-miss" : "Found",
            result.center().getX(),
            result.center().getY(),
            result.center().getZ(),
            result.meanHeight(),
            result.avgAbsDeviation(),
            thresholdUsed,
            result.minHeight(),
            result.maxHeight(),
            result.sampleCount(),
            result.attempts(),
            elapsed
        );

        send(server, msg);
        AtlantisMod.LOGGER.info(msg);
    }

    private void send(MinecraftServer server, String message) {
        if (requesterId != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requesterId);
            if (player != null) {
                player.sendMessage(Text.literal(message), false);
                return;
            }
        }

        // Fallback to server log only
        AtlantisMod.LOGGER.info(message);
    }
}
