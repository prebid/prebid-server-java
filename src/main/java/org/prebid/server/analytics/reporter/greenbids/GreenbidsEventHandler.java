package org.prebid.server.analytics.reporter.greenbids;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.pubstack.PubstackEventHandler;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class GreenbidsEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(PubstackEventHandler.class);
    private static final String SCOPE_FIELD_NAME = "scope";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final String GZIP = "gzip";
    private static final String NEW_LINE = "\n";

    private volatile boolean enabled;
    private volatile String endpoint;
    private volatile String scopeId;
    private final long maxBytesSize;
    private final long maxEventCount;
    private final long reportTtlMillis;
    private final long timeoutMs;
    private final Vertx vertx;
    private final JacksonMapper jacksonMapper;
    private final HttpClient httpClient;
    private final ReentrantLock lockOnSend;
    private final AtomicReference<Queue<String>> events;
    private final AtomicLong bytesSize;
    private volatile long reportTimerId;

    public GreenbidsEventHandler(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            boolean enabled,
            String endpoint,
            JacksonMapper jacksonMapper,
            HttpClient httpClient,
            Vertx vertx
    ) {
        this.enabled = enabled;
        this.endpoint = HttpUtil.validateUrl(endpoint);
        this.scopeId = greenbidsAnalyticsProperties.getScopeId();
        this.maxBytesSize = greenbidsAnalyticsProperties.getSizeBytes();
        this.maxEventCount = greenbidsAnalyticsProperties.getCount();
        this.reportTtlMillis = greenbidsAnalyticsProperties.getReportTtlMs();
        this.timeoutMs = greenbidsAnalyticsProperties.getTimeoutMs();
        this.jacksonMapper  Objects.requireNonNull(jacksonMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vertx = Objects.requireNonNull(vertx);

        this.lockOnSend = new ReentrantLock();
        this.events = new AtomicReference<>(new ConcurrentLinkedQueue<>());
        this.headers = makeHeaders();

    }

    private static
}
