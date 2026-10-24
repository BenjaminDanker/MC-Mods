package com.silver.bossunlock;

import java.util.ArrayList;
import java.util.List;

/** config/boss-unlock.json. Each rule names a boss (by entity tag or tag prefix, optionally its type) and an element. */
public final class BossUnlockConfig {
    /** Players within this many blocks of the boss when it dies share the unlock. */
    public double shareRadiusBlocks = 48;
    public List<Rule> rules = new ArrayList<>();

    public static final class Rule {
        public String tag;
        public String tagPrefix;
        public String entityType;
        public String element;
    }
}
