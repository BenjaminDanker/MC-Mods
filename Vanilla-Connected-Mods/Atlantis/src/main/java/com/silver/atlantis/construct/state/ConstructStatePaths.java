package com.silver.atlantis.construct.state;

import java.nio.file.Path;

public final class ConstructStatePaths {

    private ConstructStatePaths() {
    }

    public static Path stateFile(Path runDir) {
        return runDir.resolve("construct_state.json");
    }
}
