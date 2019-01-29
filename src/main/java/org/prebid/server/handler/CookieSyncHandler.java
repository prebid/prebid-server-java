package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
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

    private final String externalUrl;
    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final GdprService gdprService;
    private final Integer gdprHostVendorId;
    private final boolean useGeoLocation;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;

    public CookieSyncHandler(String externalUrl, long defaultTimeout, UidsCookieService uidsCookieService,
                             BidderCatalog bidderCatalog,
                             GdprService gdprService, Integer gdprHostVendorId, boolean useGeoLocation,
                             AnalyticsReporter analyticsReporter, Metrics metrics, TimeoutFactory timeoutFactory) {
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
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

        final Set<Integer> vendorIds = gdprVendorIdsFor(biddersToSync);
        vendorIds.add(gdprHostVendorId);

        final String ip = useGeoLocation ? HttpUtil.ipFrom(context.request()) : null;
        final String gdprAsString = gdpr != null ? gdpr.toString() : null;

        gdprService.resultByVendor(GDPR_PURPOSES, vendorIds, gdprAsString, gdprConsent, ip,
                timeoutFactory.create(defaultTimeout))
                .setHandler(asyncResult -> handleResult(asyncResult, context, uidsCookie, biddersToSync,
                        gdprAsString, gdprConsent, cookieSyncRequest.getLimit()));
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
        final String resolvedBidder = bidderNameFor(bidder);
        return bidderCatalog.isActive(resolvedBidder)
                ? bidderCatalog.metaInfoByName(resolvedBidder).getGdpr().getVendorId()
                : null;
    }

    /**
     * Determines original bidder's name.
     */
    private String bidderNameFor(String bidder) {
        return bidderCatalog.isAlias(bidder) ? bidderCatalog.nameByAlias(bidder) : bidder;
    }

    /**
     * Handles GDPR verification result.
     */
    private void handleResult(AsyncResult<GdprResponse> asyncResult, RoutingContext context, UidsCookie uidsCookie,
                              Collection<String> biddersToSync, String gdpr, String gdprConsent, Integer limit) {
        if (asyncResult.failed()) {
            respondWith(context, uidsCookie, gdpr, gdprConsent, biddersToSync, biddersToSync, limit);
        } else {
            final Map<Integer, Boolean> vendorsToGdpr = asyncResult.result().getVendorsToGdpr();

            final Boolean gdprResult = vendorsToGdpr.get(gdprHostVendorId);
            if (gdprResult == null || !gdprResult) { // host vendor should be allowed by GDPR verification
                respondWith(context, uidsCookie, gdpr, gdprConsent, biddersToSync, biddersToSync, limit);
            } else {
                final Set<Integer> vendorIds = vendorsToGdpr.entrySet().stream()
                        .filter(Map.Entry::getValue) // get only vendors passed GDPR verification
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

                final Set<String> biddersRejectedByGdpr = biddersToSync.stream()
                        .filter(bidder -> !vendorIds.contains(gdprVendorIdFor(bidder)))
                        .collect(Collectors.toSet());

                respondWith(context, uidsCookie, gdpr, gdprConsent, biddersToSync, biddersRejectedByGdpr, limit);
            }
        }
    }

    /**
     * Make HTTP response for given bidders.
     */
    private void respondWith(RoutingContext context, UidsCookie uidsCookie, String gdpr, String gdprConsent,
                             Collection<String> bidders, Collection<String> biddersRejectedByGdpr, Integer limit) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }

        final List<BidderUsersyncStatus> bidderStatuses = bidders.stream()
                .map(bidder -> bidderStatusFor(bidder, context, uidsCookie, biddersRejectedByGdpr, gdpr, gdprConsent))
                .filter(Objects::nonNull) // skip bidder with live UID
                .collect(Collectors.toList());

        final List<BidderUsersyncStatus> updatedBidderStatuses;
        if (limit != null && limit > 0 && limit < bidders.size()) {
            Collections.shuffle(bidderStatuses);
            updatedBidderStatuses = bidderStatuses.subList(0, limit);
        } else {
            updatedBidderStatuses = bidderStatuses;
        }

        final CookieSyncResponse response = CookieSyncResponse.of(uidsCookie.hasLiveUids() ? "ok" : "no_cookie",
                updatedBidderStatuses);

        context.response()
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));

        analyticsReporter.processEvent(CookieSyncEvent.builder()
                .status(HttpResponseStatus.OK.code())
                .bidderStatus(updatedBidderStatuses)
                .build());
    }

    /**
     * Creates {@link BidderUsersyncStatus} for given bidder.
     */
    private BidderUsersyncStatus bidderStatusFor(String bidder, RoutingContext context, UidsCookie uidsCookie,
                                                 Collection<String> biddersRejectedByGdpr,
                                                 String gdpr, String gdprConsent) {
        final BidderUsersyncStatus result;
        final boolean isNotAlias = !bidderCatalog.isAlias(bidder);

        if (isNotAlias && !bidderCatalog.isValidName(bidder)) {
            result = bidderStatusBuilder(bidder)
                    .error("Unsupported bidder")
                    .build();
        } else if (isNotAlias && !bidderCatalog.isActive(bidder)) {
            result = bidderStatusBuilder(bidder)
                    .error(String.format("%s is not configured properly on this Prebid Server deploy. "
                            + "If you believe this should work, contact the company hosting the service "
                            + "and tell them to check their configuration.", bidder))
                    .build();
        } else if (isNotAlias && biddersRejectedByGdpr.contains(bidder)) {
            result = bidderStatusBuilder(bidder)
                    .error("Rejected by GDPR")
                    .build();
        } else {
            final Usersyncer usersyncer = bidderCatalog.usersyncerByName(bidderNameFor(bidder));
            final UsersyncInfo updatedUsersyncInfo = updatedUsersyncInfo(context, gdpr, gdprConsent, usersyncer);

            if (updatedUsersyncInfo != null || !uidsCookie.hasLiveUidFrom(usersyncer.cookieFamilyName())) {
                result = bidderStatusBuilder(bidder)
                        .noCookie(true)
                        .usersync(ObjectUtils.defaultIfNull(updatedUsersyncInfo,
                                usersyncer.usersyncInfo().withGdpr(gdpr, gdprConsent)))
                        .build();
            } else {
                result = null;
            }
        }

        return result;
    }

    private static BidderUsersyncStatus.BidderUsersyncStatusBuilder bidderStatusBuilder(String bidder) {
        return BidderUsersyncStatus.builder().bidder(bidder);
    }

    /**
     * Returns {@link UsersyncInfo} with updated usersync-url (pointed directly to Prebid Server /setuid endpoint)
     * or null if normal usersync flow should be applied.
     * <p>
     * Uids cookie should be in sync with host-cookie value, so the next conditions must be satisfied:
     * <p>
     * 1. Given {@link Usersyncer} should have the same cookie family value as configured host-cookie-family.
     * <p>
     * 2. Host-cookie must be present in HTTP request.
     * <p>
     * 3. Host-bidder uid value in uids cookie should not exist or be different from host-cookie uid value.
     */
    private UsersyncInfo updatedUsersyncInfo(RoutingContext context, String gdpr, String gdprConsent,
                                             Usersyncer usersyncer) {
        final String cookieFamilyName = usersyncer.cookieFamilyName();
        if (Objects.equals(cookieFamilyName, uidsCookieService.getHostCookieFamily())) {

            final String hostCookieUid = uidsCookieService.parseHostCookie(context);
            if (hostCookieUid != null) {

                final Uids parsedUids = uidsCookieService.parseUids(context);
                final Map<String, UidWithExpiry> uidsMap = parsedUids != null ? parsedUids.getUids() : null;
                final UidWithExpiry uidWithExpiry = uidsMap != null ? uidsMap.get(cookieFamilyName) : null;
                final String uid = uidWithExpiry != null ? uidWithExpiry.getUid() : null;

                if (!Objects.equals(hostCookieUid, uid)) {
                    final String url = String.format("%s/setuid?bidder=%s&gdpr=%s&gdpr_consent=%s&uid=%s",
                            externalUrl, cookieFamilyName, gdpr, gdprConsent, hostCookieUid);
                    final UsersyncInfo usersyncInfo = usersyncer.usersyncInfo();

                    return UsersyncInfo.of(url, usersyncInfo.getType(),
                            usersyncInfo.getSupportCORS());
                }
            }
        }
        return null;
    }
}
