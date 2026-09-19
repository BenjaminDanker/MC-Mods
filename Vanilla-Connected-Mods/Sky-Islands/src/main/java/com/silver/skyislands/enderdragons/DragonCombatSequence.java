package com.silver.skyislands.enderdragons;

/** Small deterministic encounter state machine, independent of world/entity access. */
public final class DragonCombatSequence {
    public enum Stage { APPROACH, WINDUP, ATTACK, RECOVER, EXHAUSTED }
    public enum Attack { SWOOP, BREATH_SWEEP, WING_GUST }
    private Stage stage=Stage.APPROACH;
    private Attack attack=Attack.BREATH_SWEEP;
    private int age;
    private int recovery=32;
    private int completedAttacks;
    private boolean damageStaggerUsed;
    public Stage stage() { return stage; }
    public Attack attack() { return attack; }
    public int age() { return age; }
    public boolean contactAttack() { return stage==Stage.ATTACK && attack==Attack.SWOOP; }
    public boolean staggerFromDamage(float damage) {
        if (stage!=Stage.WINDUP || damage<8 || damageStaggerUsed) return false;
        damageStaggerUsed=true;
        stagger();
        return true;
    }
    public boolean advance(double distance, boolean enraged, int variation) {
        return advance(distance,enraged,variation,null);
    }
    public boolean advance(double distance, boolean enraged, int variation, Attack preferred) {
        age++;
        return switch(stage) {
            case APPROACH -> {
                if (distance>64 || age<12) yield false;
                Attack next=distance<28 && attack!=Attack.WING_GUST ? Attack.WING_GUST
                        : attack==Attack.SWOOP ? Attack.BREATH_SWEEP
                        : attack==Attack.BREATH_SWEEP ? Attack.SWOOP
                        : (variation&1)==0 ? Attack.SWOOP : Attack.BREATH_SWEEP;
                if (preferred!=null && preferred!=attack && (preferred!=Attack.WING_GUST || distance<20)) next=preferred;
                attack=next; yield enter(Stage.WINDUP);
            }
            case WINDUP -> age >= (enraged?14:20) && enter(Stage.ATTACK);
            case ATTACK -> age >= (attack==Attack.WING_GUST?12:attack==Attack.SWOOP?45:48)
                    && finishAttack(enraged);
            case EXHAUSTED -> age>=80 && enter(Stage.APPROACH);
            case RECOVER -> age>=recovery && enter(Stage.APPROACH);
        };
    }
    private boolean finishAttack(boolean enraged) {
        if (++completedAttacks >= 3) {
            completedAttacks=0;
            damageStaggerUsed=false;
            return enter(Stage.EXHAUSTED);
        }
        return recover(enraged?22:32);
    }
    public void engage() { completedAttacks=0; damageStaggerUsed=false; enter(Stage.APPROACH); }
    public float headDamage(float amount) { return stage==Stage.EXHAUSTED ? amount*1.5f : amount; }
    private boolean enter(Stage next) { stage=next;age=0;return true; }
    private boolean recover(int ticks) { recovery=ticks;return enter(Stage.RECOVER); }
    public void stagger() { recover(75); }
}
