package com.silver.asyncsave.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.silver.asyncsave.AsyncSaves;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.FileUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stats files: the JSON is built on the server thread, the file is written off it. */
@Mixin(ServerStatsCounter.class)
public abstract class ServerStatsCounterMixin {
    @Shadow
    @Final
    private static Gson GSON;

    @Shadow
    @Final
    private Path file;

    @Shadow
    protected abstract JsonElement toJson();

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void asyncsave$save(CallbackInfo ci) {
        JsonElement json = toJson();
        Path target = file;
        UUID owner = ownerOf(target);
        AsyncSaves.submit(owner, "stats", () -> {
            FileUtil.createDirectoriesSafe(target.getParent());
            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8); JsonWriter json2 = GSON.newJsonWriter(writer)) {
                GSON.toJson(json, json2);
            }
        });
        ci.cancel();
    }

    /** Stats files are named <uuid>.json; anything else queues under a fixed key. */
    private static UUID ownerOf(Path path) {
        String name = path.getFileName().toString().replace(".json", "");
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException notUuid) {
            return new UUID(0, 0);
        }
    }
}
