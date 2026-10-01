package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionRequestCleaner;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargetingFlowResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PropertiesValidator;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;

import java.util.Objects;

public class OptableRawAuctionRequestHook implements RawAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(OptableRawAuctionRequestHook.class));

    public static final String CODE = "optable-targeting-raw-auction-request-hook";

    private final ConfigResolver configResolver;
    private final OptableTargetingFlowResolver optableTargetingFlowResolver;
    private final double logSamplingRate;

    public OptableRawAuctionRequestHook(ConfigResolver configResolver,
                                        OptableTargetingFlowResolver optableTargetingFlowResolver,
                                        double logSamplingRate) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.optableTargetingFlowResolver = optableTargetingFlowResolver;
        this.logSamplingRate = logSamplingRate;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload payload,
                                                                AuctionInvocationContext invocationContext) {

        final OptableTargetingProperties properties = configResolver.resolve(invocationContext.accountConfig());
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setEarlyNetworkCallEnabled(true);
        moduleContext.setCallTargetingAPITimestamp(System.currentTimeMillis());
        moduleContext.setOptableTargetingProperties(properties);

        if (!PropertiesValidator.isValid(properties)) {
            conditionalLogger.error(
                    "Account not properly configured: tenant and/or origin is missing.", logSamplingRate);

            moduleContext.failWithExecutionTime(
                    System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp());

            return update(AuctionRequestCleaner.instance(), moduleContext);
        }

        return optableTargetingFlowResolver.resolveAsyncOptableTargetingFlow(
                moduleContext, payload, invocationContext, properties, false);
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
