package org.prebid.server.metric.prebid;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.handler.openrtb2.AmpHandler;
import org.prebid.server.handler.openrtb2.AuctionHandler;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.time.Clock;

public class RequestHandlerMetrics extends AbstractHandlerMetrics {

    public RequestHandlerMetrics(Metrics metrics, Clock clock) {
        super(metrics, clock);
    }

    public void updateRequestMetrics(RoutingContext context, Handler<RoutingContext> handler) {
        if (AmpHandler.class.isAssignableFrom(handler.getClass())) {
            getMetrics().incCounter(MetricName.amp_requests);
        } else {
            getMetrics().incCounter(MetricName.requests);
            if (AuctionHandler.class.isAssignableFrom(handler.getClass())) {
                getMetrics().incCounter(MetricName.open_rtb_requests);
            }
        }

        if (isSafari(context)) {
            getMetrics().incCounter(MetricName.safari_requests);
        }
    }

    public Future<BidRequest> updateErrorRequestsMetric(RoutingContext context, Handler<RoutingContext> handler,
                                                        Throwable failed) {
        getMetrics().incCounter(MetricName.error_requests);
        return Future.failedFuture(failed);
    }

    public <T> T updateAppAndNoCookieMetrics(RoutingContext context, Handler<RoutingContext> handler, T bean,
                                             boolean hasLiveUids, boolean isApp) {

        if (isApp) {
            getMetrics().incCounter(MetricName.app_requests);
        } else if (hasLiveUids) {

            if (AmpHandler.class.isAssignableFrom(handler.getClass())) {
                getMetrics().incCounter(MetricName.amp_no_cookie);
            } else {
                getMetrics().incCounter(MetricName.no_cookie_requests);
            }

            if (isSafari(context)) {
                getMetrics().incCounter(MetricName.safari_no_cookie_requests);
            }
        }

        return bean;
    }

}
