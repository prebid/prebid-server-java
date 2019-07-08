package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.metric.MetricName;

@Builder
@Value
public class AuctionContext {

    RoutingContext routingContext;

    UidsCookie uidsCookie;

    BidRequest bidRequest;

    Timeout timeout;

    MetricName requestTypeMetric;
}
