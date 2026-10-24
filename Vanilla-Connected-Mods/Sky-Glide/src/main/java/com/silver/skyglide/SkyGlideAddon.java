package com.silver.skyglide;

import me.moros.bending.api.ability.AbilityDescription;
import me.moros.bending.api.ability.Activation;
import me.moros.bending.api.ability.element.Element;
import me.moros.bending.api.addon.Addon;
import me.moros.bending.api.addon.BendingContext;
import me.moros.bending.api.registry.Registries;
import net.kyori.adventure.text.Component;

/** Adds SkyGlide (a slow, time-limited Air flight), TideWard (Water's defense) and four utility abilities (Gills, CinderVeil, DeepSight, Updraft) to Bending. Loaded from config/bending/addons. */
public final class SkyGlideAddon implements Addon {
    @Override
    public void load(BendingContext context) {
        AbilityDescription description = AbilityDescription.builder("SkyGlide", SkyGlide::new)
            .element(Element.AIR)
            .activation(Activation.ATTACK)
            .displayName(Component.text("SkyGlide"))
            .build();
        Registries.ABILITIES.register(description);
        Registries.ABILITIES.register(AbilityDescription.builder("TideWard", TideWard::new)
            .element(Element.WATER)
            .activation(Activation.ATTACK)
            .displayName(Component.text("TideWard"))
            .build());
        Registries.ABILITIES.register(AbilityDescription.builder("Gills", Gills::new)
            .element(Element.WATER)
            .activation(Activation.ATTACK)
            .displayName(Component.text("Gills"))
            .build());
        Registries.ABILITIES.register(AbilityDescription.builder("CinderVeil", CinderVeil::new)
            .element(Element.FIRE)
            .activation(Activation.ATTACK)
            .displayName(Component.text("CinderVeil"))
            .build());
        Registries.ABILITIES.register(AbilityDescription.builder("DeepSight", DeepSight::new)
            .element(Element.EARTH)
            .activation(Activation.ATTACK)
            .displayName(Component.text("DeepSight"))
            .build());
        Registries.ABILITIES.register(AbilityDescription.builder("Updraft", Updraft::new)
            .element(Element.AIR)
            .activation(Activation.ATTACK)
            .displayName(Component.text("Updraft"))
            .build());
    }
}
