package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Getter;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.List;
import java.util.Objects;

@Getter
public class ModuleContext {

    private List<Audience> targeting;

    private EnrichmentStatus enrichRequestStatus;

    private EnrichmentStatus enrichResponseStatus;

    private boolean adserverTargetingEnabled;

    private long optableTargetingExecutionTime;

    public static ModuleContext of(AuctionInvocationContext invocationContext) {
        return (ModuleContext) Objects.requireNonNull(invocationContext.moduleContext());
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

    public ModuleContext setOptableTargetingExecutionTime(long optableTargetingExecutionTime) {
        this.optableTargetingExecutionTime = optableTargetingExecutionTime;
        return this;
    }
}
