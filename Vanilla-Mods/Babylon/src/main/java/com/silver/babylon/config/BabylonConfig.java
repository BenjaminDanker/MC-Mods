package com.silver.babylon.config;

public final class BabylonConfig {
    public Region region = new Region();
    public String notWorthyMessage = "You are not worthy";
    public String portalRedirectTargetServer = "magic";
    public String portalRedirectTargetPortal = "";
    public String portalRequestSecret = "silver";
    public int entryDelaySeconds = 1;
    public int particleSeconds = 3;

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
