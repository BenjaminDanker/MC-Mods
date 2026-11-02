package com.silver.aipets.service.mood;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** One bounded background pass per minute; never runs on a Minecraft tick. */
public final class PetMoodDecayScheduler implements AutoCloseable {
    private final JdbcPetMoodDecayService service;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(
            task -> { Thread thread=new Thread(task,"pet-service-mood-decay");
                thread.setDaemon(false); return thread; });
    public PetMoodDecayScheduler(JdbcPetMoodDecayService service) { this.service=service; }
    public void start() {
        executor.scheduleWithFixedDelay(() -> {
            try { service.decayDue(Instant.now(),100); }
            catch (RuntimeException failure) {
                System.getLogger(PetMoodDecayScheduler.class.getName()).log(
                        System.Logger.Level.WARNING,"Mood decay will retry",failure);
            }
        },0,Duration.ofMinutes(1).toMillis(),TimeUnit.MILLISECONDS);
    }
    @Override public void close() { executor.shutdownNow(); }
}
