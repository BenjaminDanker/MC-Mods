package com.silver.aipets.service;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.service.config.PetServiceConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone process entry point. Schema migrations remain an operator-controlled action. */
public final class PetServiceApplication {
    private static final System.Logger LOGGER =
            System.getLogger(PetServiceApplication.class.getName());

    private PetServiceApplication() {
    }

    public static void main(String[] arguments) throws Exception {
        PetServiceConfig config = PetServiceConfig.fromEnvironment(System.getenv());
        PetServiceRuntime runtime = PetServiceRuntime.create(config);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean shutdownStarted = new AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shutdownStarted.compareAndSet(false, true)) {
                LOGGER.log(System.Logger.Level.INFO, StructuredPetEvent
                        .operation("service_shutdown").outcome("started").toJson());
                runtime.close();
            }
            stopped.countDown();
        }, "pet-service-shutdown"));

        runtime.start();
        LOGGER.log(
                System.Logger.Level.INFO,
                StructuredPetEvent.operation("service_startup").outcome("listening").toJson());
        try {
            stopped.await();
        } catch (InterruptedException interrupted) {
            if (shutdownStarted.compareAndSet(false, true)) {
                runtime.close();
            }
            Thread.currentThread().interrupt();
        }
    }
}
