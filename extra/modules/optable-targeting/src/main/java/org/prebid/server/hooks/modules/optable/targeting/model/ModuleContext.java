package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Data;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.List;

@Data
public class ModuleContext {

    private List<Audience> targeting;

    private EnrichmentStatus enrichRequestStatus;

    private EnrichmentStatus enrichResponseStatus;

    private boolean adserverTargetingEnabled;

    private long optableTargetingExecutionTime;

    public static ModuleContext of(AuctionInvocationContext invocationContext) {
        final ModuleContext moduleContext = (ModuleContext) invocationContext.moduleContext();
        return moduleContext != null ? moduleContext : new ModuleContext();
    }
}
