package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.ability.AbilityInstance;
import me.moros.bending.api.ability.Activation;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.platform.particle.ParticleBuilder;
import me.moros.bending.api.platform.potion.PotionEffect;
import me.moros.bending.api.user.User;
import me.moros.bending.api.util.functional.ExpireRemovalPolicy;
import me.moros.bending.api.util.functional.Policies;
import me.moros.bending.api.util.functional.RemovalPolicy;

import java.util.List;

/**
 * A waterbender's basic defense: a shell of water that soaks up damage (absorption hearts) and softens
 * what gets through (resistance) for a few seconds. Water had no real shield of its own.
 */
public final class TideWard extends AbilityInstance {
    private Config userConfig;
    private RemovalPolicy removalPolicy;

    public TideWard(AbilityDescription description) {
        super(description);
    }

    @Override
    public boolean activate(User user, Activation method) {
        if (user.game().abilityManager(user.worldKey()).hasAbility(user, TideWard.class)) {
            return false;
        }
        this.user = user;
        loadConfig();
        removalPolicy = Policies.builder()
            .add(Policies.DEAD)
            .add(ExpireRemovalPolicy.of(userConfig.duration))
            .build();
        int ticks = (int) Math.max(20, userConfig.duration / 50);
        user.addPotion(PotionEffect.ABSORPTION.builder().duration(ticks).amplifier(userConfig.absorptionLevel).build());
        user.addPotion(PotionEffect.RESISTANCE.builder().duration(ticks).amplifier(userConfig.resistanceLevel).build());
        return true;
    }

    @Override
    public void loadConfig() {
        userConfig = user.game().configProcessor().calculate(this, Config.class);
    }

    @Override
    public UpdateResult update() {
        if (removalPolicy.test(user, description())) {
            return UpdateResult.REMOVE;
        }
        ParticleBuilder.bubble(user.location().add(0, 0.9, 0)).count(3).offset(0.6, 0.8, 0.6).spawn(user.world());
        return UpdateResult.CONTINUE;
    }

    @Override
    public void onDestroy() {
        user.addCooldown(description(), userConfig.cooldown);
    }

    /** Values live in bending.conf under abilities.water.tideward. */
    private static final class Config implements Configurable {
        @Modifiable(Attribute.COOLDOWN)
        private long cooldown = 14000;
        @Modifiable(Attribute.DURATION)
        private long duration = 8000;
        /** Absorption amplifier: level 2 gives 6 extra hearts. */
        private int absorptionLevel = 2;
        /** Resistance amplifier: level 1 reduces damage by 40%. */
        private int resistanceLevel = 1;

        @Override
        public List<String> path() {
            return List.of("abilities", "water", "tideward");
        }
    }
}
