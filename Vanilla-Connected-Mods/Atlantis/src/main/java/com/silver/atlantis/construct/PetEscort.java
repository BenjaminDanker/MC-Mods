package com.silver.atlantis.construct;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;

/**
 * Pet-Companion pets that stand in the world (not held) do not follow their owner through a teleport.
 * This finds a player's placed pets without depending on Pet-Companion: its tameable mixin exposes
 * {@code aipets$isPet()} and {@code aipets$getOwnerUuid()}, looked up by name. Without that mod nothing matches.
 */
public final class PetEscort {
    private static final ConcurrentMap<Class<?>, Method[]> METHODS = new ConcurrentHashMap<>();
    private static final Method[] NONE = new Method[0];

    private PetEscort() {
    }

    /** True for a Pet-Companion pet entity, whoever owns it. */
    public static boolean isPet(Object entity) {
        Method[] methods = methodsOf(entity.getClass());
        if (methods.length == 0) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(methods[0].invoke(entity));
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    public static boolean isOwnedPet(Object entity, UUID owner) {
        if (!isPet(entity)) {
            return false;
        }
        try {
            return owner.equals(methodsOf(entity.getClass())[1].invoke(entity));
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    /** Moves the player's placed pets next to a spot their owner has just been teleported to. Returns how many moved. */
    public static int follow(ServerPlayer owner, ServerLevel level, double x, double y, double z) {
        UUID id = owner.getUUID();
        int moved = 0;
        for (TamableAnimal pet : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(TamableAnimal.class),
                candidate -> isOwnedPet(candidate, id))) {
            if (pet.teleportTo(level, x + 1.0, y, z, Set.of(), pet.getYRot(), pet.getXRot(), false)) {
                moved++;
            }
        }
        return moved;
    }

    private static Method[] methodsOf(Class<?> type) {
        return METHODS.computeIfAbsent(type, found -> {
            try {
                return new Method[] {found.getMethod("aipets$isPet"), found.getMethod("aipets$getOwnerUuid")};
            } catch (NoSuchMethodException absent) {
                return NONE;
            }
        });
    }
}
