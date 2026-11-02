package com.silver.skyislands.enderdragons;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;

/** Bounded world-space attack drafts. No entity or chunk references are retained. */
public final class DragonWakeField {
    public static final int LIFETIME=600, MAX_SEGMENTS=192;
    public record Segment(UUID owner,DragonDraft draft,long born) {
        public double fade(long now) { return Math.clamp((born+LIFETIME-now)/200.0,0,1); }
    }
    private final ArrayDeque<Segment> segments=new ArrayDeque<>();
    public void expire(long now) { segments.removeIf(s -> now-s.born()>=LIFETIME); }
    public int size() { return segments.size(); }

    public boolean emit(UUID owner,DragonDraft draft,long now) {
        expire(now);
        var iterator=segments.descendingIterator();
        while(iterator.hasNext()) {
            Segment previous=iterator.next();
            if (!previous.owner().equals(owner) || previous.draft().kind()!=draft.kind()) continue;
            if (now-previous.born()<6 || draft.center().distanceToSqr(previous.draft().center())<9*9) return false;
            break;
        }
        if (segments.size()==MAX_SEGMENTS) segments.removeFirst();
        segments.addLast(new Segment(owner,draft,now));
        return true;
    }

    public Vec3 sample(Vec3 position,long now,Predicate<Segment> accessible) {
        Vec3 strongest=Vec3.ZERO;
        for(Segment segment:segments) {
            Vec3 acceleration=segment.draft().sample(position).scale(segment.fade(now));
            // A newer equally strong attack draft supersedes an older overlapping one.
            if (Math.abs(acceleration.y)>=Math.abs(strongest.y) && accessible.test(segment)) strongest=acceleration;
        }
        return strongest;
    }

    public List<Segment> nearest(Vec3 position,long now) {
        return segments.stream().filter(s -> s.fade(now)>.05 && s.draft().center().distanceToSqr(position)<96*96)
                .sorted(java.util.Comparator.comparingDouble(s -> s.draft().center().distanceToSqr(position)))
                .limit(6).toList();
    }
}
