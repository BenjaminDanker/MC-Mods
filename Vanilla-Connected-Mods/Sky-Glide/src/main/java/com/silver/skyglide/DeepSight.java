package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.platform.particle.ParticleBuilder;
import me.moros.bending.api.platform.potion.PotionEffect;

import java.util.List;

/** An earthbender feels the stone around them: they see in the dark and dig faster for a while. */
public final class DeepSight extends Buff {
    private Config userConfig;

    public DeepSight(AbilityDescription description) {
        super(description);
    }

    @Override
    public void loadConfig() {
        userConfig = user.game().configProcessor().calculate(this, Config.class);
    }

    @Override
    long durationMillis() {
        return userConfig.duration;
    }

    @Override
    long cooldownMillis() {
        return userConfig.cooldown;
    }

    @Override
    void apply(int ticks) {
        user.addPotion(PotionEffect.NIGHT_VISION.builder().duration(ticks).amplifier(0).build());
        user.addPotion(PotionEffect.HASTE.builder().duration(ticks).amplifier(1).build());
    }

    @Override
    void shimmer() {
        ParticleBuilder.rgb(user.location().add(0, 1.0, 0), "8B7355").count(1).offset(0.4, 0.6, 0.4).spawn(user.world());
    }

    /** Values live in bending.conf under abilities.earth.deepsight. */
    private static final class Config implements Configurable {
        @Modifiable(Attribute.COOLDOWN)
        private long cooldown = 60000;
        @Modifiable(Attribute.DURATION)
        private long duration = 90000;

        @Override
        public List<String> path() {
            return List.of("abilities", "earth", "deepsight");
        }
    }
}
