package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Getter;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.List;
import java.util.Objects;

@Getter
public class ModuleContext {

    private Metrics metrics;

    private List<Audience> targeting;

    private EnrichmentStatus enrichRequestStatus;

    private EnrichmentStatus enrichResponseStatus;

    private boolean adserverTargetingEnabled;

    public static ModuleContext of(AuctionInvocationContext invocationContext) {
        return (ModuleContext) Objects.requireNonNull(invocationContext.moduleContext());
    }

    public ModuleContext setMetrics(Metrics metrics) {
        this.metrics = metrics;
        return this;
    }

    public ModuleContext setTargeting(List<Audience> targeting) {
        this.targeting = targeting;
        return this;
    }

    public ModuleContext setEnrichRequestStatus(EnrichmentStatus enrichRequestStatus) {
        this.enrichRequestStatus = enrichRequestStatus;
        return this;
    }

    public ModuleContext setEnrichResponseStatus(EnrichmentStatus enrichResponseStatus) {
        this.enrichResponseStatus = enrichResponseStatus;
        return this;
    }

    public ModuleContext setAdserverTargetingEnabled(boolean adserverTargetingEnabled) {
        this.adserverTargetingEnabled = adserverTargetingEnabled;
        return this;
    }

    public ModuleContext setFinishTime(long timestamp) {
        setMetrics(getMetrics().toBuilder()
                .moduleFinishTime(timestamp)
                .build());

        return this;
    }
}
