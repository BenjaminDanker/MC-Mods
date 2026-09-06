package com.silver.aipets.fabric.mixin;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.entity.PetEntityController;
import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(TameableEntity.class)
public abstract class TameableEntityMixin implements PetEntityData {
    @Unique private static final String MARKER_KEY = "pet_companion:marked";
    @Unique private static final String PET_ID_KEY = "pet_companion:pet_id";
    @Unique private static final String OWNER_ID_KEY = "pet_companion:owner_uuid";
    @Unique private static final String RECORD_VERSION_KEY = "pet_companion:record_version";
    @Unique private static final String SLEEPING_KEY = "pet_companion:sleeping";

    @Unique private UUID aipets$petId;
    @Unique private UUID aipets$ownerUuid;
    @Unique private long aipets$recordVersion;
    @Unique private boolean aipets$sleeping;

    @Inject(method = "writeCustomData(Lnet/minecraft/storage/WriteView;)V", at = @At("TAIL"))
    private void aipets$writeIdentity(WriteView view, CallbackInfo ci) {
        if (!aipets$isPet()) {
            return;
        }
        view.putBoolean(MARKER_KEY, true);
        view.putString(PET_ID_KEY, aipets$petId.toString());
        view.putString(OWNER_ID_KEY, aipets$ownerUuid.toString());
        view.putLong(RECORD_VERSION_KEY, aipets$recordVersion);
        view.putBoolean(SLEEPING_KEY, aipets$sleeping);
    }

    @Inject(method = "readCustomData(Lnet/minecraft/storage/ReadView;)V", at = @At("TAIL"))
    private void aipets$readIdentity(ReadView view, CallbackInfo ci) {
        clearIdentity();
        if (!view.getBoolean(MARKER_KEY, false)) {
            return;
        }
        try {
            UUID petId = UUID.fromString(view.getString(PET_ID_KEY, ""));
            UUID ownerId = UUID.fromString(view.getString(OWNER_ID_KEY, ""));
            long version = view.getLong(RECORD_VERSION_KEY, -1L);
            if (version < 0) {
                throw new IllegalArgumentException("negative record version");
            }
            aipets$petId = petId;
            aipets$ownerUuid = ownerId;
            aipets$recordVersion = version;
            aipets$sleeping = view.getBoolean(SLEEPING_KEY, false);
            PetEntityController.configure((TameableEntity) (Object) this);
        } catch (IllegalArgumentException malformed) {
            clearIdentity();
            PetCompanionMod.LOGGER.warn(StructuredPetEvent
                    .operation("entity_identity_nbt_read")
                    .failure(malformed).outcome("identity_cleared").toJson());
        }
    }

    @Inject(method = "canBeLeashed()Z", at = @At("HEAD"), cancellable = true)
    private void aipets$preventLeashing(CallbackInfoReturnable<Boolean> cir) {
        if (aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tryTeleportToOwner()V", at = @At("HEAD"), cancellable = true)
    private void aipets$preventOwnerTeleport(CallbackInfo ci) {
        if (aipets$isPet()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldTryTeleportToOwner()Z", at = @At("HEAD"), cancellable = true)
    private void aipets$neverTryOwnerTeleport(CallbackInfoReturnable<Boolean> cir) {
        if (aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "setOwner(Lnet/minecraft/entity/LivingEntity;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventOwnerMutation(LivingEntity owner, CallbackInfo ci) {
        if (aipets$isPet() && owner != null) {
            ci.cancel();
        }
    }

    @Inject(
            method = "setOwner(Lnet/minecraft/entity/LazyEntityReference;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$preventOwnerReferenceMutation(
            LazyEntityReference<LivingEntity> owner, CallbackInfo ci) {
        if (aipets$isPet() && owner != null) {
            ci.cancel();
        }
    }

    @Inject(method = "setTamedBy", at = @At("HEAD"), cancellable = true)
    private void aipets$preventTaming(PlayerEntity player, CallbackInfo ci) {
        if (aipets$isPet()) {
            ci.cancel();
        }
    }

    @Inject(method = "setTamed", at = @At("HEAD"), cancellable = true)
    private void aipets$preventTamedState(boolean tamed, boolean updateAttributes, CallbackInfo ci) {
        if (aipets$isPet() && tamed) {
            ci.cancel();
        }
    }

    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void aipets$preventLivingTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (aipets$isPet()) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public boolean aipets$isPet() {
        return aipets$petId != null && aipets$ownerUuid != null;
    }

    @Override
    public UUID aipets$getPetId() {
        return aipets$petId;
    }

    @Override
    public UUID aipets$getOwnerUuid() {
        return aipets$ownerUuid;
    }

    @Override
    public long aipets$getRecordVersion() {
        return aipets$recordVersion;
    }

    @Override
    public boolean aipets$isSleeping() {
        return aipets$sleeping;
    }

    @Override
    public void aipets$mark(UUID petId, UUID ownerUuid, long recordVersion, boolean sleeping) {
        if (recordVersion < 0) {
            throw new IllegalArgumentException("recordVersion must be non-negative");
        }
        aipets$petId = java.util.Objects.requireNonNull(petId, "petId");
        aipets$ownerUuid = java.util.Objects.requireNonNull(ownerUuid, "ownerUuid");
        aipets$recordVersion = recordVersion;
        aipets$sleeping = sleeping;
    }

    @Override
    public void aipets$setRecordVersion(long recordVersion) {
        if (!aipets$isPet() || recordVersion < 0) {
            throw new IllegalStateException("Cannot set a negative version or update an unmarked entity");
        }
        aipets$recordVersion = recordVersion;
    }

    @Override
    public void aipets$setSleeping(boolean sleeping) {
        if (!aipets$isPet()) {
            throw new IllegalStateException("Cannot update an unmarked entity");
        }
        aipets$sleeping = sleeping;
        if (sleeping) {
            ((TameableEntity) (Object) this).getNavigation().stop();
        }
    }

    @Unique
    private void clearIdentity() {
        aipets$petId = null;
        aipets$ownerUuid = null;
        aipets$recordVersion = 0;
        aipets$sleeping = false;
    }
}
