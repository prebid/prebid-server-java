package org.prebid.handler;

import io.vertx.core.Handler;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.cookie.UidsCookie;
import org.prebid.cookie.UidsCookieService;
import org.prebid.metric.MetricName;
import org.prebid.metric.Metrics;

import java.util.Objects;

public class SetuidHandler implements Handler<RoutingContext> {

    private final UidsCookieService uidsCookieService;
    private final Metrics metrics;

    public SetuidHandler(UidsCookieService uidsCookieService, Metrics metrics) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public void handle(RoutingContext context) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            context.response().setStatusCode(401).end();
            metrics.cookieSync().incCounter(MetricName.opt_outs);
            return;
        }

        final String bidder = context.request().getParam("bidder");
        if (StringUtils.isBlank(bidder)) {
            context.response().setStatusCode(400).end();
            metrics.cookieSync().incCounter(MetricName.bad_requests);
            return;
        }

        final String uid = context.request().getParam("uid");
        final UidsCookie updatedUidsCookie;
        if (StringUtils.isBlank(uid)) {
            updatedUidsCookie = uidsCookie.deleteUid(bidder);
        } else if (UidsCookie.isFacebookSentinel(bidder, uid)) {
            // At the moment, Facebook calls /setuid with a UID of 0 if the user isn't logged into Facebook.
            // They shouldn't be sending us a sentinel value... but since they are, we're refusing to save that ID.
            updatedUidsCookie = uidsCookie;
        } else {
            updatedUidsCookie = uidsCookie.updateUid(bidder, uid);
            metrics.cookieSync().forBidder(bidder).incCounter(MetricName.sets);
        }

        final Cookie cookie = uidsCookieService.toCookie(updatedUidsCookie);
        context.addCookie(cookie).response().end();
    }
}
