package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.platform.particle.ParticleBuilder;
import me.moros.bending.api.platform.potion.PotionEffect;

import java.util.List;

/** Water's way to move under the sea: breathe water and swim like a dolphin for a while. */
public final class Gills extends Buff {
    private Config userConfig;

    public Gills(AbilityDescription description) {
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
        user.addPotion(PotionEffect.WATER_BREATHING.builder().duration(ticks).amplifier(0).build());
        user.addPotion(PotionEffect.DOLPHINS_GRACE.builder().duration(ticks).amplifier(0).build());
    }

    @Override
    void shimmer() {
        ParticleBuilder.bubble(user.location().add(0, 1.0, 0)).count(2).offset(0.4, 0.6, 0.4).spawn(user.world());
    }

    /** Values live in bending.conf under abilities.water.gills. */
    private static final class Config implements Configurable {
        @Modifiable(Attribute.COOLDOWN)
        private long cooldown = 60000;
        @Modifiable(Attribute.DURATION)
        private long duration = 90000;

        @Override
        public List<String> path() {
            return List.of("abilities", "water", "gills");
        }
    }
}
