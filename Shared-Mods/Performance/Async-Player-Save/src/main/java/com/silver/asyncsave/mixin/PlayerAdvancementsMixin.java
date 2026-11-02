package com.silver.asyncsave.mixin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.silver.asyncsave.AsyncSaves;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.util.FileUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Advancement files: encoded on the server thread, written off it. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow
    @Final
    private static Gson GSON;

    @Shadow
    @Final
    private Path playerSavePath;

    @Shadow
    @Final
    private Codec<Object> codec;

    /** asData() returns a private record type, so it is called reflectively (once looked up). */
    @org.spongepowered.asm.mixin.Unique
    private static java.lang.reflect.Method asyncsave$asData;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void asyncsave$save(CallbackInfo ci) {
        Object data;
        try {
            if (asyncsave$asData == null) {
                java.lang.reflect.Method method = PlayerAdvancements.class.getDeclaredMethod("asData");
                method.setAccessible(true);
                asyncsave$asData = method;
            }
            data = asyncsave$asData.invoke(this);
        } catch (ReflectiveOperationException failure) {
            return; // vanilla saves it the usual way
        }
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        Path target = playerSavePath;
        UUID owner;
        try {
            owner = UUID.fromString(target.getFileName().toString().replace(".json", ""));
        } catch (IllegalArgumentException notUuid) {
            owner = new UUID(0, 0);
        }
        AsyncSaves.submit(owner, "advancements", () -> {
            FileUtil.createDirectoriesSafe(target.getParent());
            try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8); JsonWriter out = GSON.newJsonWriter(writer)) {
                GSON.toJson(json, out);
            }
        });
        ci.cancel();
    }
}
