package com.silver.arcade.qa;

import com.silver.qaclient.Bot;
import com.silver.qaclient.QaScenario;

/**
 * The arcade's QA scenario for the QA Client util (Util-Mods/QA-Client): plays the arcade like a person. System
 * properties: arcadeqa.rooms (all or ids), arcadeqa.tier, arcadeqa.force (the QA server's config/arcade-force-room.txt),
 * arcadeqa.choice, arcadeqa.exit, arcadeqa.giveup.
 */
public final class ArcadeQa implements QaScenario {
    @Override
    public String name() {
        return "arcade";
    }

    @Override
    public void run(Bot bot) {
        new Scenario(bot).run();
    }
}
