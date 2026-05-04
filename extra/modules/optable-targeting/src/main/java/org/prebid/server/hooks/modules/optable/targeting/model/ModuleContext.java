package org.prebid.server.hooks.modules.optable.targeting.model;

import io.vertx.core.Future;
import lombok.Data;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

import java.util.List;

@Data
public class ModuleContext {

    private List<Audience> targeting;

    private EnrichmentStatus enrichRequestStatus;

    private EnrichmentStatus enrichResponseStatus;

    private boolean adserverTargetingEnabled;

    private long optableTargetingExecutionTime;

    private boolean isEarlyNetworkCallEnabled = false;

    private Future<TargetingResult> optableTargetingCall;

    private long callTargetingAPITimestamp;

    public static ModuleContext of(AuctionInvocationContext invocationContext) {
        final ModuleContext moduleContext = (ModuleContext) invocationContext.moduleContext();
        return moduleContext != null ? moduleContext : new ModuleContext();
    }

    public void failWithExecutionTime(long executionTime) {
        setOptableTargetingExecutionTime(executionTime);
        setEnrichRequestStatus(EnrichmentStatus.failure());
    }
}
