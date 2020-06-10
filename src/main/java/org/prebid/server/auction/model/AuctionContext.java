package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iab.openrtb.request.BidRequest;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.metric.MetricName;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class AuctionContext {

    @JsonIgnore
    RoutingContext routingContext;

    @JsonIgnore
    UidsCookie uidsCookie;

    BidRequest bidRequest;

    @JsonIgnore
    Timeout timeout;

    Account account;

    MetricName requestTypeMetric;

    Map<String, List<DebugHttpCall>> debugHttpCalls;
}
