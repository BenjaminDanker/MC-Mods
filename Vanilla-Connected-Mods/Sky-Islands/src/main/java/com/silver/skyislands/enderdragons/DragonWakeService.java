package com.silver.skyislands.enderdragons;

import java.util.HashMap;
import java.util.UUID;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Persistent vertical currents emitted only by swoops and wing-gust attacks. */
public final class DragonWakeService {
    private static final HashMap<ServerLevel,DragonWakeField> fields=new HashMap<>();
    private DragonWakeService() {}
    public static void clear() { fields.clear(); }
    public static void emitUpdraft(ServerLevel world,UUID owner,Vec3 position,long now) {
        fields.computeIfAbsent(world,w -> new DragonWakeField()).emit(owner,DragonDraft.updraft(position),now);
    }
    public static void emitDowndraft(ServerLevel world,UUID owner,Vec3 position,long now) {
        fields.computeIfAbsent(world,w -> new DragonWakeField()).emit(owner,DragonDraft.downdraft(position),now);
    }

    public static void tick(MinecraftServer server) {
        var iterator=fields.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            ServerLevel world=entry.getKey();
            DragonWakeField field=entry.getValue();
            long now=world.getGameTime();
            field.expire(now);
            if (field.size()==0) { iterator.remove(); continue; }
            if (now%2!=0) continue;
            for(ServerPlayer player:world.players()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                apply(world,player,field,now);
                if (now%10==0) render(world,player,field,now);
            }
        }
    }

    private static void apply(ServerLevel world,ServerPlayer player,DragonWakeField field,long now) {
        boolean sheltered=EnderDragonManager.isSheltered(world,player.position());
        Vec3 impulse=field.sample(player.position(),now,s ->
                (!sheltered || world.getEntity(s.owner()) instanceof DragonProvokedAccess access && access.skyIslands$isProvoked())
                        && clearPath(world,player,s.draft().center()));
        if (impulse.lengthSqr()<1e-6) return;
        if (impulse.y<0 && !hasDownwardClearance(world,player)) return;
        Vec3 movement=DragonDraft.applyVertical(player.getKnownMovement(),impulse.y);
        if (movement.y==player.getKnownMovement().y) return;
        player.setDeltaMovement(movement);
        player.hurtMarked=true;
        if (impulse.y<0) player.resetFallDistance();
    }

    private static boolean hasDownwardClearance(ServerLevel world,ServerPlayer player) {
        return !player.onGround() && world.noCollision(player,player.getBoundingBox().move(0,-3,0));
    }
    private static void render(ServerLevel world,ServerPlayer player,DragonWakeField field,long now) {
        for(var segment:field.nearest(player.position(),now)) {
            Vec3 center=segment.draft().center();
            int vertical=segment.draft().kind()==DragonDraft.Kind.UPDRAFT?1:-1;
            double fade=segment.fade(now);
            world.sendParticles(player,PowerParticleOption.create(ParticleTypes.DRAGON_BREATH,1.0f),true,false,
                    center.x,center.y,center.z,3,5,8,5,.055*vertical*fade);
        }
    }

    private static boolean clearPath(ServerLevel world,ServerPlayer player,Vec3 origin) {
        Vec3 end=player.getEyePosition();
        if (origin.distanceToSqr(end)>48*48) return false;
        int minX=((int)Math.floor(Math.min(origin.x,end.x)))>>4,maxX=((int)Math.floor(Math.max(origin.x,end.x)))>>4;
        int minZ=((int)Math.floor(Math.min(origin.z,end.z)))>>4,maxZ=((int)Math.floor(Math.max(origin.z,end.z)))>>4;
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) if (!world.hasChunk(x,z)) return false;
        return world.clip(new ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
    }
}
