package org.prebid.server.analytics.reporter.agma;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iabtcf.decoder.TCString;
import com.iabtcf.utils.IntIterable;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.agma.model.AgmaAnalyticsProperties;
import org.prebid.server.analytics.reporter.agma.model.AgmaEvent;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

public class AgmaAnalyticsReporter implements AnalyticsReporter {

    private static final Logger logger = LoggerFactory.getLogger(AgmaAnalyticsReporter.class);

    private final String url;
    private final boolean compressToGzip;
    private final long httpTimeoutMs;
    private final long maxBufferSize;
    private final long maxEventCount;
    private final long bufferTimeoutMs;
    private final Map<String, String> accounts;

    private final Vertx vertx;
    private final JacksonMapper jacksonMapper;
    private final HttpClient httpClient;
    private final Clock clock;

    private final ReentrantLock lockOnSend;
    private final AtomicReference<Queue<String>> events;
    private final MultiMap headers;
    private final AtomicLong byteSize;
    private volatile long reportTimerId;

    public AgmaAnalyticsReporter(AgmaAnalyticsProperties agmaAnalyticsProperties,
                                 PrebidVersionProvider prebidVersionProvider,
                                 JacksonMapper jacksonMapper,
                                 Clock clock,
                                 HttpClient httpClient,
                                 Vertx vertx) {

        this.accounts = agmaAnalyticsProperties.getAccounts();

        this.url = HttpUtil.validateUrl(agmaAnalyticsProperties.getUrl());
        this.httpTimeoutMs = agmaAnalyticsProperties.getHttpTimeoutMs();
        this.compressToGzip = agmaAnalyticsProperties.isGzip();

        this.maxBufferSize = agmaAnalyticsProperties.getBufferSize();
        this.maxEventCount = agmaAnalyticsProperties.getMaxEventsCount();
        this.bufferTimeoutMs = agmaAnalyticsProperties.getBufferTimeoutMs();

        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vertx = Objects.requireNonNull(vertx);
        this.clock = Objects.requireNonNull(clock);

        this.lockOnSend = new ReentrantLock();
        this.events = new AtomicReference<>(new ConcurrentLinkedQueue<>());
        this.headers = makeHeaders(Objects.requireNonNull(prebidVersionProvider));
        this.byteSize = new AtomicLong();
        this.reportTimerId = setBufferTimer();
    }

    @Override
    public <T> Future<Void> processEvent(T event) {
        final Pair<AuctionContext, String> contextAndType = switch (event) {
            case AuctionEvent auctionEvent -> Pair.of(auctionEvent.getAuctionContext(), "auction");
            case AmpEvent ampEvent -> Pair.of(ampEvent.getAuctionContext(), "amp");
            case VideoEvent videoEvent -> Pair.of(videoEvent.getAuctionContext(), "video");
            case null, default -> null;
        };

        if (contextAndType == null) {
            return Future.succeededFuture();
        }

        final AuctionContext auctionContext = contextAndType.getLeft();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final TimeoutContext timeoutContext = auctionContext.getTimeoutContext();
        final PrivacyContext privacyContext = auctionContext.getPrivacyContext();

        if (!allowedToSendEvent(bidRequest, privacyContext)) {
            return Future.succeededFuture();
        }

        final String accountCode = Optional.ofNullable(bidRequest)
                .map(AgmaAnalyticsReporter::getPublisherId)
                .map(accounts::get)
                .orElse(null);

        if (accountCode == null) {
            return Future.succeededFuture();
        }

        final AgmaEvent agmaEvent = AgmaEvent.builder()
                .eventType(contextAndType.getRight())
                .accountCode(accountCode)
                .requestId(bidRequest.getId())
                .app(bidRequest.getApp())
                .site(bidRequest.getSite())
                .device(bidRequest.getDevice())
                .user(bidRequest.getUser())
                .startTime(ZonedDateTime.ofInstant(
                        Instant.ofEpochMilli(timeoutContext.getStartTime()), clock.getZone()))
                .build();

        buffer(agmaEvent);
        sendEventsOnCondition(byteSize -> byteSize.get() > maxBufferSize, byteSize);
        sendEventsOnCondition(eventsReference -> eventsReference.get().size() > maxEventCount, events);

        return Future.succeededFuture();
    }

    private boolean allowedToSendEvent(BidRequest bidRequest, PrivacyContext privacyContext) {
        final TCString consent = Optional.ofNullable(privacyContext)
                .map(PrivacyContext::getTcfContext)
                .map(TcfContext::getConsent)
                .or(() -> Optional.ofNullable(bidRequest.getUser())
                        .map(User::getExt)
                        .map(ExtUser::getConsent)
                        .map(AgmaAnalyticsReporter::decodeConsent))
                .orElse(null);

        if (consent == null) {
            return false;
        }

        final IntIterable purposesConsent = consent.getPurposesConsent();
        final IntIterable vendorConsent = consent.getVendorConsent();

        final boolean isPurposeAllowed = purposesConsent.contains(PurposeCode.NINE.code());
        final boolean isVendorAllowed = vendorConsent.contains(vendorId());
        return isPurposeAllowed && isVendorAllowed;
    }

    private static TCString decodeConsent(String consent) {
        try {
            return TCString.decode(consent);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String getPublisherId(BidRequest bidRequest) {
        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();

        final String publisherId = Optional.ofNullable(site).map(Site::getPublisher).map(Publisher::getId)
                .or(() -> Optional.ofNullable(app).map(App::getPublisher).map(Publisher::getId))
                .orElse(null);
        final String appSiteId = Optional.ofNullable(site).map(Site::getId)
                .or(() -> Optional.ofNullable(app).map(App::getId))
                .orElse(null);

        if (publisherId == null && appSiteId == null) {
            return null;
        }

        return publisherId;
    }

    private <T> void buffer(T event) {
        final String jsonEvent = jacksonMapper.encodeToString(event);
        events.get().add(jsonEvent);
        byteSize.getAndAdd(jsonEvent.getBytes().length);
    }

    private <T> boolean sendEventsOnCondition(Predicate<T> conditionToSend, T conditionValue) {
        boolean requestWasSent = false;
        if (conditionToSend.test(conditionValue)) {
            lockOnSend.lock();
            try {
                if (conditionToSend.test(conditionValue)) {
                    requestWasSent = true;
                    sendEvents(events);
                }
            } catch (Exception exception) {
                logger.error("[agmaAnalytics] Failed to send analytics report to endpoint {} with a reason {}",
                        url, exception.getMessage());
            } finally {
                lockOnSend.unlock();
            }
        }
        return requestWasSent;
    }

    private void sendEvents(AtomicReference<Queue<String>> events) {
        final Queue<String> copyToSend = events.getAndSet(new ConcurrentLinkedQueue<>());

        resetReportEventsConditions();

        final String payload = preparePayload(copyToSend);
        final Future<HttpClientResponse> responseFuture = compressToGzip
                ? httpClient.request(HttpMethod.POST, url, headers, gzip(payload), httpTimeoutMs)
                : httpClient.request(HttpMethod.POST, url, headers, payload, httpTimeoutMs);

        responseFuture.onComplete(this::handleReportResponse);
    }

    private void resetReportEventsConditions() {
        byteSize.set(0);
        vertx.cancelTimer(reportTimerId);
        reportTimerId = setBufferTimer();
    }

    private static String preparePayload(Queue<String> events) {
        return "[" + String.join(",", events) + "]";
    }

    private static byte[] gzip(String value) {
        try (ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj)) {

            gzip.write(value.getBytes(StandardCharsets.UTF_8));
            gzip.finish();

            return obj.toByteArray();
        } catch (IOException e) {
            throw new PreBidException("[agmaAnalytics] failed to compress, skip the events : " + e.getMessage());
        }
    }

    private void handleReportResponse(AsyncResult<HttpClientResponse> result) {
        if (result.failed()) {
            logger.error("[agmaAnalytics] Failed to send events to endpoint {} with a reason: {}",
                    url, result.cause().getMessage());
        } else {
            final HttpClientResponse httpClientResponse = result.result();
            final int statusCode = httpClientResponse.getStatusCode();
            if (statusCode != HttpResponseStatus.OK.code()) {
                logger.error("[agmaAnalytics] Wrong code received {} instead of 200", statusCode);
            }
        }
    }

    private long setBufferTimer() {
        return vertx.setTimer(bufferTimeoutMs, timerId -> sendOnTimer());
    }

    private void sendOnTimer() {
        final boolean requestWasSent = sendEventsOnCondition(events -> !events.get().isEmpty(), events);
        if (!requestWasSent) {
            setBufferTimer();
        }
    }

    private MultiMap makeHeaders(PrebidVersionProvider versionProvider) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpUtil.X_PREBID_HEADER, versionProvider.getNameVersionRecord());

        if (compressToGzip) {
            headers.add(HttpHeaders.CONTENT_ENCODING, HttpHeaderValues.GZIP);
        }

        return headers;
    }

    @Override
    public int vendorId() {
        return 1122;
    }

    @Override
    public String name() {
        return "agmaAnalytics";
    }
}
