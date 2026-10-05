package com.silver.aipets.fabric.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;

/** Per-species adapter. Unknown mobs safely support only looking and pausing. */
public interface PetActionCapabilities {
    boolean supports(PetIntent intent);
    void sit(Mob pet, boolean sitting);
    void vocalize(Mob pet);

    static PetActionCapabilities forEntity(Mob pet) {
        if (pet instanceof Wolf) return VANILLA_DOG;
        if (pet instanceof Cat) return VANILLA_CAT;
        return FALLBACK;
    }

    PetActionCapabilities FALLBACK = new PetActionCapabilities() {
        @Override public boolean supports(PetIntent intent) {
            return intent == PetIntent.PAUSE || intent == PetIntent.LOOK_AT_OWNER
                    || intent == PetIntent.LOOK_AT_INTEREST;
        }
        @Override public void sit(Mob pet, boolean sitting) { }
        @Override public void vocalize(Mob pet) { }
    };

    PetActionCapabilities VANILLA_DOG = new Vanilla(true);
    PetActionCapabilities VANILLA_CAT = new Vanilla(false);

    final class Vanilla implements PetActionCapabilities {
        private final boolean dog;
        private Vanilla(boolean dog) { this.dog = dog; }
        @Override public boolean supports(PetIntent intent) {
            return true;
        }
        @Override public void sit(Mob pet, boolean sitting) {
            if (pet instanceof TamableAnimal tameable) {
                if (tameable.isOrderedToSit() != sitting) tameable.setOrderedToSit(sitting);
                if (tameable.isInSittingPose() != sitting) tameable.setInSittingPose(sitting);
                if (sitting) tameable.getNavigation().stop();
            }
        }
        @Override public void vocalize(Mob pet) {
            // Mob.playAmbientSound dispatches to Cat/Wolf's real vanilla sound selection.
            PetActionController.playControlledAmbient(pet);
        }
        public boolean isDog() { return dog; }
    }
}
