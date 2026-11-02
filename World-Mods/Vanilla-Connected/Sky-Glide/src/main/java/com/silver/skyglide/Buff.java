package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.ability.AbilityInstance;
import me.moros.bending.api.ability.Activation;
import me.moros.bending.api.user.User;
import me.moros.bending.api.util.functional.ExpireRemovalPolicy;
import me.moros.bending.api.util.functional.Policies;
import me.moros.bending.api.util.functional.RemovalPolicy;

/** A utility ability that gives its user potion effects for a while, then rests. Subclasses say which and for how long. */
abstract class Buff extends AbilityInstance {
    private RemovalPolicy removalPolicy;

    Buff(AbilityDescription description) {
        super(description);
    }

    abstract long durationMillis();

    abstract long cooldownMillis();

    /** Called once on activation; {@code ticks} is the duration in game ticks. */
    abstract void apply(int ticks);

    /** Called every update while the buff lasts. */
    abstract void shimmer();

    @Override
    public boolean activate(User user, Activation method) {
        if (user.game().abilityManager(user.worldKey()).hasAbility(user, getClass())) {
            return false;
        }
        this.user = user;
        loadConfig();
        removalPolicy = Policies.builder()
            .add(Policies.DEAD)
            .add(ExpireRemovalPolicy.of(durationMillis()))
            .build();
        apply((int) Math.max(20, durationMillis() / 50));
        return true;
    }

    @Override
    public UpdateResult update() {
        if (removalPolicy.test(user, description())) {
            return UpdateResult.REMOVE;
        }
        shimmer();
        return UpdateResult.CONTINUE;
    }

    @Override
    public void onDestroy() {
        user.addCooldown(description(), cooldownMillis());
    }
}
