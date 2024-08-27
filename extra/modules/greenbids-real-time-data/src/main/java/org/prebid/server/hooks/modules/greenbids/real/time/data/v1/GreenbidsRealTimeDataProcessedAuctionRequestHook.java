package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.storage.Storage;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.Partner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.*;
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
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "greenbids-real-time-data-processed-auction-request-hook";
    private static final String ACTIVITY = "greenbids-filter";
    private static final String SUCCESS_STATUS = "success";
    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids-rtd";
    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;

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

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer hourBucket = timestamp.getHour();
        final Integer minuteQuadrant = (timestamp.getMinute() / 15) + 1;

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Partner partner = parseBidRequestExt(bidRequest);

        if (partner == null) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        final String userAgent = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .orElse(null);
        final GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgent);

        OnnxModelRunner onnxModelRunner = null;
        ThrottlingThresholds throttlingThresholds = null;
        try {
            onnxModelRunner = retrieveOnnxModelRunner(partner);
            throttlingThresholds = retrieveThreshold(partner);
        } catch (PreBidException e) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        if (onnxModelRunner == null || throttlingThresholds == null) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        final List<ThrottlingMessage> throttlingMessages = extractThrottlingMessages(
                bidRequest,
                greenbidsUserAgent,
                hourBucket,
                minuteQuadrant);

        final String[][] throttlingInferenceRows = convertToArray(throttlingMessages);

        if (isAnyFeatureNull(throttlingInferenceRows)) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        Double threshold = partner.getThreshold(throttlingThresholds);
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = runModeAndFilterBidders(
                onnxModelRunner, throttlingMessages, throttlingInferenceRows, threshold);

        final String greenbidsId = UUID.randomUUID().toString();
        final boolean isExploration = determineIsExploration(partner, greenbidsId);

        final List<Imp> impsWithFilteredBidders = updateImps(bidRequest, impsBiddersFilterMap);
        final BidRequest updatedBidRequest = !isExploration
                ? bidRequest.toBuilder().imp(impsWithFilteredBidders).build()
                : bidRequest;
        final InvocationAction invocationAction = !isExploration
                ? InvocationAction.update
                : InvocationAction.no_action;
        final Map<String, Map<String, Boolean>> impsBiddersFilterMapToAnalyticsTag = !isExploration
                ? impsBiddersFilterMap
                : keepAllBiddersForAnalyticsResult(impsBiddersFilterMap);

        final Map<String, Ortb2ImpExtResult> ort2ImpExtResultMap = createOrtb2ImpExt(
                bidRequest, impsBiddersFilterMapToAnalyticsTag, greenbidsId, isExploration);
        final AnalyticsResult analyticsResult = AnalyticsResult.of(
                "success", ort2ImpExtResultMap, null, null);

        return Future.succeededFuture(toInvocationResult(
                updatedBidRequest, analyticsResult, invocationAction));
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

    private OnnxModelRunner retrieveOnnxModelRunner(Partner partner) {
        final String onnxModelPath = "models_pbuid=" + partner.getPbuid() + ".onnx";
        final ModelCache modelCache = new ModelCache(
                onnxModelPath,
                storage,
                gcsBucketName,
                modelCacheWithExpiration,
                onnxModelCacheKeyPrefix);
        return modelCache.getModelRunner(partner.getPbuid());
    }

    private ThrottlingThresholds retrieveThreshold(Partner partner) {
        final String thresholdJsonPath = "thresholds_pbuid=" + partner.getPbuid() + ".json";
        final ThresholdCache thresholdCache = new ThresholdCache(
                thresholdJsonPath,
                storage,
                gcsBucketName,
                mapper,
                thresholdsCacheWithExpiration,
                thresholdsCacheKeyPrefix);
        return thresholdCache.getThrottlingThresholds(partner.getPbuid());
    }

    private List<ThrottlingMessage> extractThrottlingMessages(
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            Integer hourBucket,
            Integer minuteQuadrant) {
        final String hostname = bidRequest.getSite().getDomain();
        final List<Imp> imps = bidRequest.getImp();

        return imps.stream()
                .flatMap(imp -> {
                    final String impId = imp.getId();
                    final ObjectNode impExt = imp.getExt();
                    final JsonNode bidderNode = extImpPrebid(impExt.get("prebid")).getBidder();

                    final String ipv4 = Optional.ofNullable(bidRequest.getDevice())
                            .map(Device::getIp)
                            .orElse(null);
                    final String countryFromIp;
                    try {
                        countryFromIp = getCountry(ipv4);
                    } catch (IOException | GeoIp2Exception e) {
                        throw new PreBidException("Failed to get country for IP", e);
                    }

                    final List<ThrottlingMessage> throttlingImpMessages = new ArrayList<>();
                    if (bidderNode.isObject()) {
                        final ObjectNode bidders = (ObjectNode) bidderNode;
                        final Iterator<String> fieldNames = bidders.fieldNames();
                        while (fieldNames.hasNext()) {
                            final String bidderName = fieldNames.next();
                            throttlingImpMessages.add(
                                    ThrottlingMessage.builder()
                                            .browser(greenbidsUserAgent.getBrowser())
                                            .bidder(bidderName)
                                            .adUnitCode(impId)
                                            .country(countryFromIp)
                                            .hostname(hostname)
                                            .device(greenbidsUserAgent.getDevice())
                                            .hourBucket(hourBucket.toString())
                                            .minuteQuadrant(minuteQuadrant.toString())
                                            .build());
                        }
                    }
                    return throttlingImpMessages.stream();
                })
                .collect(Collectors.toList());
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return jacksonMapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    private String getCountry(String ip) throws IOException, GeoIp2Exception {
        return Optional.ofNullable(ip)
                .map(ipAddress -> {
                    try {
                        final DatabaseReader dbReader = new DatabaseReader.Builder(database).build();
                        final InetAddress inetAddress = InetAddress.getByName(ipAddress);
                        final CountryResponse response = dbReader.country(inetAddress);
                        final Country country = response.getCountry();
                        return country.getName();
                    } catch (IOException | GeoIp2Exception e) {
                        throw new PreBidException("Failed to fetch country from geoLite DB", e);
                    }
                }).orElse(null);
    }

    private String[][] convertToArray(List<ThrottlingMessage> messages) {
        return messages.stream()
                .map(message -> new String[]{
                        Optional.ofNullable(message.getBrowser()).orElse(""),
                        Optional.ofNullable(message.getBidder()).orElse(""),
                        Optional.ofNullable(message.getAdUnitCode()).orElse(""),
                        Optional.ofNullable(message.getCountry()).orElse(""),
                        Optional.ofNullable(message.getHostname()).orElse(""),
                        Optional.ofNullable(message.getDevice()).orElse(""),
                        Optional.ofNullable(message.getHourBucket()).orElse(""),
                        Optional.ofNullable(message.getMinuteQuadrant()).orElse("")})
                .toArray(String[][]::new);
    }

    private Boolean isAnyFeatureNull(String[][] throttlingInferenceRow) {
        return Arrays.stream(throttlingInferenceRow)
                .flatMap(Arrays::stream)
                .anyMatch(Objects::isNull);
    }

    private Map<String, Map<String, Boolean>> runModeAndFilterBidders(
            OnnxModelRunner onnxModelRunner,
            List<ThrottlingMessage> throttlingMessages,
            String[][] throttlingInferenceRows,
            Double threshold) {
        final Map<String, Map<String, Boolean>> impsBiddersFilterMap = new HashMap<>();
        final OrtSession.Result results;
        try {
            results = onnxModelRunner.runModel(throttlingInferenceRows);
            processModelResults(results, throttlingMessages, threshold, impsBiddersFilterMap);
        } catch (OrtException e) {
            throw new PreBidException("Exception during model inference: ", e);
        }
        return impsBiddersFilterMap;
    }

    private void processModelResults(
            OrtSession.Result results,
            List<ThrottlingMessage> throttlingMessages,
            Double threshold,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .forEach(onnxItem -> {
                    final OnnxValue onnxValue = onnxItem.getValue();
                    final OnnxTensor tensor = (OnnxTensor) onnxValue;
                    try {
                        final float[][] probas = (float[][]) tensor.getValue();
                        IntStream.range(0, probas.length)
                                .mapToObj(i -> {
                                    final ThrottlingMessage message = throttlingMessages.get(i);
                                    final String impId = message.getAdUnitCode();
                                    final String bidder = message.getBidder();
                                    final boolean isKeptInAuction = probas[i][1] > threshold;
                                    return Map.entry(impId, Map.entry(bidder, isKeptInAuction));
                                })
                                .forEach(entry -> impsBiddersFilterMap
                                        .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                                        .put(entry.getValue().getKey(), entry.getValue().getValue()));
                    } catch (OrtException e) {
                        throw new PreBidException("Exception when extracting proba from OnnxTensor: ", e);
                    }
                });
    }

    private Boolean determineIsExploration(Partner partner, String greenbidsId) {
        final int hashInt = Integer.parseInt(
                greenbidsId.substring(greenbidsId.length() - 4), 16);
        return hashInt < partner.getExplorationRate() * RANGE_16_BIT_INTEGER_DIVISION_BASIS;
    }

    private List<Imp> updateImps(BidRequest bidRequest, Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        return bidRequest.getImp().stream()
                .map(imp -> updateImp(imp, impsBiddersFilterMap.get(imp.getId())))
                .toList();
    }

    private Imp updateImp(Imp imp, Map<String, Boolean> bidderFilterMap) {
        return imp.toBuilder()
                .ext(updateImpExt(imp.getExt(), bidderFilterMap))
                .build();
    }

    private ObjectNode updateImpExt(ObjectNode impExt, Map<String, Boolean> bidderFilterMap) {
        final ObjectNode updatedExt = impExt.deepCopy();
        Optional.ofNullable((ObjectNode) updatedExt.get("prebid"))
                .map(prebidNode -> (ObjectNode) prebidNode.get("bidder"))
                .ifPresent(bidderNode ->
                        bidderFilterMap.entrySet().stream()
                                .filter(entry -> !entry.getValue())
                                .map(Map.Entry::getKey)
                                .forEach(bidderNode::remove));
        return updatedExt;
    }

    private Map<String, Map<String, Boolean>> keepAllBiddersForAnalyticsResult(
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        impsBiddersFilterMap.replaceAll((impId, biddersMap) ->
                biddersMap
                        .entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> true)));
        return impsBiddersFilterMap;
    }

    private Map<String, Ortb2ImpExtResult> createOrtb2ImpExt(
            BidRequest bidRequest,
            Map<String, Map<String, Boolean>> impsBiddersFilterMap,
            String greenbidsId,
            Boolean isExploration) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(
                        Imp::getId,
                        imp -> {
                            final String tid = imp.getExt().get("tid").asText();
                            final Map<String, Boolean> impBiddersFilterMap = impsBiddersFilterMap.get(imp.getId());
                            final ExplorationResult explorationResult = ExplorationResult.of(
                                    greenbidsId, impBiddersFilterMap, isExploration);
                            return Ortb2ImpExtResult.of(
                                    explorationResult, tid);
                        }));
    }

    private InvocationResult<AuctionRequestPayload> toInvocationResult(
            BidRequest bidRequest,
            AnalyticsResult analyticsResult,
            InvocationAction action
    ) {
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
