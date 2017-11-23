package org.rtb.vexing.handler;

import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;

public class SetuidHandler {

    private final Metrics metrics;

    public SetuidHandler(Metrics metrics) {
        this.metrics = metrics;
    }

    public void setuid(RoutingContext context) {
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(context);
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

        context.addCookie(updatedUidsCookie.toCookie()).response().end();
    }
}
