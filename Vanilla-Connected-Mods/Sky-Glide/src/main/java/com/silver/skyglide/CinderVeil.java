package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.platform.particle.ParticleBuilder;
import me.moros.bending.api.platform.potion.PotionEffect;

import java.util.List;

/** A cloak of embers that makes a firebender immune to fire and lava for a while. */
public final class CinderVeil extends Buff {
    private Config userConfig;

    public CinderVeil(AbilityDescription description) {
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
        user.addPotion(PotionEffect.FIRE_RESISTANCE.builder().duration(ticks).amplifier(0).build());
    }

    @Override
    void shimmer() {
        ParticleBuilder.fire(user, user.location().add(0, 0.9, 0)).count(2).offset(0.5, 0.7, 0.5).spawn(user.world());
    }

    /** Values live in bending.conf under abilities.fire.cinderveil. */
    private static final class Config implements Configurable {
        @Modifiable(Attribute.COOLDOWN)
        private long cooldown = 45000;
        @Modifiable(Attribute.DURATION)
        private long duration = 60000;

        @Override
        public List<String> path() {
            return List.of("abilities", "fire", "cinderveil");
        }
    }
}
