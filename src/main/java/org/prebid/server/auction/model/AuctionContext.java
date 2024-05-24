package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cookie.UidsCookie;
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
    BidResponse bidResponse;

    @JsonIgnore
    List<AuctionParticipation> auctionParticipations;

    Account account;

    MetricName requestTypeMetric;

    List<String> prebidErrors;

    List<String> debugWarnings;

    Map<String, List<DebugHttpCall>> debugHttpCalls;

    Map<String, BidRejectionTracker> bidRejectionTrackers;

    @JsonIgnore
    TimeoutContext timeoutContext;

    GppContext gppContext;

    PrivacyContext privacyContext;

    ActivityInfrastructure activityInfrastructure;

    @JsonIgnore
    GeoInfo geoInfo;

    HookExecutionContext hookExecutionContext;

    DebugContext debugContext;

    boolean requestRejected;

    CachedDebugLog cachedDebugLog;

    public AuctionContext with(Account account) {
        return this.toBuilder().account(account).build();
    }

    public AuctionContext with(BidRequest bidRequest) {
        return this.toBuilder().bidRequest(bidRequest).build();
    }

    public AuctionContext with(BidResponse bidResponse) {
        return this.toBuilder().bidResponse(bidResponse).build();
    }

    public AuctionContext with(List<AuctionParticipation> auctionParticipations) {
        return this.toBuilder().auctionParticipations(auctionParticipations).build();
    }

    public AuctionContext with(MetricName requestTypeMetric) {
        return this.toBuilder()
                .requestTypeMetric(requestTypeMetric)
                .build();
    }

    public AuctionContext with(GppContext gppContext) {
        return this.toBuilder()
                .gppContext(gppContext)
                .build();
    }

    public AuctionContext with(PrivacyContext privacyContext) {
        return this.toBuilder()
                .privacyContext(privacyContext)
                .geoInfo(privacyContext.getTcfContext().getGeoInfo())
                .build();
    }

    public AuctionContext with(ActivityInfrastructure activityInfrastructure) {
        return this.toBuilder()
                .activityInfrastructure(activityInfrastructure)
                .build();
    }

    public AuctionContext with(DebugContext debugContext) {
        return this.toBuilder()
                .debugContext(debugContext)
                .build();
    }

    public AuctionContext with(GeoInfo geoInfo) {
        return this.toBuilder()
                .geoInfo(geoInfo)
                .build();
    }

    public AuctionContext withRequestRejected() {
        return this.toBuilder()
                .requestRejected(true)
                .build();
    }
}
