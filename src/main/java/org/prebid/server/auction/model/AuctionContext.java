package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class AuctionContext {

    HttpRequestContext httpRequest;

    @JsonIgnore
    UidsCookie uidsCookie;

    BidRequest bidRequest;

    @JsonIgnore
    Timeout timeout;

    Account account;

    MetricName requestTypeMetric;

    List<String> prebidErrors;

    List<String> debugWarnings;

    Map<String, List<DebugHttpCall>> debugHttpCalls;

    PrivacyContext privacyContext;

    GeoInfo geoInfo;

    HookExecutionContext hookExecutionContext;

    DebugContext debugContext;

    boolean requestRejected;

    @JsonIgnore
    TxnLog txnLog;

    @JsonIgnore
    DeepDebugLog deepDebugLog;

    public AuctionContext with(Account account) {
        return this.toBuilder().account(account).build();
    }

    public AuctionContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public AuctionContext with(BidRequest bidRequest, List<String> errors) {
        return this.toBuilder().bidRequest(bidRequest).prebidErrors(errors).build();
    }

    public AuctionContext with(PrivacyContext privacyContext) {
        return this.toBuilder()
                .privacyContext(privacyContext)
                .geoInfo(privacyContext.getTcfContext().getGeoInfo())
                .build();
    }

    public AuctionContext with(MetricName requestTypeMetric) {
        return this.toBuilder()
                .requestTypeMetric(requestTypeMetric)
                .build();
    }

    public AuctionContext with(DebugContext debugContext) {
        return this.toBuilder()
                .debugContext(debugContext)
                .build();
    }

    public AuctionContext withRequestRejected() {
        return this.toBuilder()
                .requestRejected(true)
                .build();
    }
}
