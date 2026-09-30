package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargetingFlowResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.Objects;

public class OptableTargetingProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    public static final String CODE = "optable-targeting-processed-auction-request-hook";

    private final ConfigResolver configResolver;

    private final OptableTargetingFlowResolver optableTargetingFlowResolver;

    public OptableTargetingProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                       OptableTargetingFlowResolver earlyOptableCallResolver) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.optableTargetingFlowResolver = Objects.requireNonNull(earlyOptableCallResolver);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());

        if (moduleContext.isEarlyNetworkCallEnabled()) {
            if (moduleContext.isEarlyCallInitializationCompleted()) {
                return success(moduleContext);
            } else {
                return optableTargetingFlowResolver.resolveAsyncOptableTargetingFlow(
                        moduleContext, auctionRequestPayload, invocationContext, properties, true);
            }
        }

        return optableTargetingFlowResolver.resolveOptableTargetingFlow(
                auctionRequestPayload, invocationContext, moduleContext, properties);
    }

    public static Future<InvocationResult<AuctionRequestPayload>> success(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
