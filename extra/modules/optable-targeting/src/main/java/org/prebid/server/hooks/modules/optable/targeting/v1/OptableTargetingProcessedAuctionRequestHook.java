package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidRequestCleaner;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidRequestEnricher;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.NetworkCall;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;

import java.util.Objects;

public class OptableTargetingProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(OptableRawAuctionRequestHook.class));

    public static final String CODE = "optable-targeting-processed-auction-request-hook";

    private static final String AUCTION_NOT_PROPERLY_CONFIGURED =
            "Account not properly configured: tenant and/or origin is missing.";

    private final ConfigResolver configResolver;
    private final NetworkCall networkCall;
    private final double logSamplingRate;

    public OptableTargetingProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                       NetworkCall networkCall,
                                                       double logSamplingRate) {
        this.configResolver = Objects.requireNonNull(configResolver);
        this.networkCall = Objects.requireNonNull(networkCall);
        this.logSamplingRate = logSamplingRate;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final ModuleContext moduleContext = ModuleContext.of(invocationContext);

        final Future<TargetingResult> optableTargetingCall = moduleContext.isEarlyNetworkCallEnabled()
                ? moduleContext.getOptableTargetingCall()
                : makeOptableTargetingCall(auctionRequestPayload, invocationContext, moduleContext, properties);

        if (optableTargetingCall == null) {
            moduleContext.failWithExecutionTime(
                    System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp());
            return update(BidRequestCleaner.instance(), moduleContext);
        }

        final Future<InvocationResult<AuctionRequestPayload>> future = optableTargetingCall
                .compose(targetingResult -> {
                    moduleContext.setOptableTargetingExecutionTime(
                            System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp());
                    return enrichedPayload(targetingResult, moduleContext, properties);
                })
                .recover(throwable -> {
                    moduleContext.failWithExecutionTime(
                            System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp());
                    return update(BidRequestCleaner.instance(), moduleContext);
                });

        return future;
    }

    private Future<TargetingResult> makeOptableTargetingCall(
            AuctionRequestPayload payload,
            AuctionInvocationContext invocationContext,
            ModuleContext moduleContext,
            OptableTargetingProperties properties) {
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis());
        if (!OptableHook.isTargetingPropertiesValid(properties)) {
            conditionalLogger.error(AUCTION_NOT_PROPERLY_CONFIGURED, logSamplingRate);

            moduleContext.failWithExecutionTime(
                    System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp());
            return Future.failedFuture(AUCTION_NOT_PROPERLY_CONFIGURED);
        }

        return networkCall.makeRequest(
                payload,
                invocationContext,
                properties);
    }

    private Future<InvocationResult<AuctionRequestPayload>> enrichedPayload(TargetingResult targetingResult,
                                                                            ModuleContext moduleContext,
                                                                            OptableTargetingProperties properties) {

        moduleContext.setTargeting(targetingResult.getAudience());
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());
        return update(
                BidRequestCleaner.instance()
                        .andThen(BidRequestEnricher.of(targetingResult, properties))
                        ::apply,
                moduleContext);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> update(
            PayloadUpdate<AuctionRequestPayload> payloadUpdate,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .analyticsTags(AnalyticTagsResolver.toEnrichRequestAnalyticTags(moduleContext))
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
