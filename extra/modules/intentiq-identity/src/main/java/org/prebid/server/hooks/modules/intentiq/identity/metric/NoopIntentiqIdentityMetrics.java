package org.prebid.server.hooks.modules.intentiq.identity.metric;

import java.util.function.LongSupplier;

/**
 * Wired in place of {@link IntentiqIdentityMetrics} when {@code metrics-enabled} is false (see
 * {@code IntentiqIdentityConfig}). Every recording path is a no-op, so callers need no flag check
 * and the shared {@code MetricRegistry} is never touched.
 */
public class NoopIntentiqIdentityMetrics extends IntentiqIdentityMetrics {

    @Override
    protected void inc(String name, String dpi) {
        // no-op
    }

    @Override
    protected void time(String name, String dpi, long timeNanos) {
        // no-op
    }

    @Override
    protected void gauge(String name, LongSupplier supplier) {
        // no-op
    }
}
