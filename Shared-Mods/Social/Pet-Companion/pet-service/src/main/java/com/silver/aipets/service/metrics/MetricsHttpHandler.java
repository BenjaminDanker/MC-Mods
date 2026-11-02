package com.silver.aipets.service.metrics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;

/** Records bounded request count, aggregate latency, and error totals around an HTTP handler. */
public final class MetricsHttpHandler implements HttpHandler {
    private final HttpHandler delegate;
    private final PetOperationalMetrics metrics;

    public MetricsHttpHandler(HttpHandler delegate, PetOperationalMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        long started = System.nanoTime();
        try {
            delegate.handle(exchange);
        } finally {
            metrics.recordHttp(System.nanoTime() - started, exchange.getResponseCode());
        }
    }
}
