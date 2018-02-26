package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.usersyncer.UsersyncerCatalog;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CookieSyncHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);

    private final UidsCookieService uidsCookieService;
    private final UsersyncerCatalog usersyncerCatalog;
    private final Metrics metrics;

    public CookieSyncHandler(UidsCookieService uidsCookieService, UsersyncerCatalog usersyncerCatalog,
                             Metrics metrics) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.usersyncerCatalog = Objects.requireNonNull(usersyncerCatalog);
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

        final JsonObject body;
        try {
            body = context.getBodyAsJson();
        } catch (DecodeException e) {
            logger.info("Failed to parse /cookie_sync request body", e);
            context.response().setStatusCode(400).setStatusMessage("JSON parse failed").end();
            return;
        }

        if (body == null) {
            logger.error("Incoming request has no body.");
            context.response().setStatusCode(400).end();
            return;
        }

        final CookieSyncRequest cookieSyncRequest = body.mapTo(CookieSyncRequest.class);

        final List<BidderStatus> bidderStatuses = cookieSyncRequest.getBidders().stream()
                .filter(usersyncerCatalog::isValidName)
                .map(usersyncerCatalog::byName)
                .filter(usersyncer -> !uidsCookie.hasLiveUidFrom(usersyncer.cookieFamilyName()))
                .map(usersyncer -> BidderStatus.builder()
                        .noCookie(true)
                        .bidder(usersyncer.name())
                        .usersync(usersyncer.usersyncInfo())
                        .build())
                .collect(Collectors.toList());

        final CookieSyncResponse response = CookieSyncResponse.of(cookieSyncRequest.getUuid(),
                uidsCookie.hasLiveUids() ? "ok" : "no_cookie", bidderStatuses);

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }
}
