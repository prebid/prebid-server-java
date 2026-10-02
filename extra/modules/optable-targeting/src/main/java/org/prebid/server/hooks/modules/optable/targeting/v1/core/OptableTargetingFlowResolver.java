package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class OptableTargetingFlowResolver {

    private static final String PREBID_STORED_REQUEST_PATH = "/prebid/storedrequest";

    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(
            LoggerFactory.getLogger(OptableTargetingProcessedAuctionRequestHook.class));

    private static final String AUCTION_NOT_PROPERLY_CONFIGURED =
            "Account not properly configured: tenant and/or origin is missing.";

    private final BidderEnrichmentSampler bidderEnrichmentSampler;
    private final TargetingRequestExecutor targetingRequestExecutor;
    private final CompositeHookExecutionPlan hooksExecutionPlan;
    private final double logSamplingRate;

    public OptableTargetingFlowResolver(BidderEnrichmentSampler bidderEnrichmentSampler,
                                        TargetingRequestExecutor targetingRequestExecutor,
                                        CompositeHookExecutionPlan hooksExecutionPlan,
                                        double logSamplingRate) {

        this.bidderEnrichmentSampler = Objects.requireNonNull(bidderEnrichmentSampler);
        this.targetingRequestExecutor = Objects.requireNonNull(targetingRequestExecutor);
        this.hooksExecutionPlan = hooksExecutionPlan;
        this.logSamplingRate = logSamplingRate;
    }

    public Future<InvocationResult<AuctionRequestPayload>> resolveAsyncOptableTargetingFlow(
            ModuleContext moduleContext,
            AuctionRequestPayload payload,
            AuctionInvocationContext invocationContext,
            OptableTargetingProperties properties,
            boolean cleanRequestOnFail) {

        final BidRequest bidRequest = invocationContext.auctionContext().getBidRequest();
        if (!isTrafficSourceValid(moduleContext, properties, cleanRequestOnFail, bidRequest)
                || hasStoredRequestOrImps(moduleContext, cleanRequestOnFail, bidRequest)) {
            return noEnrichment(cleanRequestOnFail, moduleContext);
        }

        final Set<String> biddersToEnrich = bidderEnrichmentSampler.sample(bidRequest, properties);
        if (CollectionUtils.isEmpty(biddersToEnrich)) {
            return noEnrichment(cleanRequestOnFail, moduleContext);
        }
        moduleContext.setBiddersToEnrich(biddersToEnrich);
        final Account account = invocationContext.auctionContext().getAccount();
        final long crossHookFutureTimeout =
                hooksExecutionPlan.getOptableTargetingBidderRequestTimeout(account);

        final Future<TargetingResult> optableTargetingCall = targetingRequestExecutor.makeRequest(
                payload,
                invocationContext,
                properties,
                crossHookFutureTimeout);

        moduleContext.setOptableTargetingCall(optableTargetingCall);

        return update(AuctionRequestCleaner.instance(), moduleContext);
    }

    private static boolean hasStoredRequestOrImps(ModuleContext moduleContext,
                                                  boolean cleanRequestOnFail,
                                                  BidRequest bidRequest) {

        if (!cleanRequestOnFail && ((hasStoredRequest(bidRequest) || hasStoredImps(bidRequest)))) {
            moduleContext.setEarlyCallInitializationCompleted(false);
            return true;
        }
        return false;
    }

    private static boolean isTrafficSourceValid(ModuleContext moduleContext,
                                                OptableTargetingProperties properties,
                                                boolean cleanRequestOnFail,
                                                BidRequest bidRequest) {

        if (!PropertiesValidator.isTrafficSourceValid(bidRequest, properties)) {
            if (cleanRequestOnFail) {
                moduleContext.setShouldSkipEnrichment(true);
            }
            moduleContext.setEarlyCallInitializationCompleted(false);
            return false;
        }
        return true;
    }

    private static boolean hasStoredImps(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getImp())
                .stream()
                .flatMap(Collection::stream)
                .map(Imp::getExt)
                .filter(Objects::nonNull)
                .anyMatch(impExt -> impExt.at(PREBID_STORED_REQUEST_PATH).isObject());
    }

    private static boolean hasStoredRequest(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getStoredrequest)
                .isPresent();
    }


    /**
     * @deprecated This call is deprecated and will be removed in a future release.
     */
    @Deprecated
    public Future<InvocationResult<AuctionRequestPayload>> resolveOptableTargetingFlow(
            AuctionRequestPayload auctionRequestPayload,
            AuctionInvocationContext invocationContext,
            ModuleContext moduleContext,
            OptableTargetingProperties properties) {

        if (moduleContext.isShouldSkipEnrichment()) {
            moduleContext.setOptableTargetingExecutionTime(calcAPICallExecutionTime(moduleContext));
            return updateWithAnalytics(AuctionRequestCleaner.instance(), moduleContext);
        }

        final Account account = invocationContext.auctionContext().getAccount();
        final boolean hasRawAuctionRequestHook = hooksExecutionPlan.hasRawAuctionRequestHook(account);
        final boolean hasBidderRequestHook = hooksExecutionPlan.hasBidderRequestHook(account);

        if (hasRawAuctionRequestHook && hasBidderRequestHook) {
            return updateWithAnalytics(AuctionRequestCleaner.instance(), moduleContext);
        }

        final Future<TargetingResult> optableTargetingCall = hasRawAuctionRequestHook
                ? resolveEarlyNetworkCall(moduleContext)
                : resolvePreEarlyNetworkCall(auctionRequestPayload, invocationContext, moduleContext, properties);

        if (optableTargetingCall == null) {
            moduleContext.failWithExecutionTime(calcAPICallExecutionTime(moduleContext));
            return updateWithAnalytics(AuctionRequestCleaner.instance(), moduleContext);
        }

        return optableTargetingCall
                .compose(targetingResult -> {
                    moduleContext.setOptableTargetingExecutionTime(calcAPICallExecutionTime(moduleContext));
                    return enrichPayload(targetingResult, moduleContext, properties);
                })
                .recover(throwable -> {
                    moduleContext.failWithExecutionTime(calcAPICallExecutionTime(moduleContext));
                    return updateWithAnalytics(AuctionRequestCleaner.instance(), moduleContext);
                });
    }

    private Future<InvocationResult<AuctionRequestPayload>> enrichPayload(
            TargetingResult targetingResult,
            ModuleContext moduleContext,
            OptableTargetingProperties properties) {

        moduleContext.setTargeting(targetingResult.getAudience());
        moduleContext.setId5Signature(Id5Resolver.resolveId5Signature(targetingResult));
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

        final PayloadUpdate<AuctionRequestPayload> payloadUpdate =
                AuctionRequestCleaner.instance().andThen(BidRequestEnricher.of(targetingResult, properties))::apply;

        return updateWithAnalytics(payloadUpdate, moduleContext);
    }

    private Future<TargetingResult> resolveEarlyNetworkCall(ModuleContext moduleContext) {
        return moduleContext.getOptableTargetingCall();
    }

    private static long calcAPICallExecutionTime(ModuleContext moduleContext) {
        return System.currentTimeMillis() - moduleContext.getCallTargetingAPITimestamp();
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

        return targetingRequestExecutor.makeRequest(
                payload,
                invocationContext,
                properties,
                null);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> update(
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

    private static Future<InvocationResult<AuctionRequestPayload>> updateWithAnalytics(
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

    public static Future<InvocationResult<AuctionRequestPayload>> success(ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(moduleContext)
                        .build());
    }

    private static Future<InvocationResult<AuctionRequestPayload>> noEnrichment(
            boolean cleanRequest, ModuleContext moduleContext) {

        return cleanRequest
                ? update(AuctionRequestCleaner.instance(), moduleContext)
                : success(moduleContext);
    }
}
