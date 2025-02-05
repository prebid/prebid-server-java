package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInvocationService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunnerWithThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsConfig;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountHooksConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids-rtd";
    private static final String CODE = "greenbids-real-time-data-processed-auction-request";
    private static final String ACTIVITY = "greenbids-filter";
    private static final String SUCCESS_STATUS = "success";

    private final ObjectMapper mapper;
    private final FilterService filterService;
    private final OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds;
    private final GreenbidsInferenceDataService greenbidsInferenceDataService;
    private final GreenbidsInvocationService greenbidsInvocationService;

    public GreenbidsRealTimeDataProcessedAuctionRequestHook(
            ObjectMapper mapper,
            FilterService filterService,
            OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds,
            GreenbidsInferenceDataService greenbidsInferenceDataService,
            GreenbidsInvocationService greenbidsInvocationService) {
        this.mapper = Objects.requireNonNull(mapper);
        this.filterService = Objects.requireNonNull(filterService);
        this.onnxModelRunnerWithThresholds = Objects.requireNonNull(onnxModelRunnerWithThresholds);
        this.greenbidsInferenceDataService = Objects.requireNonNull(greenbidsInferenceDataService);
        this.greenbidsInvocationService = Objects.requireNonNull(greenbidsInvocationService);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload,
            AuctionInvocationContext invocationContext) {

        final AuctionContext auctionContext = invocationContext.auctionContext();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final GreenbidsConfig greenbidsConfig = Optional.ofNullable(parseBidRequestExt(auctionContext))
                .orElseGet(() -> toGreenbidsConfig(invocationContext.accountConfig()));

        if (greenbidsConfig == null) {
            return Future.failedFuture(
                    new PreBidException("Greenbids config is null; cannot proceed."));
        }

        return Future.all(
                        onnxModelRunnerWithThresholds.retrieveOnnxModelRunner(greenbidsConfig),
                        onnxModelRunnerWithThresholds.retrieveThreshold(greenbidsConfig))
                .compose(compositeFuture -> toInvocationResult(
                        bidRequest,
                        greenbidsConfig,
                        compositeFuture.resultAt(0),
                        compositeFuture.resultAt(1)))
                .recover(throwable -> Future.succeededFuture(toInvocationResult(
                        bidRequest, null, InvocationAction.no_action)));
    }

    private GreenbidsConfig parseBidRequestExt(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getBidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAnalytics)
                .filter(this::isNotEmptyObjectNode)
                .map(analytics -> (ObjectNode) analytics.get(BID_REQUEST_ANALYTICS_EXTENSION_NAME))
                .map(this::toGreenbidsConfig)
                .orElse(null);
    }

    private boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
    }

    private GreenbidsConfig toGreenbidsConfig(ObjectNode greenbidsConfigNode) {
        try {
            return mapper.treeToValue(greenbidsConfigNode, GreenbidsConfig.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Future<InvocationResult<AuctionRequestPayload>> toInvocationResult(
            BidRequest bidRequest,
            GreenbidsConfig greenbidsConfig,
            OnnxModelRunner onnxModelRunner,
            Double threshold) {

        final Map<String, Map<String, Boolean>> impsBiddersFilterMap;
        try {
            final List<ThrottlingMessage> throttlingMessages = greenbidsInferenceDataService
                    .extractThrottlingMessagesFromBidRequest(bidRequest);

            impsBiddersFilterMap = filterService.filterBidders(
                    onnxModelRunner,
                    throttlingMessages,
                    threshold);
        } catch (PreBidException e) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        final GreenbidsInvocationResult greenbidsInvocationResult = greenbidsInvocationService
                .createGreenbidsInvocationResult(greenbidsConfig, bidRequest, impsBiddersFilterMap);

        return Future.succeededFuture(toInvocationResult(
                greenbidsInvocationResult.getUpdatedBidRequest(),
                greenbidsInvocationResult.getAnalyticsResult(),
                greenbidsInvocationResult.getInvocationAction()));
    }

    private InvocationResult<AuctionRequestPayload> toInvocationResult(
            BidRequest bidRequest,
            AnalyticsResult analyticsResult,
            InvocationAction action) {

        final List<AnalyticsResult> analyticsResults = analyticsResult != null
                ? Collections.singletonList(analyticsResult)
                : Collections.emptyList();

        return switch (action) {
            case InvocationAction.update -> InvocationResultImpl
                    .<AuctionRequestPayload>builder()
                    .status(InvocationStatus.success)
                    .action(action)
                    .payloadUpdate(payload -> AuctionRequestPayloadImpl.of(bidRequest))
                    .analyticsTags(toAnalyticsTags(analyticsResults))
                    .build();
            default -> InvocationResultImpl
                    .<AuctionRequestPayload>builder()
                    .status(InvocationStatus.success)
                    .action(action)
                    .analyticsTags(toAnalyticsTags(analyticsResults))
                    .build();
        };
    }

    private Tags toAnalyticsTags(List<AnalyticsResult> analyticsResults) {
        if (CollectionUtils.isEmpty(analyticsResults)) {
            return null;
        }

        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                ACTIVITY,
                SUCCESS_STATUS,
                toResults(analyticsResults))));
    }

    private List<Result> toResults(List<AnalyticsResult> analyticsResults) {
        return analyticsResults.stream()
                .flatMap(analyticsResult -> {
                    Map<String, Ortb2ImpExtResult> values = analyticsResult.getValues();

                    if (values == null || values.isEmpty()) {
                        return java.util.stream.Stream.empty();
                    }

                    return values.entrySet().stream().map(entry -> {
                        String impId = entry.getKey();
                        Ortb2ImpExtResult ortb2ImpExtResult = entry.getValue();
                        ObjectNode objectNode = toObjectNode(impId, ortb2ImpExtResult);

                        return (Result) ResultImpl.of(
                                analyticsResult.getStatus(),
                                objectNode,
                                AppliedToImpl.builder()
                                        .impIds(Collections.singletonList(impId))
                                        .build());
                    });
                })
                .toList();
    }

    private ObjectNode toObjectNode(String key, Ortb2ImpExtResult value) {
        ObjectNode root = mapper.createObjectNode();
        JsonNode node = value != null ? mapper.valueToTree(value) : null;
        root.set(key, node);
        return root;
    }

    @Override
    public String code() {
        return CODE;
    }
}
