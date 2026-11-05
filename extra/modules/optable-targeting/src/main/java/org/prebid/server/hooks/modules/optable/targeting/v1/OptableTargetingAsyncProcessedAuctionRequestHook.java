package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionRequestCleaner;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargetingFlowExecutor;
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

public class OptableTargetingAsyncProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(OptableTargetingAsyncProcessedAuctionRequestHook.class));

    public static final String CODE = "optable-targeting-async-processed-auction-request-hook";

    private final ConfigResolver configResolver;

    private final OptableTargetingFlowExecutor optableTargetingFlowExecutor;

    private final double logSamplingRate;

    public OptableTargetingAsyncProcessedAuctionRequestHook(ConfigResolver configResolver,
                                                            OptableTargetingFlowExecutor earlyOptableCallResolver,
                                                            double logSamplingRate) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.optableTargetingFlowExecutor = Objects.requireNonNull(earlyOptableCallResolver);
        this.logSamplingRate = logSamplingRate;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                 AuctionInvocationContext invocationContext) {

        final ModuleContext moduleContext = ModuleContext.of(invocationContext);
        if (moduleContext.isEarlyCallInitializationCompleted()) {
            return noAction(moduleContext);
        }

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        moduleContext.setOptableTargetingProperties(properties);
        moduleContext.setOptableTargetingStartTimestamp(System.currentTimeMillis());

        if (!PropertiesValidator.isValid(properties)) {
            conditionalLogger.error(
                    "Account not properly configured: tenant and/or origin is missing.", logSamplingRate);

            moduleContext.failAndRecordExecutionTime();

            return update(AuctionRequestCleaner.instance(), moduleContext);
        }

        if (!PropertiesValidator.isTrafficSourceValid(
                invocationContext.auctionContext().getBidRequest(), properties)) {

            moduleContext.setShouldSkipEnrichment(true);

            return update(AuctionRequestCleaner.instance(), moduleContext);
        }

        return optableTargetingFlowExecutor.executeAsyncOptableTargetingFlow(
                moduleContext, auctionRequestPayload, invocationContext, properties);
    }

    public static Future<InvocationResult<AuctionRequestPayload>> noAction(ModuleContext moduleContext) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }

    public static Future<InvocationResult<AuctionRequestPayload>> update(
            PayloadUpdate<AuctionRequestPayload> payloadUpdate,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
