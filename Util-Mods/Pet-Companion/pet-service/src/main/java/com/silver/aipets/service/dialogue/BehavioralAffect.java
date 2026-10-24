package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.PetTraits;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Bounded behavioral attention/approach state. Alertness is only vigilance/readiness. */
public record BehavioralAffect(int valence, int valenceIntensity, int alertness,
        int alertnessIntensity, int socialStance, int socialIntensity,
        String trigger, UUID target, Instant updatedAt, UUID eventId) {
    public BehavioralAffect(int valence, int valenceIntensity, int alertness,
            int alertnessIntensity, int socialStance, int socialIntensity,
            String trigger, UUID target, Instant updatedAt) {
        this(valence, valenceIntensity, alertness, alertnessIntensity, socialStance,
                socialIntensity, trigger, target, updatedAt, null);
    }
    public BehavioralAffect {
        valence = clamp(valence, -100, 100);
        alertness = clamp(alertness, 0, 100);
        socialStance = clamp(socialStance, -100, 100);
        valenceIntensity = clamp(valenceIntensity, 0, 100);
        alertnessIntensity = clamp(alertnessIntensity, 0, 100);
        socialIntensity = clamp(socialIntensity, 0, 100);
        trigger = trigger == null ? "NONE" : trigger.substring(0, Math.min(64, trigger.length()));
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
    public static BehavioralAffect neutral(Instant at) {
        return new BehavioralAffect(0, 0, 0, 0, 0, 0, "NONE", null, at);
    }
    public BehavioralAffect apply(DialogueAffect affect, RelationshipSignal signal,
            PetTraits traits, double confidence, UUID relatedOwner, Instant at) {
        return apply(affect, 1, signal, traits, confidence, relatedOwner, null, at);
    }
    public BehavioralAffect apply(DialogueAffect affect, int strength, RelationshipSignal signal,
            PetTraits traits, double confidence, UUID relatedOwner, UUID causeEvent, Instant at) {
        Objects.requireNonNull(affect); Objects.requireNonNull(signal);
        Objects.requireNonNull(traits); Objects.requireNonNull(at);
        if (causeEvent != null && causeEvent.equals(eventId)) return this;
        BehavioralAffect base = decay(at);
        double evidence = Math.max(0, Math.min(1, confidence));
        int intensity = clamp(strength, 0, 2);
        double temperament = Math.max(.45, Math.min(1.35,
                .75 + (traits.value(com.silver.aipets.common.domain.TraitName.SECURITY) - 50) / 200.0));
        int dv = 0, da = 0, ds = 0;
        switch (affect) {
            case CALMER -> { dv = 6; da = -3; ds = 3; }
            case BRIGHTER -> { dv = 5; da = 2; ds = 2; }
            case WARY -> { dv = -4; da = 5; ds = -6; }
            case TIRED -> { da = -5; }
            case NONE -> { }
        }
        dv *= intensity;
        da *= intensity;
        ds *= intensity;
        int magnitude = signal.strength();
        switch (signal.type()) {
            case WARMTH, REPAIR -> { dv += 5 * magnitude; ds += 4 * magnitude; }
            case TRUST -> ds += 5 * magnitude;
            case BOUNDARY, DISTANCE -> ds -= 7 * magnitude;
            case IRRITATION -> { dv -= 5 * magnitude; ds -= 4 * magnitude; }
            case FEAR -> { dv -= 8 * magnitude; da += 8 * magnitude; ds -= 9 * magnitude; }
            case CURIOSITY -> da += 4 * magnitude;
            case NONE -> { }
        }
        // Habituation: the same pleasant stimulus again soon is worth less each time (a tenth
        // "good boy" lands softer than the first). Threats are deliberately excluded; repeated
        // harm sensitizes instead of fading.
        String incoming = affect != DialogueAffect.NONE ? affect.name() : signal.type().name();
        if (pleasant(affect, signal) && incoming.equals(base.trigger)
                && Duration.between(updatedAt, at).compareTo(HABITUATION_WINDOW) < 0) {
            dv = (int) Math.round(dv * HABITUATION_FACTOR);
            da = (int) Math.round(da * HABITUATION_FACTOR);
            ds = (int) Math.round(ds * HABITUATION_FACTOR);
        }
        dv = (int) Math.round(dv * temperament * evidence);
        da = (int) Math.round(da * Math.max(.65, temperament) * evidence);
        ds = (int) Math.round(ds * evidence);
        return new BehavioralAffect(clamp(base.valence + dv, -100, 100),
                updateIntensity(base.valenceIntensity, dv), clamp(base.alertness + da, 0, 100),
                updateIntensity(base.alertnessIntensity, da), clamp(base.socialStance + ds, -100, 100),
                updateIntensity(base.socialIntensity, ds), affect != DialogueAffect.NONE ? affect.name() : signal.type().name(),
                signal.meaningful() ? relatedOwner : base.target, at, causeEvent);
    }
    private static final Duration HABITUATION_WINDOW = Duration.ofHours(2);
    private static final double HABITUATION_FACTOR = .6;

    private static boolean pleasant(DialogueAffect affect, RelationshipSignal signal) {
        return affect == DialogueAffect.BRIGHTER || affect == DialogueAffect.CALMER
                || affect == DialogueAffect.NONE && (signal.type() == RelationshipSignal.Type.WARMTH
                        || signal.type() == RelationshipSignal.Type.TRUST
                        || signal.type() == RelationshipSignal.Type.CURIOSITY);
    }

    public BehavioralAffect decay(Instant at) {
        if (!at.isAfter(updatedAt)) return this;
        double elapsed = Duration.between(updatedAt, at).toMillis() / 1000d;
        return new BehavioralAffect(decayValue(valence, elapsed, 24), decayValue(valenceIntensity, elapsed, 24),
                decayValue(alertness, elapsed, 2), decayValue(alertnessIntensity, elapsed, 2),
                decayValue(socialStance, elapsed, 24 * 7), decayValue(socialIntensity, elapsed, 24 * 7),
                trigger, target, at, eventId);
    }
    private static int updateIntensity(int prior, int delta) { return clamp(prior + Math.abs(delta), 0, 100); }
    private static int decayValue(int value, double elapsedSeconds, double halfLifeHours) {
        return (int) Math.round(value * Math.pow(.5, elapsedSeconds / (halfLifeHours * 3600d)));
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
