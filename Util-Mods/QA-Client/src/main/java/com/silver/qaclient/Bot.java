package com.silver.qaclient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A player's hands. The scenario thread calls these; every one of them runs on the client thread and goes through the
 * client's own interaction code (Minecraft.startAttack / startUseItem / continueAttack, which call MultiPlayerGameMode),
 * so reach, adventure-mode restrictions and the can-break rules of the held tool apply exactly as for a person.
 */
public final class Bot {
    public final Minecraft mc = Minecraft.getInstance();
    private final Object tickLock = new Object();
    private int shotNo;

    public <T> T call(Supplier<T> s) {
        if (mc.isSameThread()) {
            return s.get();
        }
        CompletableFuture<T> f = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                f.complete(s.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void run(Runnable r) {
        call(() -> {
            r.run();
            return null;
        });
    }

    void tickPulse() {
        Ctl c = ctl;
        if (c != null) {
            try {
                if (c.tick(mc, ++ctlTicks)) {
                    ctl = null;
                    ctlDone = true;
                }
            } catch (Throwable t) {
                Rec.event("ctl_crash", "error", t.toString());
                ctl = null;
                ctlDone = true;
            }
        }
        synchronized (tickLock) {
            tickLock.notifyAll();
        }
    }

    /** A per-tick controller: runs on the client thread at the end of every client tick (keys set now act on the next tick). */
    public interface Ctl {
        /** @param n 1 on the first call; returns true when finished */
        boolean tick(Minecraft mc, int n);
    }

    private volatile Ctl ctl;
    private volatile boolean ctlDone;
    private int ctlTicks;

    /** Runs a controller until it finishes or maxTicks pass; releases every key afterwards. True if it finished by itself. */
    public boolean drive(Ctl c, int maxTicks) {
        ctlDone = false;
        ctlTicks = 0;
        ctl = c;
        long end = Rec.tick + maxTicks;
        while (!ctlDone && Rec.tick < end) {
            synchronized (tickLock) {
                try {
                    tickLock.wait(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        boolean fin = ctlDone;
        ctl = null;
        stopMoving();
        run(() -> mc.options.keyUse.setDown(false));
        return fin;
    }

    /** Client-thread helpers for controllers: the client's own use / attack / pick. */
    public static void useItem(Minecraft mc) {
        mc.startUseItem();
    }

    public static boolean attack(Minecraft mc) {
        return mc.startAttack();
    }

    public static void pick(Minecraft mc) {
        mc.pick(1.0f);
    }

    /** Sets the movement keys (client thread only). */
    public static void keys(Minecraft mc, boolean fwd, boolean back, boolean left, boolean right, boolean jump, boolean sprint) {
        mc.options.keyUp.setDown(fwd);
        mc.options.keyDown.setDown(back);
        mc.options.keyLeft.setDown(left);
        mc.options.keyRight.setDown(right);
        mc.options.keyJump.setDown(jump);
        mc.options.keySprint.setDown(sprint);
    }

    /** Turns the head (client thread only). */
    public static void face(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
    }

    public static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) (Math.toDegrees(Math.atan2(toZ - fromZ, toX - fromX)) - 90.0);
    }

    public record Mob(int id, String type, Vec3 pos, Vec3 eye, float health, boolean hostile) {
    }

    /** Living things around that are not the player: what a person sees. */
    public java.util.List<Mob> mobs(double radius) {
        return call(() -> {
            java.util.List<Mob> out = new java.util.ArrayList<>();
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.LivingEntity le && e != mc.player && le.isAlive()
                    && !(e instanceof net.minecraft.world.entity.player.Player) && e.distanceTo(mc.player) <= radius) {
                    out.add(new Mob(e.getId(), net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(),
                        e.position(), e.getEyePosition(), le.getHealth(), e instanceof net.minecraft.world.entity.monster.Enemy));
                }
            }
            return out;
        });
    }

    public void ticks(int n) {
        long end = Rec.tick + n;
        while (Rec.tick < end) {
            synchronized (tickLock) {
                try {
                    tickLock.wait(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /** Waits until {@code cond} (evaluated on the client thread) holds; false on timeout. */
    public boolean until(BooleanSupplier cond, int maxTicks) {
        long end = Rec.tick + maxTicks;
        while (Rec.tick < end) {
            if (call(cond::getAsBoolean)) {
                return true;
            }
            ticks(1);
        }
        return call(cond::getAsBoolean);
    }

    public boolean inWorld() {
        return call(() -> mc.player != null && mc.level != null);
    }

    public Vec3 pos() {
        return call(() -> mc.player == null ? Vec3.ZERO : mc.player.position());
    }

    public Vec3 eye() {
        return call(() -> mc.player.getEyePosition());
    }

    public BlockState block(BlockPos p) {
        return call(() -> mc.level.getBlockState(p));
    }

    public void select(int slot) {
        run(() -> mc.player.getInventory().setSelectedSlot(slot));
        ticks(2);
    }

    public void look(Vec3 at) {
        run(() -> {
            Vec3 e = mc.player.getEyePosition();
            double dx = at.x - e.x, dy = at.y - e.y, dz = at.z - e.z;
            double h = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
            mc.player.setYRot(yaw);
            mc.player.setXRot(Math.max(-90f, Math.min(90f, pitch)));
            mc.player.yRotO = yaw;
            mc.player.xRotO = mc.player.getXRot();
            mc.pick(1.0f);
        });
        ticks(2);
        run(() -> mc.pick(1.0f));
    }

    public record Aim(String hit, BlockPos pos, Direction face, double dist, String entity) {
    }

    public Aim crosshair() {
        return call(() -> {
            HitResult h = mc.hitResult;
            if (h instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                return new Aim("block", b.getBlockPos(), b.getDirection(), mc.player.getEyePosition().distanceTo(b.getLocation()), null);
            }
            if (h != null && h.getType() == HitResult.Type.ENTITY) {
                return new Aim("entity", null, null, 0, ((net.minecraft.world.phys.EntityHitResult) h).getEntity().getName().getString());
            }
            return new Aim("miss", null, null, 0, null);
        });
    }

    /** Distance from the eye to the nearest point of a block's box (what reach is checked against). */
    public double reachTo(BlockPos p) {
        return call(() -> {
            Vec3 e = mc.player.getEyePosition();
            double x = Math.max(p.getX(), Math.min(e.x, p.getX() + 1));
            double y = Math.max(p.getY(), Math.min(e.y, p.getY() + 1));
            double z = Math.max(p.getZ(), Math.min(e.z, p.getZ() + 1));
            return e.distanceTo(new Vec3(x, y, z));
        });
    }

    public double reachLimit() {
        return call(() -> mc.player.blockInteractionRange());
    }

    /** Aim at the top face centre (or given offset) of a block; true when the crosshair then really is on that block. */
    public boolean aimAtBlock(BlockPos p) {
        Vec3[] spots = {
            Vec3.atCenterOf(p).add(0, 0.45, 0), Vec3.atCenterOf(p), Vec3.atCenterOf(p).add(0, -0.3, 0),
            Vec3.atCenterOf(p).add(0.35, 0.35, 0.35), Vec3.atCenterOf(p).add(-0.35, 0.35, -0.35)
        };
        java.util.List<Vec3> all = new java.util.ArrayList<>(java.util.List.of(spots));
        Vec3 e0 = eye();
        Vec3 c0 = Vec3.atCenterOf(p);
        // then the faces turned towards the eye (a neighbour can shadow the block's middle from the side)
        for (double[] f : new double[][] {{0, 0, -0.49}, {0, 0, 0.49}, {-0.49, 0, 0}, {0.49, 0, 0}, {0, 0.49, 0}, {0, -0.49, 0}}) {
            Vec3 face = c0.add(f[0], f[1], f[2]);
            if (face.distanceToSqr(e0) < c0.distanceToSqr(e0)) {
                for (double[] o : new double[][] {{0, 0, 0}, {0.25, 0.25, 0.25}, {-0.25, -0.25, -0.25}, {0.25, -0.25, 0.25}, {-0.25, 0.25, -0.25}}) {
                    all.add(face.add(f[0] == 0 ? o[0] : 0, f[1] == 0 ? o[1] : 0, f[2] == 0 ? o[2] : 0));
                }
            }
        }
        for (Vec3 s : all) {
            look(s);
            Aim a = crosshair();
            if (a.pos() != null && a.pos().equals(p)) {
                return true;
            }
        }
        return false;
    }

    /** One right-click on whatever the crosshair is over, through the client's own startUseItem. */
    public void rightClick() {
        run(() -> mc.startUseItem());
        ticks(3);
    }

    /** Turns to {@code at} and right-clicks in the same tick, with no settling wait (fast clicking games). */
    public void quickUse(Vec3 at) {
        run(() -> {
            Vec3 e = mc.player.getEyePosition();
            double dx = at.x - e.x, dy = at.y - e.y, dz = at.z - e.z;
            double h = Math.sqrt(dx * dx + dz * dz);
            mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));
            mc.pick(1.0f);
            mc.startUseItem();
        });
    }

    /** Starts a dig (or swing) through the client's own startAttack without waiting; true if the client began one. */
    public boolean startDig() {
        return call(() -> mc.startAttack());
    }

    /** One left-click (start of a dig or a swing) through the client's own startAttack. */
    public boolean leftClick() {
        boolean r = call(() -> mc.startAttack());
        ticks(2);
        return r;
    }

    /** Holds the attack button for up to maxTicks, or until {@code done} holds; returns ticks held. */
    public int hold(BooleanSupplier done, int maxTicks) {
        long start = Rec.tick;
        run(() -> mc.startAttack());
        while (Rec.tick - start < maxTicks) {
            if (call(done::getAsBoolean)) {
                break;
            }
            run(() -> mc.continueAttack(true));
            ticks(1);
        }
        run(() -> {
            mc.continueAttack(false);
            mc.gameMode.stopDestroyBlock();
        });
        return (int) (Rec.tick - start);
    }

    public void key(net.minecraft.client.KeyMapping k, boolean down) {
        run(() -> k.setDown(down));
    }

    public void stopMoving() {
        run(() -> {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
            mc.options.keySprint.setDown(false);
        });
    }

    /** Walks (forward key, facing the target) until within {@code r} blocks horizontally; jumps when blocked. */
    public boolean walkTo(double x, double z, double r, int maxTicks) {
        long start = Rec.tick;
        Vec3 last = pos();
        int stuck = 0;
        while (Rec.tick - start < maxTicks) {
            Vec3 p = pos();
            double d = Math.hypot(p.x - x, p.z - z);
            if (d <= r) {
                stopMoving();
                return true;
            }
            run(() -> {
                double dx = x - mc.player.getX(), dz = z - mc.player.getZ();
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                mc.player.setYRot(yaw);
                mc.player.setXRot(10f);
                mc.options.keyUp.setDown(true);
            });
            ticks(2);
            Vec3 q = pos();
            if (q.distanceTo(last) > 3) {
                // Teleported (door, room start): stop walking, the target is meaningless now.
                stopMoving();
                return true;
            }
            if (q.distanceTo(last) < 0.1) {
                stuck++;
                if (stuck >= 5) {
                    run(() -> mc.options.keyJump.setDown(true));
                }
                if (stuck >= 30) {
                    stopMoving();
                    return false;
                }
            } else {
                stuck = 0;
                run(() -> mc.options.keyJump.setDown(false));
            }
            last = q;
        }
        stopMoving();
        return false;
    }

    public void chat(String text) {
        run(() -> {
            if (text.startsWith("/")) {
                mc.player.connection.sendCommand(text.substring(1));
            } else {
                mc.player.connection.sendChat(text);
            }
        });
        Rec.event("sent_chat", "text", text);
        ticks(3);
    }

    /** Screenshot of the rendered frame (HUD included) to dir/shots/NN-name.png; returns the relative path. */
    public String shot(String name) {
        String file = String.format("%03d-%s.png", ++shotNo, name.replaceAll("[^A-Za-z0-9_.-]", "_"));
        Path p = Rec.dir.resolve("shots").resolve(file);
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        try {
            Files.createDirectories(p.getParent());
        } catch (Exception ignored) {
        }
        mc.execute(() -> Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), img -> {
            try (img) {
                img.writeToFile(p);
                done.complete(true);
            } catch (Exception e) {
                done.complete(false);
            }
        }));
        try {
            done.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Rec.event("shot_failed", "name", name);
        }
        Rec.event("shot", "file", "shots/" + file, "label", name);
        return "shots/" + file;
    }
}
