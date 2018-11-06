package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CookieSyncHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);

    private static final Set<GdprPurpose> GDPR_PURPOSES =
            Collections.unmodifiableSet(EnumSet.of(GdprPurpose.informationStorageAndAccess));

    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final GdprService gdprService;
    private final Integer gdprHostVendorId;
    private final boolean useGeoLocation;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;

    public CookieSyncHandler(long defaultTimeout, UidsCookieService uidsCookieService, BidderCatalog bidderCatalog,
                             GdprService gdprService, Integer gdprHostVendorId, boolean useGeoLocation,
                             AnalyticsReporter analyticsReporter, Metrics metrics, TimeoutFactory timeoutFactory) {
        this.defaultTimeout = defaultTimeout;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.gdprService = Objects.requireNonNull(gdprService);
        this.gdprHostVendorId = gdprHostVendorId;
        this.useGeoLocation = useGeoLocation;
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
    }

    @Override
    public void handle(RoutingContext context) {
        metrics.updateCookieSyncRequestMetric();

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            final int status = HttpResponseStatus.UNAUTHORIZED.code();
            context.response().setStatusCode(status).setStatusMessage("User has opted out").end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, "user has opted out"));
            return;
        }

        final Buffer body = context.getBody();
        if (body == null) {
            logger.info("Incoming request has no body.");
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            context.response().setStatusCode(status).end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, "request has no body"));
            return;
        }

        final CookieSyncRequest cookieSyncRequest;
        try {
            cookieSyncRequest = Json.decodeValue(body, CookieSyncRequest.class);
        } catch (DecodeException e) {
            logger.info("Failed to parse /cookie_sync request body", e);
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            context.response().setStatusCode(status).setStatusMessage("JSON parse failed").end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, "JSON parse failed"));
            return;
        }

        final Integer gdpr = cookieSyncRequest.getGdpr();
        final String gdprConsent = cookieSyncRequest.getGdprConsent();
        if (Objects.equals(gdpr, 1) && StringUtils.isBlank(gdprConsent)) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            final String message = "gdpr_consent is required if gdpr is 1";
            context.response().setStatusCode(status).setStatusMessage(message).end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, message));
            return;
        }

        final List<String> biddersFromRequest = cookieSyncRequest.getBidders();

        // if bidder list was omitted in request, that means sync should be done for all bidders
        final Collection<String> biddersToSync = biddersFromRequest == null
                ? bidderCatalog.names() : biddersFromRequest;

        final String ip = useGeoLocation ? HttpUtil.ipFrom(context.request()) : null;
        final String gdprAsString = gdpr != null ? gdpr.toString() : null;
        gdprService.resultByVendor(GDPR_PURPOSES, Collections.singleton(gdprHostVendorId), gdprAsString, gdprConsent,
                ip, timeoutFactory.create(defaultTimeout))
                .setHandler(asyncResult -> handleGdprResultForHost(asyncResult, context, uidsCookie, biddersToSync, ip,
                        gdprAsString, gdprConsent));
    }

    /**
     * Handles GDPR verification result for host vendor.
     */
    private void handleGdprResultForHost(AsyncResult<GdprResponse> asyncResultForHost, RoutingContext context,
                                         UidsCookie uidsCookie, Collection<String> biddersToSync, String ip,
                                         String gdpr, String gdprConsent) {
        if (asyncResultForHost.failed()) {
            respondWith(context, uidsCookie, gdpr, gdprConsent, biddersToSync, biddersToSync);
        } else {
            final Set<Integer> vendorIds = gdprVendorIdsFor(biddersToSync);
            gdprService.resultByVendor(GDPR_PURPOSES, vendorIds, gdpr, gdprConsent, ip,
                    timeoutFactory.create(defaultTimeout))
                    .setHandler(asyncResultForBidders -> handleGdprResultForBidders(asyncResultForBidders, context,
                            uidsCookie, biddersToSync, gdpr, gdprConsent));
        }
    }

    /**
     * Handles GDPR verification result for bidders from request.
     */
    private void handleGdprResultForBidders(AsyncResult<GdprResponse> asyncResultForBidders, RoutingContext context,
                                            UidsCookie uidsCookie, Collection<String> biddersToSync,
                                            String gdpr, String gdprConsent) {
        if (asyncResultForBidders.failed()) {
            respondWith(context, uidsCookie, gdpr, gdprConsent, biddersToSync, biddersToSync);
        } else {
            final Set<Integer> vendorIds = asyncResultForBidders.result().getVendorsToGdpr().entrySet().stream()
                    .filter(Map.Entry::getValue) // get bidders passed GDPR verification
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            final Set<String> biddersRejectedByGdpr = biddersToSync.stream()
                    .filter(bidder -> !vendorIds.contains(gdprVendorIdFor(bidder)))
                    .collect(Collectors.toSet());

            respondWith(context, uidsCookie, gdpr, gdprConsent, biddersToSync, biddersRejectedByGdpr);
        }
    }

    /**
     * Fetches GDPR Vendor IDs for given bidders.
     */
    private Set<Integer> gdprVendorIdsFor(Collection<String> bidders) {
        return bidders.stream()
                .map(this::gdprVendorIdFor)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Fetches GDPR Vendor ID for given bidder.
     */
    private Integer gdprVendorIdFor(String bidder) {
        return bidderCatalog.isActive(bidder)
                ? bidderCatalog.metaInfoByName(bidder).info().getGdpr().getVendorId()
                : null;
    }

    /**
     * Make HTTP response for given bidders.
     */
    private void respondWith(RoutingContext context, UidsCookie uidsCookie, String gdpr, String gdprConsent,
                             Collection<String> bidders, Collection<String> biddersRejectedByGdpr) {
        final List<BidderUsersyncStatus> bidderStatuses = bidders.stream()
                .map(bidderName -> bidderStatusFor(bidderName, uidsCookie, biddersRejectedByGdpr, gdpr, gdprConsent))
                .filter(Objects::nonNull) // skip bidder with live Uid
                .collect(Collectors.toList());

        final CookieSyncResponse response = CookieSyncResponse.of(uidsCookie.hasLiveUids() ? "ok" : "no_cookie",
                bidderStatuses);

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));

        analyticsReporter.processEvent(CookieSyncEvent.builder()
                .status(HttpResponseStatus.OK.code())
                .bidderStatus(bidderStatuses)
                .build());
    }

    /**
     * Creates {@link BidderUsersyncStatus} for given bidder.
     */
    private BidderUsersyncStatus bidderStatusFor(String bidderName, UidsCookie uidsCookie,
                                                 Collection<String> biddersRejectedByGdpr, String gdpr,
                                                 String gdprConsent) {
        final BidderUsersyncStatus result;

        if (!bidderCatalog.isValidName(bidderName)) {
            result = bidderStatusBuilder(bidderName)
                    .error("Unsupported bidder")
                    .build();
        } else if (!bidderCatalog.isActive(bidderName)) {
            result = bidderStatusBuilder(bidderName)
                    .error(String.format("%s is not configured properly on this Prebid Server deploy. "
                            + "If you believe this should work, contact the company hosting the service "
                            + "and tell them to check their configuration.", bidderName))
                    .build();
        } else if (biddersRejectedByGdpr.contains(bidderName)) {
            result = bidderStatusBuilder(bidderName)
                    .error("Rejected by GDPR")
                    .build();
        } else {
            final Usersyncer usersyncer = bidderCatalog.usersyncerByName(bidderName);
            if (!uidsCookie.hasLiveUidFrom(usersyncer.cookieFamilyName())) {
                result = bidderStatusBuilder(bidderName)
                        .noCookie(true)
                        .usersync(usersyncer.usersyncInfo().withGdpr(gdpr, gdprConsent))
                        .build();
            } else {
                result = null;
            }
        }

        return result;
    }

    private static BidderUsersyncStatus.BidderUsersyncStatusBuilder bidderStatusBuilder(String bidderName) {
        return BidderUsersyncStatus.builder().bidder(bidderName);
    }
}
