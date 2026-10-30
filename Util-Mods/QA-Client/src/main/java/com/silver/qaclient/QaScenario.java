package com.silver.qaclient;

/**
 * A test scenario for the QA client: plays one server like a person, through the Bot, while Rec, Hud and Chunks record
 * what the client sees. A mod registers its scenarios under the "qaclient" entrypoint (fabric.mod.json); the client
 * runs the one named by -Dqaclient.scenario, or the only one installed.
 */
public interface QaScenario {
    /** The name -Dqaclient.scenario selects this scenario by. */
    String name();

    /** Runs on its own thread; the client stops when it returns. */
    void run(Bot bot) throws Exception;
}
