package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.Getter;
import lombok.Setter;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.List;
import java.util.Objects;

@Setter
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
}
