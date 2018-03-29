package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.CookieSyncResponse;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CookieSyncHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);

    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final Metrics metrics;

    public CookieSyncHandler(UidsCookieService uidsCookieService, BidderCatalog bidderCatalog,
                             Metrics metrics) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void handle(RoutingContext context) {
        metrics.incCounter(MetricName.cookie_sync_requests);

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            context.response().setStatusCode(401).setStatusMessage("User has opted out").end();
            return;
        }

        final Buffer body = context.getBody();

        if (body == null) {
            logger.error("Incoming request has no body.");
            context.response().setStatusCode(400).end();
            return;
        }

        final CookieSyncRequest cookieSyncRequest;
        try {
            cookieSyncRequest = Json.decodeValue(body, CookieSyncRequest.class);
        } catch (DecodeException e) {
            logger.info("Failed to parse /cookie_sync request body", e);
            context.response().setStatusCode(400).setStatusMessage("JSON parse failed").end();
            return;
        }

        final List<String> biddersFromRequest = cookieSyncRequest.getBidders();

        // if bidder list was omitted in request, that means sync should be done for all bidders
        final Collection<String> biddersToSync = biddersFromRequest == null
                ? bidderCatalog.names() : biddersFromRequest;

        final List<BidderStatus> bidderStatuses = biddersToSync.stream()
                .map(bidderName -> bidderStatusFor(bidderName, uidsCookie))
                .filter(Objects::nonNull) // skip bidder with live Uid
                .collect(Collectors.toList());

        final CookieSyncResponse response = CookieSyncResponse.of(uidsCookie.hasLiveUids() ? "ok" : "no_cookie",
                bidderStatuses);

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }

    private BidderStatus bidderStatusFor(String bidderName, UidsCookie uidsCookie) {
        final BidderStatus result;

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

    private static BidderStatus.BidderStatusBuilder bidderStatusBuilder(String bidderName) {
        return BidderStatus.builder().bidder(bidderName);
    }
}
