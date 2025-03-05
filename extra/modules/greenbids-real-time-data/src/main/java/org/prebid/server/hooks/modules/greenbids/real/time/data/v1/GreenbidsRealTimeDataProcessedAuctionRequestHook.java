package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.prebid.server.analytics.reporter.greenbids.model.ExplorationResult;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.Rejected;
import org.prebid.server.auction.model.RejectedImp;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInvocationResultCreator;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsPayloadUpdater;
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids-rtd";
    private static final String CODE = "greenbids-real-time-data-processed-auction-request";
    private static final String ACTIVITY = "greenbids-filter";
    private static final String SUCCESS_STATUS = "success";

    private final ObjectMapper mapper;
    private final FilterService filterService;
    private final OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds;
    private final GreenbidsInferenceDataService greenbidsInferenceDataService;

    public GreenbidsRealTimeDataProcessedAuctionRequestHook(
            ObjectMapper mapper,
            FilterService filterService,
            OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds,
            GreenbidsInferenceDataService greenbidsInferenceDataService) {

        this.mapper = Objects.requireNonNull(mapper);
        this.filterService = Objects.requireNonNull(filterService);
        this.onnxModelRunnerWithThresholds = Objects.requireNonNull(onnxModelRunnerWithThresholds);
        this.greenbidsInferenceDataService = Objects.requireNonNull(greenbidsInferenceDataService);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final BidRequest bidRequest = auctionRequestPayload.bidRequest();
        final GreenbidsConfig greenbidsConfig = Optional.ofNullable(parseBidRequestExt(bidRequest.getExt()))
                .orElseGet(() -> toGreenbidsConfig(invocationContext.accountConfig()));

        if (greenbidsConfig == null) {
            return Future.failedFuture(new PreBidException("Greenbids config is null; cannot proceed."));
        }

        return Future.all(
                        onnxModelRunnerWithThresholds.retrieveOnnxModelRunner(greenbidsConfig),
                        onnxModelRunnerWithThresholds.retrieveThreshold(greenbidsConfig))
                .compose(compositeFuture -> toInvocationResult(
                        bidRequest,
                        greenbidsConfig,
                        compositeFuture.resultAt(0),
                        compositeFuture.resultAt(1)))
                .recover(throwable -> noActionInvocationResult());
    }

    private GreenbidsConfig parseBidRequestExt(ExtRequest extRequest) {
        return Optional.ofNullable(extRequest)
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
            return noActionInvocationResult();
        }

        final GreenbidsInvocationResult invocationResult = GreenbidsInvocationResultCreator.create(
                greenbidsConfig,
                bidRequest,
                impsBiddersFilterMap);

        return invocationResult.getInvocationAction() == InvocationAction.no_action
                ? noActionInvocationResult(invocationResult.getAnalyticsResult())
                : toInvocationResult(bidRequest, impsBiddersFilterMap, invocationResult);
    }

    private Future<InvocationResult<AuctionRequestPayload>> toInvocationResult(
            BidRequest bidRequest,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap,
            GreenbidsInvocationResult invocationResult) {

        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(invocationResult.getInvocationAction())
                .payloadUpdate(payload -> AuctionRequestPayloadImpl.of(GreenbidsPayloadUpdater.update(bidRequest, impsBiddersFilterMap)))
                .analyticsTags(toAnalyticsTags(invocationResult.getAnalyticsResult()))
                .rejections(toRejections(impsBiddersFilterMap))
                .build());
    }

    private Future<InvocationResult<AuctionRequestPayload>> noActionInvocationResult(AnalyticsResult analyticsResult) {
        return Future.succeededFuture(InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_action)
                .analyticsTags(toAnalyticsTags(analyticsResult))
                .build());
    }

    private Future<InvocationResult<AuctionRequestPayload>> noActionInvocationResult() {
        return noActionInvocationResult(null);
    }

    private Tags toAnalyticsTags(AnalyticsResult analyticsResult) {
        if (analyticsResult == null) {
            return null;
        }

        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                ACTIVITY,
                SUCCESS_STATUS,
                toResults(analyticsResult))));
    }

    private List<Result> toResults(AnalyticsResult analyticsResult) {
        return analyticsResult.getValues().entrySet().stream()
                .map(entry -> toResult(analyticsResult.getStatus(), entry))
                .toList();
    }

    private Result toResult(String status, Map.Entry<String, Ortb2ImpExtResult> entry) {
        final String impId = entry.getKey();
        final Ortb2ImpExtResult ortb2ImpExtResult = entry.getValue();
        final List<String> removedBidders = Optional.ofNullable(ortb2ImpExtResult)
                .map(Ortb2ImpExtResult::getGreenbids)
                .map(ExplorationResult::getKeptInAuction)
                .map(Map::entrySet)
                .stream()
                .flatMap(Collection::stream)
                .filter(e -> BooleanUtils.isFalse(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        return ResultImpl.of(
                status,
                toObjectNode(entry),
                AppliedToImpl.builder()
                        .impIds(Collections.singletonList(impId))
                        .bidders(removedBidders.isEmpty() ? null: removedBidders)
                        .build());
    }

    private ObjectNode toObjectNode(Map.Entry<String, Ortb2ImpExtResult> values) {
        return values != null ? mapper.valueToTree(values) : null;
    }

    private Map<String, List<Rejected>> toRejections(Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        return impsBiddersFilterMap.entrySet().stream()
                .flatMap(entry -> Stream.ofNullable(entry.getValue())
                        .map(Map::entrySet)
                        .flatMap(Collection::stream)
                        .filter(e -> BooleanUtils.isFalse(e.getValue()))
                        .map(Map.Entry::getKey)
                        .map(bidder -> Pair.of(
                                bidder,
                                RejectedImp.of(entry.getKey(), BidRejectionReason.REQUEST_BLOCKED_OPTIMIZED))))
                .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
    }

    @Override
    public String code() {
        return CODE;
    }
}
