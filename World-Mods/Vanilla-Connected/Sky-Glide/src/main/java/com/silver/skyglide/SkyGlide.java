package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.ability.AbilityInstance;
import me.moros.bending.api.ability.Activation;
import me.moros.bending.api.config.Configurable;
import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.Modifiable;
import me.moros.bending.api.user.User;
import me.moros.bending.api.util.functional.ExpireRemovalPolicy;
import me.moros.bending.api.util.functional.Policies;
import me.moros.bending.api.util.functional.RemovalPolicy;
import me.moros.math.Vector3d;

import java.util.List;

/**
 * Slow gliding flight for airbenders: steer with your look direction, sneak to land. It is nowhere
 * near elytra speed, runs for a limited time and then needs a long rest.
 */
public final class SkyGlide extends AbilityInstance {
    private Config userConfig;
    private RemovalPolicy removalPolicy;

    public SkyGlide(AbilityDescription description) {
        super(description);
    }

    @Override
    public boolean activate(User user, Activation method) {
        if (user.game().abilityManager(user.worldKey()).hasAbility(user, SkyGlide.class)) {
            return false;
        }
        this.user = user;
        loadConfig();
        removalPolicy = Policies.builder()
            .add(Policies.SNEAKING)
            .add(Policies.UNDER_WATER)
            .add(Policies.UNDER_LAVA)
            .add(ExpireRemovalPolicy.of(userConfig.duration))
            .build();
        return true;
    }

    @Override
    public void loadConfig() {
        userConfig = user.game().configProcessor().calculate(this, Config.class);
    }

    @Override
    public UpdateResult update() {
        if (removalPolicy.test(user, description()) || !user.canBuild()) {
            return UpdateResult.REMOVE;
        }
        Vector3d look = user.direction();
        double vertical = Math.max(-userConfig.maxDescent, Math.min(userConfig.maxClimb, look.y() * userConfig.speed));
        user.applyVelocity(this, Vector3d.of(look.x() * userConfig.speed, vertical, look.z() * userConfig.speed));
        return UpdateResult.CONTINUE;
    }

    @Override
    public void onDestroy() {
        user.addCooldown(description(), userConfig.cooldown);
    }

    /** Values live in bending.conf under abilities.air.skyglide. */
    private static final class Config implements Configurable {
        @Modifiable(Attribute.SPEED)
        private double speed = 0.42;
        @Modifiable(Attribute.COOLDOWN)
        private long cooldown = 30000;
        @Modifiable(Attribute.DURATION)
        private long duration = 25000;
        private double maxClimb = 0.18;
        private double maxDescent = 0.32;

        @Override
        public List<String> path() {
            return List.of("abilities", "air", "skyglide");
        }
    }
}
