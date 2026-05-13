package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidRequestCleaner;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderEnrichmentSampler;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.NetworkCall;
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
import java.util.Set;

public class OptableRawAuctionRequestHook implements RawAuctionRequestHook {

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(OptableRawAuctionRequestHook.class));

    private static final String CODE = "optable-targeting-raw-auction-request-hook";

    private final ConfigResolver configResolver;
    private final NetworkCall networkCall;
    private final BidderEnrichmentSampler bidderEnrichmentSampler;
    private final double logSamplingRate;

    public OptableRawAuctionRequestHook(ConfigResolver configResolver,
                                        NetworkCall networkCall,
                                        BidderEnrichmentSampler bidderEnrichmentSampler,
                                        double logSamplingRate) {

        this.configResolver = Objects.requireNonNull(configResolver);
        this.networkCall = Objects.requireNonNull(networkCall);
        this.bidderEnrichmentSampler = Objects.requireNonNull(bidderEnrichmentSampler);
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

            return update(BidRequestCleaner.instance(), moduleContext);
        }

        final BidRequest bidRequest = invocationContext.auctionContext().getBidRequest();
        if (!PropertiesValidator.isTrafficSourceValid(bidRequest, properties)) {
            moduleContext.setShouldSkipEnrichment(true);
            return update(BidRequestCleaner.instance(), moduleContext);
        }

        final Set<String> biddersToEnrich = bidderEnrichmentSampler.sample(bidRequest, properties);
        if (CollectionUtils.isEmpty(biddersToEnrich)) {
            return update(BidRequestCleaner.instance(), moduleContext);
        }

        moduleContext.setBiddersToEnrich(biddersToEnrich);
        final Future<TargetingResult> optableTargetingCall = networkCall.makeRequest(
                payload,
                invocationContext,
                properties);

        moduleContext.setOptableTargetingCall(optableTargetingCall);

        return updateModuleContext(moduleContext);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> updateModuleContext(ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .analyticsTags(AnalyticTagsResolver.toEnrichRequestAnalyticTags(moduleContext))
                        .moduleContext(moduleContext)
                        .build());
    }

    public static <T> Future<InvocationResult<T>> update(
            PayloadUpdate<T> payloadUpdate,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<T>builder()
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
