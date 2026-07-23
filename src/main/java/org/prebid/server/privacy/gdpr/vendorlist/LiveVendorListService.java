package org.prebid.server.privacy.gdpr.vendorlist;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.Getter;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LiveVendorListService implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(LiveVendorListService.class);

    private final String cacheDir;
    private final String liveGvlUrl;
    private final long refreshPeriodMs;
    private final int defaultTimeoutMs;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private final VendorListFileStore vendorListFileStore;
    private final Metrics metrics;
    private final JacksonMapper mapper;
    private final Clock clock;

    @Getter
    private volatile Set<Integer> deletedVendorIds = Set.of();

    public LiveVendorListService(String cacheDir,
                                 String liveGvlUrl,
                                 long refreshPeriodMs,
                                 int defaultTimeoutMs,
                                 Vertx vertx,
                                 HttpClient httpClient,
                                 VendorListFileStore vendorListFileStore,
                                 Metrics metrics,
                                 JacksonMapper mapper,
                                 Clock clock) {

        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.liveGvlUrl = HttpUtil.validateUrl(Objects.requireNonNull(liveGvlUrl));
        this.refreshPeriodMs = refreshPeriodMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vendorListFileStore = Objects.requireNonNull(vendorListFileStore);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Future<Void> initialize() {
        initializeWithLatestCachedVersion();
        vertx.setPeriodic(0, refreshPeriodMs, ignored -> refresh());
        return Future.succeededFuture();
    }

    private void initializeWithLatestCachedVersion() {
        vendorListFileStore.getLatestVendorListFromCache(cacheDir).ifPresent(vendorList -> {
            saveDeletedVendorsFromVendorList(vendorList);
            logger.info("Initialized live GVL from cache with version %d".formatted(vendorList.getVendorListVersion()));
        });
    }

    void refresh() {
        httpClient.get(liveGvlUrl, defaultTimeoutMs)
                .map(this::processResponse)
                .map(this::saveDeletedVendorsFromVendorList)
                .otherwise(this::handleError);
    }

    private Void saveDeletedVendorsFromVendorList(VendorList vendorList) {
        updateDeletedVendorIds(extractDeletedVendorIds(vendorList));
        return null;
    }

    private VendorList processResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException("HTTP status code " + statusCode);
        }

        final String body = response.getBody();
        final VendorList vendorList = VendorListUtil.parseVendorList(body, mapper);

        if (!VendorListUtil.vendorListIsValid(vendorList)) {
            throw new PreBidException("Fetched vendor list parsed but has invalid data: " + body);
        }

        return vendorList;
    }

    Set<Integer> extractDeletedVendorIds(VendorList vendorList) {
        final Instant now = clock.instant();
        return vendorList.getVendors().values().stream()
                .filter(vendor -> VendorListUtil.vendorIsDeletedAt(vendor, now))
                .map(Vendor::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Void updateDeletedVendorIds(Set<Integer> ids) {
        deletedVendorIds = ids;
        metrics.updatePrivacyTcfLiveVendorListOkMetric();
        return null;
    }

    private Void handleError(Throwable exception) {
        logger.warn("Error occurred while fetching live GVL", exception);
        metrics.updatePrivacyTcfLiveVendorListErrorMetric();
        return null;
    }
}
