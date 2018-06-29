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
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class SetuidHandler implements Handler<RoutingContext> {

    private static final Set<GdprPurpose> GDPR_PURPOSES =
            Collections.unmodifiableSet(EnumSet.of(GdprPurpose.informationStorageAndAccess));

    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final GdprService gdprService;
    private final Set<Integer> gdprVendorIds;
    private final boolean useGeoLocation;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;

    public SetuidHandler(long defaultTimeout, UidsCookieService uidsCookieService, GdprService gdprService,
                         Integer gdprHostVendorId, boolean useGeoLocation, AnalyticsReporter analyticsReporter,
                         Metrics metrics, TimeoutFactory timeoutFactory) {
        this.defaultTimeout = defaultTimeout;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.gdprService = Objects.requireNonNull(gdprService);
        this.gdprVendorIds = Collections.singleton(gdprHostVendorId);
        this.useGeoLocation = useGeoLocation;
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
    }

    @Override
    public void handle(RoutingContext context) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            final int status = HttpResponseStatus.UNAUTHORIZED.code();
            context.response().setStatusCode(status).end();
            metrics.updateCookieSyncOptoutMetric();
            analyticsReporter.processEvent(SetuidEvent.error(status));
            return;
        }

        final String bidder = context.request().getParam("bidder");
        if (StringUtils.isBlank(bidder)) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            context.response().setStatusCode(status).end("\"bidder\" query param is required");
            metrics.updateCookieSyncBadRequestMetric();
            analyticsReporter.processEvent(SetuidEvent.error(status));
            return;
        }

        final String gdpr = context.request().getParam("gdpr");
        final String gdprConsent = context.request().getParam("gdpr_consent");
        final String ip = useGeoLocation ? HttpUtil.ipFrom(context.request()) : null;
        gdprService.resultByVendor(GDPR_PURPOSES, gdprVendorIds, gdpr, gdprConsent, ip,
                timeoutFactory.create(defaultTimeout))
                .setHandler(asyncResult -> handleResult(asyncResult, context, uidsCookie, bidder));
    }

    private void handleResult(AsyncResult<GdprResponse> asyncResult, RoutingContext context,
                              UidsCookie uidsCookie, String bidder) {
        final boolean gdprProcessingFailed = asyncResult.failed();
        final boolean allowedCookie = !gdprProcessingFailed && asyncResult.result().getVendorsToGdpr().values()
                .iterator().next();

        if (allowedCookie) {
            respondWithCookie(context, bidder, uidsCookie);
        } else {
            final int status;
            final String body;

            if (gdprProcessingFailed) {
                status = HttpResponseStatus.BAD_REQUEST.code();
                body = asyncResult.cause().getMessage();
            } else {
                status = HttpResponseStatus.OK.code();
                body = "The gdpr_consent param prevents cookies from being saved";
            }

            respondWithoutCookie(context, status, body, bidder);
        }
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
            metrics.updateCookieSyncSetsMetric(bidder);
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

    private void respondWithoutCookie(RoutingContext context, int status, String body, String bidder) {
        context.response().setStatusCode(status).end(body);
        metrics.updateCookieSyncGdprPreventMetric(bidder);
        analyticsReporter.processEvent(SetuidEvent.error(status));
    }
}
