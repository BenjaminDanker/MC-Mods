package com.silver.playersync.mixin;

import java.nio.file.CopyOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's save moves the previous file to "*_old" first. When that file is already gone (a player moved servers
 * between two saves) the move fails and the whole save with it; a missing file there is harmless, so it counts as done.
 */
@Mixin(Util.class)
public class OldFileRenameMixin {
    @Inject(method = "createRenamer(Ljava/nio/file/Path;Ljava/nio/file/Path;[Ljava/nio/file/CopyOption;)Ljava/util/function/BooleanSupplier;",
        at = @At("RETURN"), cancellable = true)
    private static void playersync$tolerateMissingOld(Path from, Path to, CopyOption[] options, CallbackInfoReturnable<BooleanSupplier> cir) {
        BooleanSupplier rename = cir.getReturnValue();
        cir.setReturnValue(() -> {
            try {
                return rename.getAsBoolean();
            } catch (Throwable failure) { // vanilla can rethrow an IOException unchecked
                if (missingFile(failure) && to.getFileName() != null && to.getFileName().toString().endsWith("_old")) {
                    return true;
                }
                if (failure instanceof RuntimeException unchecked) {
                    throw unchecked;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                Util.throwAsRuntime(failure);
                return false;
            }
        });
    }

    private static boolean missingFile(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof NoSuchFileException) {
                return true;
            }
        }
        return false;
    }
}
