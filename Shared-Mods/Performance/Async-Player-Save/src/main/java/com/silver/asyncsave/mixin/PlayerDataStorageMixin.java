package com.silver.asyncsave.mixin;

import com.silver.asyncsave.AsyncSaves;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player .dat files: the data is taken on the server thread exactly as vanilla does; the file is written off it. */
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Unique
    private static final Logger asyncsave$LOGGER = LoggerFactory.getLogger("asyncsave");

    @Shadow
    @Final
    private File playerDir;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void asyncsave$save(Player player, CallbackInfo ci) {
        CompoundTag tag;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), asyncsave$LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, player.registryAccess());
            player.saveWithoutId(output);
            tag = output.buildResult();
        } catch (Exception failure) {
            asyncsave$LOGGER.warn("Failed to save player data for {}", player.getPlainTextName(), failure);
            ci.cancel();
            return;
        }
        Path dir = playerDir.toPath();
        String uuid = player.getStringUUID();
        AsyncSaves.submit(player.getUUID(), "player data", () -> {
            Path temporary = Files.createTempFile(dir, uuid + "-", ".dat");
            NbtIo.writeCompressed(tag, temporary);
            Util.safeReplaceFile(dir.resolve(uuid + ".dat"), temporary, dir.resolve(uuid + ".dat_old"));
        });
        ci.cancel();
    }

    @Inject(method = "load(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;", at = @At("HEAD"))
    private void asyncsave$awaitBeforeLoad(NameAndId who, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        AsyncSaves.awaitPlayer(who.id());
    }
}
