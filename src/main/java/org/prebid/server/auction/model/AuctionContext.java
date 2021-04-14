package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class AuctionContext {

    HttpRequestWrapper httpRequest;

    UidsCookie uidsCookie;

    BidRequest bidRequest;

    Timeout timeout;

    Account account;

    MetricName requestTypeMetric;

    List<String> prebidErrors;

    List<String> debugWarnings;

    Map<String, List<DebugHttpCall>> debugHttpCalls;

    PrivacyContext privacyContext;

    GeoInfo geoInfo;

    HookExecutionContext hookExecutionContext;

    public AuctionContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public AuctionContext with(PrivacyContext privacyContext) {
        return this.toBuilder()
                .privacyContext(privacyContext)
                .geoInfo(privacyContext.getTcfContext().getGeoInfo())
                .build();
    }
}
