package org.prebid.server.analytics.reporter.pubstack;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.analytics.reporter.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

public class PubstackEventHandler {

    private static final Logger logger = LoggerFactory.getLogger(PubstackEventHandler.class);
    private static final String SCOPE_FIELD_NAME = "scope";
    private static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final String GZIP = "gzip";
    private static final String NEW_LINE = "\n";

    private volatile boolean enabled;
    private volatile String endpoint;
    private volatile String scopeId;
    private final long maxByteSize;
    private final long maxEventCount;
    private final long reportTtlMillis;
    private final long timeoutMs;
    private final Vertx vertx;
    private final JacksonMapper jacksonMapper;
    private final HttpClient httpClient;

    private final ReentrantLock lockOnSend;
    private final AtomicReference<Queue<String>> events;
    private final MultiMap headers;
    private final AtomicLong byteSize;
    private volatile long reportTimerId;

    public PubstackEventHandler(PubstackAnalyticsProperties pubstackAnalyticsProperties,
                                boolean enabled,
                                String endpoint,
                                JacksonMapper jacksonMapper,
                                HttpClient httpClient,
                                Vertx vertx) {
        this.enabled = enabled;
        this.endpoint = HttpUtil.validateUrl(endpoint);
        this.scopeId = pubstackAnalyticsProperties.getScopeId();
        this.maxByteSize = pubstackAnalyticsProperties.getSizeBytes();
        this.maxEventCount = pubstackAnalyticsProperties.getCount();
        this.reportTtlMillis = pubstackAnalyticsProperties.getReportTtlMs();
        this.timeoutMs = pubstackAnalyticsProperties.getTimeoutMs();
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vertx = Objects.requireNonNull(vertx);

        this.lockOnSend = new ReentrantLock();
        this.events = new AtomicReference<>(new ConcurrentLinkedQueue<>());
        this.headers = makeHeaders();
        this.byteSize = new AtomicLong();
        if (enabled) {
            this.reportTimerId = setReportTtlTimer();
        }
    }

    public <T> void handle(T event) {
        if (enabled) {
            buffer(event);
            reportEventsOnCondition(byteSize -> byteSize.get() > maxByteSize, byteSize);
            reportEventsOnCondition(eventsReference -> eventsReference.get().size() > maxEventCount, events);
        }
    }

    public void reportEvents() {
        if (enabled) {
            reportEventsOnCondition(events -> events.get().size() > 0, events);
        }
    }

    public void updateConfig(boolean enabled, String endpoint, String scopeId) {
        updateTimerOnEnabling(enabled);
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.scopeId = scopeId;
    }

    private <T> void buffer(T event) {
        final ObjectNode eventNode = jacksonMapper.mapper().valueToTree(event);
        eventNode.put(SCOPE_FIELD_NAME, scopeId);
        final String jsonEvent = jacksonMapper.encodeToString(eventNode);
        events.get().add(jsonEvent);
        byteSize.getAndAdd(jsonEvent.getBytes().length);
    }

    private <T> boolean reportEventsOnCondition(Predicate<T> conditionToSend, T conditionValue) {
        boolean requestWasSent = false;
        if (conditionToSend.test(conditionValue)) {
            lockOnSend.lock();
            try {
                if (conditionToSend.test(conditionValue)) {
                    requestWasSent = true;
                    sendEvents(events);
                }
            } catch (Exception exception) {
                logger.error("[pubstack] Failed to send analytics report to endpoint {} with a reason {}",
                        endpoint, exception.getMessage());
            } finally {
                lockOnSend.unlock();
            }
        }
        return requestWasSent;
    }

    private void sendEvents(AtomicReference<Queue<String>> events) {
        final String url = HttpUtil.validateUrl(endpoint);
        final Queue<String> copyToSend = events.getAndSet(new ConcurrentLinkedQueue<>());

        resetReportEventsConditions();

        httpClient.request(HttpMethod.POST, url, headers, toGzippedBytes(copyToSend), timeoutMs)
                .onComplete(this::handleReportResponse);
    }

    private void resetReportEventsConditions() {
        byteSize.set(0);
        vertx.cancelTimer(reportTimerId);
        reportTimerId = setReportTtlTimer();
    }

    private static byte[] toGzippedBytes(Queue<String> events) {
        return gzip(String.join(NEW_LINE, events));
    }

    private static byte[] gzip(String value) {
        try (
                ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj)) {

            gzip.write(value.getBytes(StandardCharsets.UTF_8));
            gzip.finish();

            return obj.toByteArray();
        } catch (IOException e) {
            throw new PreBidException("[pubstack] failed to compress, skip the events : " + e.getMessage());
        }
    }

    private void handleReportResponse(AsyncResult<HttpClientResponse> result) {
        if (result.failed()) {
            logger.error("[pubstack] Failed to send events to endpoint {} with a reason: {}",
                    endpoint, result.cause().getMessage());
        } else {
            final HttpClientResponse httpClientResponse = result.result();
            final int statusCode = httpClientResponse.getStatusCode();
            if (statusCode != HttpResponseStatus.OK.code()) {
                logger.error("[pubstack] Wrong code received {} instead of 200", statusCode);
            }
        }
    }

    private long setReportTtlTimer() {
        return vertx.setTimer(reportTtlMillis, timerId -> sendOnTimer());
    }

    private void sendOnTimer() {
        final boolean requestWasSent = reportEventsOnCondition(events -> events.get().size() > 0, events);
        if (!requestWasSent) {
            setReportTtlTimer();
        }
    }

    private void updateTimerOnEnabling(boolean enabled) {
        if (this.enabled && !enabled) {
            vertx.cancelTimer(reportTimerId);
        } else if (!this.enabled && enabled) {
            reportTimerId = setReportTtlTimer();
        }
    }

    private static MultiMap makeHeaders() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_OCTET_STREAM)
                .add(HttpHeaders.CONTENT_ENCODING, GZIP);
    }
}
