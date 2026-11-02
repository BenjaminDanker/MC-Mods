package com.silver.babylon.config;

import java.util.ArrayList;
import java.util.List;

public final class BabylonConfig {
    public Region region = new Region();
    public String notWorthyMessage = "You are not worthy";
    public String portalRedirectTargetServer = "magic";
    public String portalRedirectTargetPortal = "";
    public String portalRequestSecret = ""; // the network secret comes from ~/.config/mc-network/shared.properties
    public int entryDelaySeconds = 1;
    public int particleSeconds = 3;
    /** When non-empty, a worthy player is offered these paths in chat (option numbers follow list order). */
    public List<Choice> choices = new ArrayList<>();
    public String chooseHeader = "Choose your path:";
    /** Optional line under the options; blank sends nothing. */
    public String chooseFooter = "";

    public static final class Choice {
        public String id = "";
        public String label = "";
        public String targetServer = "";
        public String targetPortal = "";
    }

    public static final class Region {
        public int minX = 0;
        public int minY = 0;
        public int minZ = 0;
        public int maxX = 0;
        public int maxY = 0;
        public int maxZ = 0;

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }
}
