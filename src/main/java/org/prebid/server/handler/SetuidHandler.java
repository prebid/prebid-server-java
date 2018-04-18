package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.util.Objects;

public class SetuidHandler implements Handler<RoutingContext> {

    private final UidsCookieService uidsCookieService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;

    public SetuidHandler(UidsCookieService uidsCookieService, AnalyticsReporter analyticsReporter, Metrics metrics) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void handle(RoutingContext context) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            final int status = HttpResponseStatus.UNAUTHORIZED.code();
            context.response().setStatusCode(status).end();
            metrics.cookieSync().incCounter(MetricName.opt_outs);
            analyticsReporter.processEvent(SetuidEvent.error(status));
            return;
        }

        final String bidder = context.request().getParam("bidder");
        if (StringUtils.isBlank(bidder)) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            context.response().setStatusCode(status).end();
            metrics.cookieSync().incCounter(MetricName.bad_requests);
            analyticsReporter.processEvent(SetuidEvent.error(status));
            return;
        }

        final String uid = context.request().getParam("uid");
        final UidsCookie updatedUidsCookie;
        boolean successfullyUpdated = false;
        if (StringUtils.isBlank(uid)) {
            updatedUidsCookie = uidsCookie.deleteUid(bidder);
        } else if (UidsCookie.isFacebookSentinel(bidder, uid)) {
            // At the moment, Facebook calls /setuid with a UID of 0 if the user isn't logged into Facebook.
            // They shouldn't be sending us a sentinel value... but since they are, we're refusing to save that ID.
            updatedUidsCookie = uidsCookie;
        } else {
            updatedUidsCookie = uidsCookie.updateUid(bidder, uid);
            successfullyUpdated = true;
            metrics.cookieSync().forBidder(bidder).incCounter(MetricName.sets);
        }

        final Cookie cookie = uidsCookieService.toCookie(updatedUidsCookie);
        context.addCookie(cookie).response().end();

        analyticsReporter.processEvent(SetuidEvent.builder()
                .status(HttpResponseStatus.OK.code())
                .bidder(bidder)
                .uid(uid)
                .success(successfullyUpdated)
                .build());
    }
}
