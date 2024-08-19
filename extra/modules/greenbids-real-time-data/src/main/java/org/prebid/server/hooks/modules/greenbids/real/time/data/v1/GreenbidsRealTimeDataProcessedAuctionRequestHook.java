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
import com.google.cloud.storage.StorageOptions;
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
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ExplorationResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.GreenbidsUserAgent;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.Ortb2ImpExtResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ThresholdCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ThrottlingMessage;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "greenbids-real-time-data-processed-auction-request-hook";
    private static final String ACTIVITY = "greenbids-filter";
    private static final String SUCCESS_STATUS = "success";
    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids-rtd";
    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;

    private final ObjectMapper mapper;
    private final JacksonMapper jacksonMapper;
    private Cache<String, OnnxModelRunner> modelCacheWithExpiration;
    private Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration;
    private static String geoLiteCountryPath;
    private String googleCloudGreenbidsProject;
    private String gcsBucketName;

    public GreenbidsRealTimeDataProcessedAuctionRequestHook(
            ObjectMapper mapper,
            Cache<String, OnnxModelRunner> modelCacheWithExpiration,
            Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration,
            String geoLiteCountryPath,
            String googleCloudGreenbidsProject,
            String gcsBucketName) {
        this.mapper = Objects.requireNonNull(mapper);
        this.jacksonMapper = new JacksonMapper(mapper);
        this.modelCacheWithExpiration = modelCacheWithExpiration;
        this.thresholdsCacheWithExpiration = thresholdsCacheWithExpiration;
        this.geoLiteCountryPath = geoLiteCountryPath;
        this.googleCloudGreenbidsProject = googleCloudGreenbidsProject;
        this.gcsBucketName = gcsBucketName;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload,
            AuctionInvocationContext invocationContext) {

        AuctionContext auctionContext = invocationContext.auctionContext();

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer hourBucket = timestamp.getHour();
        final Integer minuteQuadrant = (timestamp.getMinute() / 15) + 1;

        // extract pbuid from BidRequest extension
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Partner partner = parseBidRequestExt(bidRequest);

        if (partner == null) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        // check if publisher activated RTD
        final String userAgent = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .orElse(null);
        GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgent);

        OnnxModelRunner onnxModelRunner = null;
        Double threshold = null;
        try {
            // select partner + extract threshold by TPR
            Storage storage = StorageOptions.newBuilder()
                    .setProjectId(googleCloudGreenbidsProject).build().getService();

            String onnxModelPath = "models_pbuid=" + partner.getPbuid() + ".onnx";
            ModelCache modelCache = new ModelCache(onnxModelPath, storage, gcsBucketName, modelCacheWithExpiration);
            onnxModelRunner = modelCache.getModelRunner(partner.getPbuid());

            String thresholdJsonPath = "thresholds_pbuid=" + partner.getPbuid() + ".json";
            ThresholdCache thresholdCache = new ThresholdCache(
                    thresholdJsonPath,
                    storage,
                    gcsBucketName,
                    mapper,
                    thresholdsCacheWithExpiration);
            ThrottlingThresholds throttlingThresholds = thresholdCache.getThrottlingThresholds(partner.getPbuid());
            threshold = partner.getThresholdForPartner(throttlingThresholds);
        } catch (PreBidException e) {
            System.out.println("Return Future.succeededFuture(): " + e);
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        List<ThrottlingMessage> throttlingMessages = extractThrottlingMessages(
                bidRequest,
                greenbidsUserAgent,
                hourBucket,
                minuteQuadrant);

        String[][] throttlingInferenceRow = convertToArray(throttlingMessages);

        System.out.println(
                "GreenbidsRealTimeDataProcessedAuctionRequestHook/call" + "\n" +
                        "partner: " + partner + "\n" +
                        "threshold: " + threshold + "\n" +
                        "onnxModelRunner: " + onnxModelRunner + "\n" +
                        "throttlingMessages: " + throttlingMessages + "\n"
        );

        for (String[] row : throttlingInferenceRow) {
            for (String col : row) {
                System.out.print(col + " ");
            }
            System.out.println();
        }

        if (isAnyFeatureNull(throttlingInferenceRow)) {
            return Future.succeededFuture(toInvocationResult(
                    bidRequest, null, InvocationAction.no_action));
        }

        OrtSession.Result results;
        try {
            results = onnxModelRunner.runModel(throttlingInferenceRow);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        Map<String, Map<String, Boolean>> impsBiddersFilterMap = new HashMap<>();
        final Double finalThreshold = threshold;
        StreamSupport.stream(results.spliterator(), false)
                .filter(onnxItem -> Objects.equals(onnxItem.getKey(), "probabilities"))
                .forEach(onnxItem -> {
                    System.out.println("Output: " + onnxItem.getKey() + ": " + onnxItem.getValue().toString());
                    OnnxValue onnxValue = onnxItem.getValue();
                    OnnxTensor tensor = (OnnxTensor) onnxValue;
                    try {
                        float[][] probas = (float[][]) tensor.getValue();
                        System.out.println(
                                "    tensor.getValue(): " + tensor.getValue() +
                                        "\n    probas: " + Arrays.deepToString(probas)
                        );

                        // process probas and create map
                        for (int i = 0; i < probas.length; i++) {
                            ThrottlingMessage message = throttlingMessages.get(i);
                            String impId = message.getAdUnitCode();
                            String bidder = message.getBidder();
                            boolean isKeptInAuction = probas[i][1] > finalThreshold;
                            impsBiddersFilterMap.computeIfAbsent(impId, k -> new HashMap<>())
                                    .put(bidder, isKeptInAuction);
                        }
                    } catch (OrtException e) {
                        throw new RuntimeException(e);
                    }
                });

        // exploration result for analytics adapter
        final String greenbidsId = UUID.randomUUID().toString();
        final int hashInt = Integer.parseInt(
                greenbidsId.substring(greenbidsId.length() - 4), 16);
        final boolean isExploration = hashInt < partner.getExplorationRate() * RANGE_16_BIT_INTEGER_DIVISION_BASIS;

        // update Bid Request with filtered bidders
        List<Imp> impsWithFilteredBidders = updateImps(bidRequest, impsBiddersFilterMap);
        BidRequest updatedBidRequest = !isExploration
                ? bidRequest.toBuilder().imp(impsWithFilteredBidders).build()
                : bidRequest;
        InvocationAction invocationAction = !isExploration ? InvocationAction.update : InvocationAction.no_action;
        Map<String, Map<String, Boolean>> impsBiddersFilterMapToAnalyticsTag = !isExploration
                ? impsBiddersFilterMap
                : keepAllBiddersForAnalyticsResult(impsBiddersFilterMap);

        final Map<String, Ortb2ImpExtResult> ort2ImpExtResultMap = createOrtb2ImpExt(
                bidRequest, impsBiddersFilterMapToAnalyticsTag, greenbidsId, isExploration);
        final AnalyticsResult analyticsResult = AnalyticsResult.of(
                "success", ort2ImpExtResultMap, null, null);

        System.out.println(
                "GreenbidsRealTimeDataProcessedAuctionRequestHook/call" + "\n" +
                        "   explorationRate: " + partner.getExplorationRate() + "\n" +
                        "   isExploration: " + isExploration + "\n" +
                        "   impsBiddersFilterMap: " + impsBiddersFilterMap + "\n" +
                        "   impsBiddersFilterMapToAnalyticsTag: " + impsBiddersFilterMapToAnalyticsTag + "\n" +
                        "   impsWithFilteredBidders: " + impsWithFilteredBidders + "\n" +
                        "   updatedBidRequest: " + updatedBidRequest + "\n" +
                        "   AnalyticsTag: " + toAnalyticsTags(Collections.singletonList(analyticsResult)) + "\n" +
                        "   ort2ImpExtResultMap: " + ort2ImpExtResultMap + "\n" +
                        "   analyticsResult: " + analyticsResult
        );

        return Future.succeededFuture(toInvocationResult(
                updatedBidRequest, analyticsResult, invocationAction));
    }

    private Boolean isAnyFeatureNull(String[][] throttlingInferenceRow) {
        return Arrays.stream(throttlingInferenceRow)
                .flatMap(Arrays::stream)
                .anyMatch(Objects::isNull);
    }

    private Map<String, Map<String, Boolean>> keepAllBiddersForAnalyticsResult(
            Map<String, Map<String, Boolean>> impsBiddersFilterMap) {
        for (Map.Entry<String, Map<String, Boolean>> impEntry: impsBiddersFilterMap.entrySet()) {
            Map<String, Boolean> biddersMap = impEntry.getValue();
            biddersMap.replaceAll((bidder, value) -> true);
        }
        return impsBiddersFilterMap;
    }

    private InvocationResult<AuctionRequestPayload> toInvocationResult(
            BidRequest bidRequest,
            AnalyticsResult analyticsResult,
            InvocationAction action
    ) {
        List<AnalyticsResult> analyticsResults = (analyticsResult != null)
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

    private Partner toPartner(ObjectNode adapterNode) {
        try {
            return jacksonMapper.mapper().treeToValue(adapterNode, Partner.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding bid request analytics extension: " + e.getMessage(), e);
        }
    }

    private boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
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
                            String tid = imp.getExt().get("tid").asText();
                            Map<String, Boolean> impBiddersFilterMap = impsBiddersFilterMap.get(imp.getId());
                            ExplorationResult explorationResult = ExplorationResult.of(
                                    greenbidsId, impBiddersFilterMap, isExploration);
                            return Ortb2ImpExtResult.of(
                                    explorationResult, tid);
                        }
                ));
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
        ObjectNode updatedExt = impExt.deepCopy();
        ObjectNode prebidNode = (ObjectNode) updatedExt.get("prebid");
        if (prebidNode != null) {
            ObjectNode bidderNode = (ObjectNode) prebidNode.get("bidder");
            if (bidderNode != null) {
                for(Map.Entry<String, Boolean> entry: bidderFilterMap.entrySet()) {
                    String bidderName = entry.getKey();
                    Boolean isKeptInAuction = entry.getValue();

                    if (!isKeptInAuction) {
                        bidderNode.remove(bidderName);
                    }
                }
            }
        }

        return updatedExt;
    }

    private List<ThrottlingMessage> extractThrottlingMessages(
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            Integer hourBucket,
            Integer minuteQuadrant) {
        final String hostname = bidRequest.getSite().getDomain();
        List<Imp> imps =  bidRequest.getImp();

        return imps.stream()
                .flatMap(imp -> {
                    final String impId = imp.getId();
                    final ObjectNode impExt = imp.getExt();
                    JsonNode bidderNode = extImpPrebid(impExt.get("prebid")).getBidder();

                    List<ThrottlingMessage> throttlingImpMessages = new ArrayList<>();

                    final String ipv4 = Optional.ofNullable(bidRequest.getDevice())
                            .map(Device::getIp)
                            .orElse(null);
                    String countryFromIp;
                    try {
                        countryFromIp = getCountry(ipv4);
                    } catch (IOException | GeoIp2Exception e) {
                        throw new RuntimeException(e);
                    }

                    System.out.println(
                            "extractThrottlingMessages" + "\n" +
                                    "countryFromIp: " + countryFromIp + "\n" +
                                    "greenbidsUserAgent.getBrowser(): " + greenbidsUserAgent.getBrowser() + "\n" +
                                    "greenbidsUserAgent.getDevice(): " + greenbidsUserAgent.getDevice() + "\n"
                    );

                    if (bidderNode.isObject()) {
                        ObjectNode bidders = (ObjectNode) bidderNode;
                        Iterator<String> fieldNames = bidders.fieldNames();
                        while (fieldNames.hasNext()) {
                            String bidderName = fieldNames.next();
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

    public static String getCountry(String ip) throws IOException, GeoIp2Exception {
        if (ip == null) {
            return null;
        }

        File database = new File(geoLiteCountryPath);
        DatabaseReader dbReader = new DatabaseReader.Builder(database).build();

        InetAddress ipAddress = InetAddress.getByName(ip);
        CountryResponse response = dbReader.country(ipAddress);
        Country country = response.getCountry();
        return country.getName();
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return jacksonMapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    private static String[][] convertToArray(List<ThrottlingMessage> messages) {
        String[][] result = new String[messages.size()][8];
        for (int i = 0; i < messages.size(); i++) {
            ThrottlingMessage message = messages.get(i);
            result[i][0] = message.getBrowser();
            result[i][1] = message.getBidder();
            result[i][2] = message.getAdUnitCode();
            result[i][3] = message.getCountry();
            result[i][4] = message.getHostname();
            result[i][5] = message.getDevice();
            result[i][6] = message.getHourBucket();
            result[i][7] = message.getMinuteQuadrant();
        }
        return result;
    }

    @Override
    public String code() {
        return CODE;
    }
}
