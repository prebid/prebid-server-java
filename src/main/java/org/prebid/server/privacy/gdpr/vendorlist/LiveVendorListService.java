package org.prebid.server.privacy.gdpr.vendorlist;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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

    private final String liveGvlUrl;
    private final long refreshPeriodMs;
    private final int defaultTimeoutMs;
    private final Vertx vertx;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
    private final Metrics metrics;
    private final Clock clock;

    private volatile Set<Integer> deletedVendorIds = Set.of();

    public LiveVendorListService(String liveGvlUrl,
                                 long refreshPeriodMs,
                                 int defaultTimeoutMs,
                                 Vertx vertx,
                                 HttpClient httpClient,
                                 JacksonMapper mapper,
                                 Metrics metrics,
                                 Clock clock) {

        this.liveGvlUrl = HttpUtil.validateUrl(Objects.requireNonNull(liveGvlUrl));
        this.refreshPeriodMs = refreshPeriodMs;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean isDeleted(Integer id) {
        final Set<Integer> ids = deletedVendorIds;
        return !ids.isEmpty() && ids.contains(id);
    }

    @Override
    public void initialize(Promise<Void> initializePromise) {
        vertx.setPeriodic(0, refreshPeriodMs, ignored -> refresh());

        initializePromise.tryComplete();
    }

    void refresh() {
        httpClient.get(liveGvlUrl, defaultTimeoutMs)
                .map(this::processResponse)
                .map(this::extractDeletedVendorIds)
                .map(this::updateDeletedVendorIds)
                .otherwise(this::handleError);
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
        metrics.updatePrivacyTcfVendorListLatestOkMetric();
        return null;
    }

    private Void handleError(Throwable exception) {
        logger.warn("Error occurred while fetching live GVL", exception);
        metrics.updatePrivacyTcfVendorListLatestErrorMetric();
        return null;
    }
}
