package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.platform.particle.ParticleBuilder;
import me.moros.bending.api.platform.potion.PotionEffect;
import me.moros.math.Vector3d;

import java.util.List;

/** An airbender throws themselves up on a column of wind and drifts down gently afterwards. */
public final class Updraft extends Buff {
    private Config userConfig;

    public Updraft(AbilityDescription description) {
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
        Vector3d look = user.direction();
        user.applyVelocity(this, Vector3d.of(look.x() * userConfig.forward, userConfig.lift, look.z() * userConfig.forward));
        user.addPotion(PotionEffect.SLOW_FALLING.builder().duration(ticks).amplifier(0).build());
    }

    @Override
    void shimmer() {
        ParticleBuilder.air(user.location().add(0, 0.3, 0)).count(3).offset(0.3, 0.3, 0.3).spawn(user.world());
    }

    /** Values live in bending.conf under abilities.air.updraft. */
    private static final class Config implements Configurable {
        @Modifiable(Attribute.COOLDOWN)
        private long cooldown = 12000;
        /** How long the gentle fall lasts. */
        @Modifiable(Attribute.DURATION)
        private long duration = 8000;
        @Modifiable(Attribute.SPEED)
        private double lift = 1.1;
        private double forward = 0.35;

        @Override
        public List<String> path() {
            return List.of("abilities", "air", "updraft");
        }
    }
}
