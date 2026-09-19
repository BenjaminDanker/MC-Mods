package com.silver.skyislands.enderdragons;

import java.util.ArrayList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.Vec3;
import static com.silver.skyislands.enderdragons.DragonCombatSequence.*;

/** Telegraph, commit, recover. At most three projectiles; no scans of world entities or chunk loads. */
public final class DragonCombatController {
    private final DragonFlight flight = new DragonFlight();
    private final DragonSphericalGust sphericalGust=new DragonSphericalGust();
    private record Contact(long tick, boolean ranged) {}
    private final java.util.HashMap<java.util.UUID, Contact> contacts = new java.util.HashMap<>();
    private final ArrayList<DragonFlight.Threat> threats = new ArrayList<>();
    private java.util.UUID selected;
    private long nextSelection, nextObservation;
    private final DragonCombatSequence cycle=new DragonCombatSequence();
    private record Shot(DragonFireball entity, long expires) {}
    private final ArrayList<Shot> projectiles=new ArrayList<>(3);
    private Vec3 aim=Vec3.ZERO, committed= new Vec3(1,0,0), side=new Vec3(0,0,1);
    private float windupHealth;
    private DragonBreathRun breathRun;
    private int breathPass;

    private void clearProjectiles() {
        for (var shot:projectiles) shot.entity().discard();
        projectiles.clear();
    }
    public void engage(EnderDragon dragon, ServerPlayer target) {
        clearProjectiles();
        cycle.engage();
        flight.reset();
        selected=target.getUUID();
        nextSelection=0;
        nextObservation=0;
        contacts.putIfAbsent(target.getUUID(), new Contact(dragon.level().getGameTime(), false));
        committed=unit(target.position().subtract(dragon.position()));
        move(dragon,committed,.8);
        dragon.playSound(SoundEvents.ENDER_DRAGON_GROWL,2,1);
    }
    public float headDamage(float amount) { return cycle.headDamage(amount); }
    public boolean contactAttack() { return cycle.contactAttack(); }
    public void interrupt() { cycle.stagger(); clearProjectiles(); }

    /** Remember actual attackers, including projectile owners; never infer an attack from a held weapon. */
    public void observeDamage(EnderDragon dragon, net.minecraft.world.damagesource.DamageSource source) {
        var attacker=source.getEntity();
        if (!(attacker instanceof ServerPlayer) && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)
            attacker=projectile.getOwner();
        if (!(attacker instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator() || player.level()!=dragon.level()) return;
        long now=dragon.level().getGameTime();
        contacts.entrySet().removeIf(e -> now-e.getValue().tick()>600);
        boolean projectile=source.is(DamageTypeTags.IS_PROJECTILE)
                || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile;
        if (contacts.size()<64 || contacts.containsKey(player.getUUID()))
            contacts.put(player.getUUID(),new Contact(now,projectile));
        if (!projectile && source.getDirectEntity()==player && sphericalGust.recordMeleeHit(player.getUUID(),now))
            sphericalGust((ServerLevel)dragon.level(),dragon,now);
    }

    public ServerPlayer selectTarget(EnderDragon dragon, ServerPlayer fallback) {
        ServerLevel world=(ServerLevel)dragon.level();
        long now=world.getGameTime();
        ServerPlayer current=selected==null?null:world.getServer().getPlayerList().getPlayer(selected);
        if (validTarget(dragon,current) && (now<nextSelection || cycle.stage()!=Stage.APPROACH)) return current;
        nextSelection=now+40;
        contacts.entrySet().removeIf(e -> now-e.getValue().tick()>600);
        var candidates=new ArrayList<DragonTargetSelection.Candidate>();
        if (validTarget(dragon,fallback))
            candidates.add(candidate(dragon,fallback,now));
        if (validTarget(dragon,current))
            candidates.add(candidate(dragon,current,now));
        for (var id:contacts.keySet()) {
            ServerPlayer player=world.getServer().getPlayerList().getPlayer(id);
            if (!validTarget(dragon,player)) continue;
            candidates.add(candidate(dragon,player,now));
        }
        java.util.UUID next=DragonTargetSelection.choose(candidates,selected,now);
        if (!java.util.Objects.equals(selected,next)) {
            nextObservation=0;
            flight.reset();
            // Never redirect a committed attack when its target disconnects or changes dimension.
            if (cycle.stage()==Stage.ATTACK || cycle.stage()==Stage.WINDUP) interrupt();
        }
        selected=next;
        return next==null?null:world.getServer().getPlayerList().getPlayer(next);
    }
    private boolean validTarget(EnderDragon dragon,ServerPlayer player) {
        return player!=null && player.isAlive() && !player.isSpectator() && player.level()==dragon.level() && dragon.distanceToSqr(player)<=256*256;
    }
    private DragonTargetSelection.Candidate candidate(EnderDragon dragon,ServerPlayer player,long now) {
        Contact hit=contacts.get(player.getUUID());
        return new DragonTargetSelection.Candidate(player.getUUID(),dragon.distanceTo(player),hit==null?now-600:hit.tick());
    }

    public void tick(EnderDragon dragon, ServerPlayer target) {
        ServerLevel world=(ServerLevel)dragon.level();
        long now=world.getGameTime();
        projectiles.removeIf(shot -> { if (now>=shot.expires()) shot.entity().discard(); return shot.entity().isRemoved(); });
        // Observe a bounded local encounter at 4 Hz, not all entities or world blocks.
        if (now>=nextObservation) {
            nextObservation=now+5;
            threats.clear();
            Contact targetHit=contacts.get(target.getUUID());
            threats.add(new DragonFlight.Threat(target.position(),target.getKnownMovement(),airborne(target),
                    targetHit!=null && targetHit.ranged() && now-targetHit.tick()<100));
            for (ServerPlayer player:world.players()) {
                if (player==target || !player.isAlive() || player.isSpectator() || dragon.distanceToSqr(player)>80*80
                        || (player.isCreative() && !contacts.containsKey(player.getUUID())) || threats.size()>=64) continue;
                Contact hit=contacts.get(player.getUUID());
                threats.add(new DragonFlight.Threat(player.position(),player.getKnownMovement(),airborne(player),
                        hit!=null && hit.ranged() && now-hit.tick()<100));
            }
        }
        if (cycle.staggerFromDamage(windupHealth-dragon.getHealth())) {
            dragon.playSound(SoundEvents.ENDER_DRAGON_HURT,1.5f,.8f);
        }
        // Finish lining up a pass before winding up. A nearby player directly below or behind
        // must not repeatedly stop pursuit just because the distance is small.
        boolean linedUp=DragonFlight.attackReady(dragon.position(),dragon.getDeltaMovement(),target.position());
        boolean changed=cycle.advance(cycle.stage()==Stage.APPROACH && !linedUp?65:dragon.distanceTo(target),dragon.getHealth()<dragon.getMaxHealth()*.5f,dragon.getRandom().nextInt(2),
                DragonFlight.preferredAttack(dragon.position(),dragon.getDeltaMovement(),threats));
        if (changed && cycle.stage()==Stage.WINDUP) {
            // Lead is capped, then frozen throughout the telegraph and release.
            Vec3 lead=DragonFlight.limit(target.getKnownMovement().scale(10),10);
            aim=target.position().add(lead).add(0,.6,0);
            committed=unit(aim.subtract(dragon.position()));
            side=unit(new Vec3(-committed.z,0,committed.x));
            windupHealth=dragon.getHealth();
            if (cycle.attack()==Attack.BREATH_SWEEP)
                breathRun=DragonBreathRun.plan(dragon.position(),aim,target.getKnownMovement(),++breathPass);
            dragon.playSound(SoundEvents.ENDER_DRAGON_GROWL,2,.8f);
        }
        switch(cycle.stage()) {
            case APPROACH -> {
                Vec3 offset=flight.approach(dragon.position(),dragon.getDeltaMovement(),threats,now);
                move(dragon,offset,Math.min(1.15,.65+offset.length()/200));
            }
            case WINDUP -> {
                move(dragon,aim.subtract(dragon.position()),.95);
                if (cycle.age()==0) telegraph(world,dragon);
            }
            case ATTACK -> {
                if (cycle.age()==0) {
                    // Reacquire once at release. This fixes the two-second-old target problem
                    // without turning either a swoop or a projectile into homing behavior.
                    Vec3 releaseAim=target.position().add(DragonFlight.limit(target.getKnownMovement().scale(6),8)).add(0,.5,0);
                    committed=unit(releaseAim.subtract(dragon.head.position()));
                    aim=releaseAim;
                    dragon.playSound(SoundEvents.ENDER_DRAGON_FLAP,2,.7f);
                }
                switch(cycle.attack()) {
                    case SWOOP -> {
                        move(dragon,committed,1.35); // No re-aiming after commitment.
                        if (cycle.age()%6==0)
                            DragonWakeService.emitUpdraft(world,dragon.getUUID(),dragon.position(),now);
                    }
                    case BREATH_SWEEP -> {
                        move(dragon,breathRun.direction(),1.05);
                        if (cycle.age()%12==0 && cycle.age()<36 && projectiles.size()<3) {
                            Vec3 impact=breathRun.impact(target.position(),target.getKnownMovement(),cycle.age()/12);
                            Vec3 from=dragon.head.position().add(0,1,0);
                            DragonFireball shot=new DragonFireball(world,dragon,unit(impact.subtract(from)));
                            shot.setPos(from.x,from.y,from.z);
                            if (world.addFreshEntity(shot)) projectiles.add(new Shot(shot,now+120));
                        }
                    }
                    case WING_GUST -> {
                        move(dragon,committed.add(0,.15,0),.65);
                        if (cycle.age()==0) gust(world,dragon,target,now);
                    }
                }
            }
            case EXHAUSTED -> {
                // A slow, shallow descent brings the head within reach without diving into the ground.
                double descent=Math.clamp((target.getY()+5-dragon.getY())*.03,-.3,.15);
                move(dragon,new Vec3(committed.x,descent,committed.z),.4);
                if (cycle.age()==0) dragon.playSound(SoundEvents.ENDER_DRAGON_HURT,1.5f,.65f);
            }
            case RECOVER -> move(dragon,committed.add(0,.03,0),.9);
        }
    }
    private void telegraph(ServerLevel world,EnderDragon dragon) {
        // Physical windup cues only: no action bar, chat instructions or projected attack paths.
        if (cycle.attack()==Attack.BREATH_SWEEP) {
            world.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH,1.0f),
                    dragon.head.getX(),dragon.head.getY()+1,dragon.head.getZ(),8,.6,.3,.6,.03);
        } else if (cycle.attack()==Attack.WING_GUST) {
            dragon.playSound(SoundEvents.ENDER_DRAGON_FLAP,2,.6f);
            for (int sign:new int[]{-1,1}) {
                Vec3 wing=dragon.position().add(side.scale(sign*5)).add(0,2,0);
                world.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH,1.0f),
                        wing.x,wing.y,wing.z,6,1,.5,1,.04);
            }
        } else {
            if (cycle.age()==0) dragon.playSound(SoundEvents.ENDER_DRAGON_FLAP,2,.8f);
        }
    }
    private void gust(ServerLevel world,EnderDragon dragon,ServerPlayer target,long now) {
        Vec3 draftCenter=target.position().add(0,8,0);
        DragonWakeService.emitDowndraft(world,dragon.getUUID(),draftCenter,now);
        world.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH,1.0f),
                draftCenter.x,draftCenter.y,draftCenter.z,14,4,7,4,.08);
        Vec3 forward=unit(new Vec3(committed.x,0,committed.z));
        for (ServerPlayer player:world.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            Vec3 delta=player.position().subtract(dragon.position());
            if (delta.lengthSqr()>24*24 || Math.abs(delta.y)>16
                    || unit(new Vec3(delta.x,0,delta.z)).dot(forward)<.35 || !dragon.hasLineOfSight(player)) continue;
            if (!player.isCreative()) player.hurtServer(world,dragon.damageSources().mobAttack(dragon),4);
            Vec3 push=unit(new Vec3(delta.x,0,delta.z)).scale(.65);
            player.push(push.x,.22,push.z); player.hurtMarked=true;
        }
    }
    private void sphericalGust(ServerLevel world,EnderDragon dragon,long now) {
        Vec3 center=dragon.position();
        double radiusSq=DragonSphericalGust.RADIUS*DragonSphericalGust.RADIUS;
        EnderDragonsConfig config=EnderDragonManager.getConfig();
        double impulse=config==null?DragonSphericalGust.DEFAULT_IMPULSE_STRENGTH:config.sphericalGustImpulseStrength;
        double minimum=config==null?DragonSphericalGust.DEFAULT_MINIMUM_OUTWARD_SPEED:config.sphericalGustMinimumOutwardSpeed;
        double maximum=config==null?DragonSphericalGust.DEFAULT_MAXIMUM_RADIAL_SPEED:config.sphericalGustMaximumRadialSpeed;
        for (ServerPlayer player:world.players()) {
            if (!player.isAlive() || player.isSpectator() || player.position().distanceToSqr(center)>radiusSq) continue;
            Vec3 velocity=DragonSphericalGust.velocity(center,player.position(),player.getKnownMovement(),
                    impulse,minimum,maximum);
            player.setDeltaMovement(velocity);
            player.hurtMarked=true;
            DragonGustFallProtection.grant(player,now);
        }
        // A Fibonacci sphere gives an even radial burst with a fixed, event-only packet count.
        final int particles=42;
        final double goldenAngle=Math.PI*(3-Math.sqrt(5));
        for (int i=0;i<particles;i++) {
            double y=1-2*(i+.5)/particles;
            double ring=Math.sqrt(Math.max(0,1-y*y));
            double angle=i*goldenAngle;
            Vec3 direction=new Vec3(Math.cos(angle)*ring,y,Math.sin(angle)*ring);
            world.sendParticles(ParticleTypes.CLOUD,center.x,center.y,center.z,0,
                    direction.x,direction.y,direction.z,1.15);
        }
        dragon.playSound(SoundEvents.ENDER_DRAGON_FLAP,3,.55f);
    }
    private static Vec3 unit(Vec3 direction) { return direction.lengthSqr()<1e-9?new Vec3(1,0,0):direction.normalize(); }
    private static boolean airborne(ServerPlayer player) { return player.isFallFlying() || player.getAbilities().flying; }
    private void move(EnderDragon dragon,Vec3 direction,double speed) {
        Vec3 velocity=DragonFlight.steer(dragon.getDeltaMovement(),direction,speed,flight.bank());
        dragon.setDeltaMovement(velocity);
        dragon.setFightOrigin(net.minecraft.core.BlockPos.containing(dragon.position().add(unit(velocity).scale(80))));
    }
}
