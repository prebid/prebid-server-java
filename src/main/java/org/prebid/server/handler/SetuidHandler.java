package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.gdpr.model.GdprResult;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class SetuidHandler implements Handler<RoutingContext> {

    private final UidsCookieService uidsCookieService;
    private final GdprService gdprService;
    private final boolean useGeoLocation;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;

    public SetuidHandler(UidsCookieService uidsCookieService, GdprService gdprService, boolean useGeoLocation,
                         AnalyticsReporter analyticsReporter, Metrics metrics) {
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.gdprService = Objects.requireNonNull(gdprService);
        this.useGeoLocation = useGeoLocation;
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
            context.response().setStatusCode(status).end("\"bidder\" query param is required");
            metrics.cookieSync().incCounter(MetricName.bad_requests);
            analyticsReporter.processEvent(SetuidEvent.error(status));
            return;
        }

        final String gdpr = context.request().getParam("gdpr");
        final String gdprConsent = context.request().getParam("gdpr_consent");
        final String ip = useGeoLocation ? HttpUtil.ipFrom(context.request()) : null;
        gdprService.analyze(gdpr, gdprConsent, ip)
                .setHandler(result -> handleResult(result, context, uidsCookie, bidder, gdpr, gdprConsent));
    }

    private void handleResult(AsyncResult<GdprResponse> result, RoutingContext context, UidsCookie uidsCookie,
                              String bidder, String gdpr, String gdprConsent) {
        final GdprResult gdprResult = result.result().getGdprResult();

        if (gdprResult == GdprResult.allowed) {
            respondWithCookie(context, bidder, uidsCookie);
        } else {
            respondWithoutCookie(gdprResult, context, bidder, gdpr, gdprConsent);
        }
    }

    private void respondWithoutCookie(GdprResult gdprResult, RoutingContext context, String bidder, String gdpr,
                                      String gdprConsent) {
        final int status;
        final String body;

        switch (gdprResult) {
            case error_invalid_gdpr:
                status = HttpResponseStatus.BAD_REQUEST.code();
                body = String.format("The gdpr query param must be either 0 or 1. You gave %s", gdpr);
                break;
            case error_missing_consent:
                status = HttpResponseStatus.BAD_REQUEST.code();
                body = "The gdpr_consent is required when gdpr=1";
                break;
            case error_invalid_consent:
                status = HttpResponseStatus.BAD_REQUEST.code();
                body = String.format("The gdpr_consent is invalid. You gave %s", gdprConsent);
                break;
            default: // don't save any cookie
                status = HttpResponseStatus.OK.code();
                body = "The gdpr_consent prevents cookies from being saved";
        }

        context.response().setStatusCode(status).end(body);
        metrics.cookieSync().forBidder(bidder).incCounter(MetricName.gdpr_prevent);
        analyticsReporter.processEvent(SetuidEvent.error(status));
    }

    private void respondWithCookie(RoutingContext context, String bidder, UidsCookie uidsCookie) {
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
