package org.prebid.server.hooks.modules.intentiq.identity.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityAuctionResponseHook;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityModule;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityProcessedAuctionRequestHook;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends the module's metrics ({@code modules.module.intentiq-identity.*}) to IntentIQ on a fixed interval, as
 * one POST in the Prometheus text format with a bearer token.
 *
 * <p>Built so that it can never hurt the server: the push runs on a Vert.x timer through the async HTTP
 * client with its own timeout, a push still running makes the next tick skip, and a failed push is
 * dropped (no queue, no retry), so nothing builds up in memory. Values are cumulative; a counter that
 * resets on read (the core's {@code flushingCounter}) is skipped, so the server's own reporters are
 * never disturbed.
 */
public class MetricsPusher {

    private static final Logger logger = LoggerFactory.getLogger(MetricsPusher.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);
    private static final double LOG_SAMPLING_RATE = 0.1;

    private static final String MODULE_PREFIX = "modules.module." + IntentiqIdentityModule.CODE + ".";
    private static final String CUSTOM_PREFIX = "custom.";
    private static final String METRIC_PREFIX = "iiq_pbs_";
    private static final Pattern DPI_SUFFIX = Pattern.compile("^(.*)_(\\d+)$");
    private static final Pattern INVALID_NAME_CHARS = Pattern.compile("[^a-zA-Z0-9_]");
    private static final String[] QUANTILES = {"0.5", "0.95", "0.99"};

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE = "text/plain; version=0.0.4";

    private final MetricRegistry metricRegistry;
    private final IntentiqIdentityMetrics metrics;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private final String url;
    private final String token;
    private final String instance;
    private final long intervalMs;
    private final long timeoutMs;
    private final AtomicBoolean inFlight = new AtomicBoolean();

    public MetricsPusher(MetricRegistry metricRegistry,
                         IntentiqIdentityMetrics metrics,
                         Vertx vertx,
                         HttpClient httpClient,
                         String url,
                         String token,
                         String instance,
                         long intervalMs,
                         long timeoutMs) {

        this.metricRegistry = Objects.requireNonNull(metricRegistry);
        this.metrics = Objects.requireNonNull(metrics);
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.url = Objects.requireNonNull(url);
        this.token = Objects.requireNonNull(token);
        this.instance = Objects.requireNonNull(instance);
        this.intervalMs = intervalMs;
        this.timeoutMs = timeoutMs;
    }

    /** Starts the periodic push; the first one goes out after one interval. Returns this for wiring. */
    public MetricsPusher start() {
        vertx.setPeriodic(intervalMs, ignored -> push());
        return this;
    }

    void push() {
        if (!inFlight.compareAndSet(false, true)) {
            metrics.pushSkipped();
            return;
        }

        final String body;
        try {
            body = render();
        } catch (RuntimeException e) {
            inFlight.set(false);
            metrics.pushError();
            conditionalLogger.warn("IntentIQ metrics push could not build its body: " + e.getMessage(),
                    LOG_SAMPLING_RATE);
            return;
        }

        if (body.isEmpty()) {
            inFlight.set(false);
            return;
        }

        httpClient.post(url, headers(), body, timeoutMs)
                .onComplete(ignored -> inFlight.set(false))
                .onSuccess(response -> {
                    if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                        metrics.pushSuccess();
                    } else {
                        metrics.pushError();
                        conditionalLogger.warn("IntentIQ metrics push answered HTTP " + response.getStatusCode(),
                                LOG_SAMPLING_RATE);
                    }
                })
                .onFailure(throwable -> {
                    metrics.pushError();
                    conditionalLogger.warn("IntentIQ metrics push failed: " + throwable.getMessage(),
                            LOG_SAMPLING_RATE);
                });
    }

    String render() {
        final StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Metric> entry : metricRegistry.getMetrics().entrySet()) {
            final String name = entry.getKey();
            if (name.startsWith(MODULE_PREFIX)) {
                append(body, name.substring(MODULE_PREFIX.length()), entry.getValue());
            }
        }
        return body.toString();
    }

    private void append(StringBuilder body, String moduleName, Metric metric) {
        final SeriesName series = seriesName(moduleName);
        switch (metric) {
            // Only a plain counter: a subclass may reset on read (the core's flushingCounter).
            case Counter counter when counter.getClass() == Counter.class ->
                    line(body, series.name() + "_total", series.labels(null), counter.getCount());
            case Meter meter -> line(body, series.name() + "_total", series.labels(null), meter.getCount());
            case Timer timer -> {
                line(body, series.name() + "_count", series.labels(null), timer.getCount());
                final Snapshot snapshot = timer.getSnapshot();
                final double[] values = {snapshot.getMedian(), snapshot.get95thPercentile(),
                        snapshot.get99thPercentile()};
                for (int i = 0; i < QUANTILES.length; i++) {
                    line(body, series.name() + "_ms", series.labels(QUANTILES[i]),
                            TimeUnit.NANOSECONDS.toMicros((long) values[i]) / 1000.0);
                }
            }
            case Gauge<?> gauge when gauge.getValue() instanceof Number value ->
                    line(body, series.name(), series.labels(null), value);
            default -> {
                // not pushed
            }
        }
    }

    private SeriesName seriesName(String moduleName) {
        String name = moduleName.startsWith(CUSTOM_PREFIX) ? moduleName.substring(CUSTOM_PREFIX.length()) : moduleName;
        name = name.replace(IntentiqIdentityProcessedAuctionRequestHook.CODE, "enrich")
                .replace(IntentiqIdentityAuctionResponseHook.CODE, "report");
        String dpi = null;
        final Matcher matcher = DPI_SUFFIX.matcher(name);
        if (matcher.matches()) {
            name = matcher.group(1);
            dpi = matcher.group(2);
        }
        return new SeriesName(METRIC_PREFIX + INVALID_NAME_CHARS.matcher(name).replaceAll("_"), dpi, instance);
    }

    private static void line(StringBuilder body, String name, String labels, Number value) {
        body.append(name).append(labels).append(' ').append(value).append('\n');
    }

    private MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(AUTHORIZATION_HEADER, "Bearer " + token)
                .add(CONTENT_TYPE_HEADER, CONTENT_TYPE);
    }

    /** Derives the instance label: the configured value, else the HOSTNAME variable, else the host name. */
    public static String resolveInstance(String configured) {
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        final String hostname = System.getenv("HOSTNAME");
        if (StringUtils.isNotBlank(hostname)) {
            return hostname;
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private record SeriesName(String name, String dpi, String instance) {

        String labels(String quantile) {
            final StringBuilder labels = new StringBuilder("{instance=\"").append(escape(instance)).append('"');
            if (dpi != null) {
                labels.append(",dpi=\"").append(dpi).append('"');
            }
            if (quantile != null) {
                labels.append(",quantile=\"").append(quantile).append('"');
            }
            return labels.append('}').toString();
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }
    }
}
