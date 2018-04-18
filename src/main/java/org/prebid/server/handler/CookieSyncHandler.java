package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CookieSyncHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);

    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;

    public CookieSyncHandler(UidsCookieService uidsCookieService, BidderCatalog bidderCatalog,
                             AnalyticsReporter analyticsReporter, Metrics metrics) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void handle(RoutingContext context) {
        metrics.incCounter(MetricName.cookie_sync_requests);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            final int status = HttpResponseStatus.UNAUTHORIZED.code();
            context.response().setStatusCode(status).setStatusMessage("User has opted out").end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, "user has opted out"));
            return;
        }

        final Buffer body = context.getBody();

        if (body == null) {
            logger.error("Incoming request has no body.");
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

        final List<String> biddersFromRequest = cookieSyncRequest.getBidders();

        // if bidder list was omitted in request, that means sync should be done for all bidders
        final Collection<String> biddersToSync = biddersFromRequest == null
                ? bidderCatalog.names() : biddersFromRequest;

        final List<BidderUsersyncStatus> bidderStatuses = biddersToSync.stream()
                .map(bidderName -> bidderStatusFor(bidderName, uidsCookie))
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

    private BidderUsersyncStatus bidderStatusFor(String bidderName, UidsCookie uidsCookie) {
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
        } else {
            final Usersyncer usersyncer = bidderCatalog.usersyncerByName(bidderName);
            if (!uidsCookie.hasLiveUidFrom(usersyncer.cookieFamilyName())) {
                result = bidderStatusBuilder(bidderName)
                        .noCookie(true)
                        .usersync(usersyncer.usersyncInfo())
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
