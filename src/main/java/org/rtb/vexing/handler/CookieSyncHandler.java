package org.rtb.vexing.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.request.CookieSyncRequest;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.CookieSyncResponse;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CookieSyncHandler {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);

    private final AdapterCatalog adapterCatalog;
    private final Metrics metrics;

    public CookieSyncHandler(AdapterCatalog adapterCatalog, Metrics metrics) {
        this.adapterCatalog = Objects.requireNonNull(adapterCatalog);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public void sync(RoutingContext context) {
        metrics.incCounter(MetricName.cookie_sync_requests);

        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            context.response().setStatusCode(401).setStatusMessage("User has opted out").end();
            return;
        }

        final JsonObject body = context.getBodyAsJson();
        if (body == null) {
            logger.error("Incoming request has no body.");
            context.response().setStatusCode(400).end();
            return;
        }

        final CookieSyncRequest cookieSyncRequest = body.mapTo(CookieSyncRequest.class);

        final List<BidderStatus> bidderStatuses = cookieSyncRequest.bidders.stream()
                .filter(adapterCatalog::isValidCode)
                .map(adapterCatalog::getByCode)
                .filter(adapter -> uidsCookie.uidFrom(adapter.cookieFamily()) == null)
                .map(adapter -> BidderStatus.builder()
                        .bidder(adapter.code())
                        .noCookie(true)
                        .usersync(adapter.usersyncInfo())
                        .build())
                .collect(Collectors.toList());

        final CookieSyncResponse response = CookieSyncResponse.builder()
                .uuid(cookieSyncRequest.uuid)
                .status(uidsCookie.hasLiveUids() ? "OK" : "no_cookie")
                .bidderStatus(bidderStatuses)
                .build();

        context.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }
}
