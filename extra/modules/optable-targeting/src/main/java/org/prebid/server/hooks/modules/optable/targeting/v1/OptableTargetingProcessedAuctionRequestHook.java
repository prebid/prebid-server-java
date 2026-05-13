package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidRequestCleaner;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidRequestEnricher;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.CompositeHookExecutionPlan;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.NetworkCall;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PropertiesValidator;
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
    private final ExecutionPlan globalHooksExecutionPlan;

    public OptableTargetingProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                       NetworkCall networkCall,
                                                       ExecutionPlan globalHooksExecutionPlan,
                                                       double logSamplingRate) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.networkCall = Objects.requireNonNull(networkCall);
        this.globalHooksExecutionPlan = globalHooksExecutionPlan;
        this.logSamplingRate = logSamplingRate;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final ExecutionPlan accountSpecificHoksExecutionPlan =
                java.util.Optional.ofNullable(invocationContext.auctionContext().getAccount())
                        .map(org.prebid.server.settings.model.Account::getHooks)
                        .map(org.prebid.server.settings.model.AccountHooksConfiguration::getExecutionPlan)
                        .orElse(null);
        final CompositeHookExecutionPlan hooksExecutionPlan =
                CompositeHookExecutionPlan.of(globalHooksExecutionPlan, accountSpecificHoksExecutionPlan);

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        if (moduleContext.isShouldSkipEnrichment()) {
            moduleContext.setOptableTargetingExecutionTime(calcAPICallExecutionTime(moduleContext));
            return update(BidRequestCleaner.instance(), moduleContext);
        }

        final OptableTargetingProperties properties =
                resolveOptableTargetingProperties(moduleContext, invocationContext);

        final Future<TargetingResult> optableTargetingCall = hooksExecutionPlan.hasRawAuctionRequestHook()
                ? resolveEarlyNetworkCall(moduleContext)
                : resolvePreEarlyNetworkCall(auctionRequestPayload, invocationContext, moduleContext, properties);

        if (optableTargetingCall == null) {
            moduleContext.failWithExecutionTime(calcAPICallExecutionTime(moduleContext));
            return update(BidRequestCleaner.instance(), moduleContext);
        }

        return optableTargetingCall
                .compose(targetingResult -> {
                    moduleContext.setOptableTargetingExecutionTime(calcAPICallExecutionTime(moduleContext));
                    return enrichPayload(
                            hooksExecutionPlan.hasBidderRequestHook(), targetingResult, moduleContext, properties);
                })
                .recover(throwable -> {
                    moduleContext.failWithExecutionTime(calcAPICallExecutionTime(moduleContext));
                    return update(BidRequestCleaner.instance(), moduleContext);
                });
    }

    private Future<InvocationResult<AuctionRequestPayload>> enrichPayload(
            boolean perBidderEnrichmentEnabled,
            TargetingResult targetingResult,
            ModuleContext moduleContext,
            OptableTargetingProperties properties) {

        moduleContext.setTargeting(targetingResult.getAudience());
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

        final PayloadUpdate<AuctionRequestPayload> payloadUpdate = perBidderEnrichmentEnabled
                ? BidRequestCleaner.instance()
                : BidRequestCleaner.instance().andThen(BidRequestEnricher.of(targetingResult, properties))::apply;

        return update(payloadUpdate, moduleContext);
    }

    private Future<TargetingResult> resolveEarlyNetworkCall(ModuleContext moduleContext) {
        return moduleContext.getOptableTargetingCall();
    }

    private static long calcAPICallExecutionTime(ModuleContext moduleContext) {
        return System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp();
    }

    private OptableTargetingProperties resolveOptableTargetingProperties(ModuleContext moduleContext,
                                                                         AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = moduleContext.hasOptableTargetingProperties()
                ? moduleContext.getOptableTargetingProperties()
                : configResolver.resolve(invocationContext.accountConfig());
        moduleContext.setOptableTargetingProperties(properties);

        return properties;
    }

    private Future<TargetingResult> resolvePreEarlyNetworkCall(
            AuctionRequestPayload payload,
            AuctionInvocationContext invocationContext,
            ModuleContext moduleContext,
            OptableTargetingProperties properties) {

        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis());
        if (!PropertiesValidator.isValid(properties)) {
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
