package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Storage;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsInferenceData;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunnerWithThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.GreenbidsInvocationResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.result.Ortb2ImpExtResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.model.analytics.TagsImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "greenbids-real-time-data-processed-auction-request-hook";
    private static final String ACTIVITY = "greenbids-filter";
    private static final String SUCCESS_STATUS = "success";
    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids-rtd";

    private final ObjectMapper mapper;
    private final JacksonMapper jacksonMapper;
    private final Cache<String, OnnxModelRunner> modelCacheWithExpiration;
    private final Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;
    private final String gcsBucketName;
    private final String onnxModelCacheKeyPrefix;
    private final String thresholdsCacheKeyPrefix;
    private final Storage storage;
    private final File database;

    public GreenbidsRealTimeDataProcessedAuctionRequestHook(
            ObjectMapper mapper,
            Cache<String, OnnxModelRunner> modelCacheWithExpiration,
            Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration,
            String gcsBucketName,
            String onnxModelCacheKeyPrefix,
            String thresholdsCacheKeyPrefix,
            Storage storage,
            File database) {
        this.mapper = Objects.requireNonNull(mapper);
        this.jacksonMapper = new JacksonMapper(mapper);
        this.modelCacheWithExpiration = modelCacheWithExpiration;
        this.thresholdsCacheWithExpiration = thresholdsCacheWithExpiration;
        this.gcsBucketName = gcsBucketName;
        this.onnxModelCacheKeyPrefix = onnxModelCacheKeyPrefix;
        this.thresholdsCacheKeyPrefix = thresholdsCacheKeyPrefix;
        this.storage = storage;
        this.database = database;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload,
            AuctionInvocationContext invocationContext) {

        final AuctionContext auctionContext = invocationContext.auctionContext();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Partner partner = parseBidRequestExt(bidRequest);

        if (partner == null) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        OnnxModelRunner onnxModelRunner = null;
        ThrottlingThresholds throttlingThresholds = null;
        Map<String, Map<String, Boolean>> impsBiddersFilterMap = null;
        try {
            final OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds = new OnnxModelRunnerWithThresholds(
                    jacksonMapper,
                    modelCacheWithExpiration,
                    thresholdsCacheWithExpiration,
                    storage,
                    gcsBucketName,
                    onnxModelCacheKeyPrefix,
                    thresholdsCacheKeyPrefix);
            onnxModelRunner = onnxModelRunnerWithThresholds.retrieveOnnxModelRunner(partner);
            throttlingThresholds = onnxModelRunnerWithThresholds.retrieveThreshold(partner);

            if (onnxModelRunner == null || throttlingThresholds == null) {
                throw new PreBidException("Cache was empty, fetching and put artefacts for next request");
            }

            final GreenbidsInferenceData greenbidsInferenceData = GreenbidsInferenceData.prepareData(
                    bidRequest, database, jacksonMapper);

            final FilterService filterService = new FilterService();
            final Double threshold = partner.getThreshold(throttlingThresholds);
            impsBiddersFilterMap = filterService.runModeAndFilterBidders(
                    onnxModelRunner,
                    greenbidsInferenceData.getThrottlingMessages(),
                    greenbidsInferenceData.getThrottlingInferenceRows(),
                    threshold);
        } catch (PreBidException e) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        final GreenbidsInvocationResult greenbidsInvocationResult = GreenbidsInvocationResult.prepareInvocationResult(
                partner, bidRequest, impsBiddersFilterMap);

        return Future.succeededFuture(toInvocationResult(
                greenbidsInvocationResult.getUpdatedBidRequest(),
                greenbidsInvocationResult.getAnalyticsResult(),
                greenbidsInvocationResult.getInvocationAction()));
    }

    private Partner parseBidRequestExt(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAnalytics)
                .filter(this::isNotEmptyObjectNode)
                .map(analytics -> (ObjectNode) analytics.get(BID_REQUEST_ANALYTICS_EXTENSION_NAME))
                .map(this::toPartner)
                .orElse(null);
    }

    private boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
    }

    private Partner toPartner(ObjectNode adapterNode) {
        try {
            return jacksonMapper.mapper().treeToValue(adapterNode, Partner.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding bid request analytics extension: " + e.getMessage(), e);
        }
    }

    private InvocationResult<AuctionRequestPayload> toInvocationResult(
            BidRequest bidRequest,
            AnalyticsResult analyticsResult,
            InvocationAction action) {

        final List<AnalyticsResult> analyticsResults = (analyticsResult != null)
                ? Collections.singletonList(analyticsResult)
                : Collections.emptyList();

        return InvocationResultImpl
                .<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(action)
                .errors(null)
                .debugMessages(null)
                .payloadUpdate(payload -> AuctionRequestPayloadImpl.of(bidRequest))
                .analyticsTags(toAnalyticsTags(analyticsResults))
                .build();
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
                .map(this::toResult)
                .toList();
    }

    private Result toResult(AnalyticsResult analyticsResult) {
        return ResultImpl.of(
                analyticsResult.getStatus(),
                toObjectNode(analyticsResult.getValues()),
                AppliedToImpl.builder()
                        .bidders(Collections.singletonList(analyticsResult.getBidder()))
                        .impIds(Collections.singletonList(analyticsResult.getImpId()))
                        .build());
    }

    private ObjectNode toObjectNode(Map<String, Ortb2ImpExtResult> values) {
        return values != null ? mapper.valueToTree(values) : null;
    }

    @Override
    public String code() {
        return CODE;
    }
}
