package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.AnalyticsResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ExplorationResult;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.GreenbidsUserAgent;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.Ort2ImpExtResult;
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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "greenbids-real-time-data-processed-auction-request-hook";
    private static final String ACTIVITY = "greenbids-filter";
    private static final String SUCCESS_STATUS = "success";
    private static final Double EXPLORATION_RATE = 0.0001;
    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;

    private final ObjectMapper mapper;

    private final JacksonMapper jacksonMapper;

    private final OnnxModelRunner modelRunner;

    public GreenbidsRealTimeDataProcessedAuctionRequestHook(ObjectMapper mapper, OnnxModelRunner modelRunner) {
        this.mapper = Objects.requireNonNull(mapper);
        this.jacksonMapper = new JacksonMapper(mapper);
        this.modelRunner = modelRunner;
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(
            AuctionRequestPayload auctionRequestPayload,
            AuctionInvocationContext invocationContext) {

        AuctionContext auctionContext = invocationContext.auctionContext();

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer hourBucket = timestamp.getHour();
        final Integer minuteQuadrant = (timestamp.getMinute() / 15) + 1;

        //String userAgentStirng = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        String userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
        GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgentString);

        final BidRequest bidRequest = auctionContext.getBidRequest();

        List<ThrottlingMessage> throttlingMessages = extractThrottlingMessages(
                bidRequest,
                greenbidsUserAgent,
                hourBucket,
                minuteQuadrant);

        String[][] throttlingInferenceRow = convertToArray(throttlingMessages);

        System.out.println(
                "GreenbidsRealTimeDataProcessedAuctionRequestHook/call" + "\n" +
                        "greenbidsUserAgent: " + greenbidsUserAgent + "\n" +
                        "device: " + greenbidsUserAgent.getDevice() + "\n" +
                        "browser: " + greenbidsUserAgent.getBrowser() + "\n" +
                        "isPC: " + greenbidsUserAgent.isPC() + "\n" +
                        "isBot: " + greenbidsUserAgent.isBot() + "\n" +
                        "throttlingMessages: " + throttlingMessages + "\n"
        );

        for (String[] row : throttlingInferenceRow) {
            for (String col : row) {
                System.out.print(col + " ");
            }
            System.out.println();
        }

        // measure inf time
        long startTime = System.nanoTime();

        OrtSession.Result results;
        try {
            results = modelRunner.runModel(throttlingInferenceRow);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

        long endTime = System.nanoTime();
        long duration = (endTime - startTime); // in nanoseconds
        System.out.println("Inference time: " + duration / 1000000.0 + " ms");

        //double threshold = 0.5;
        List<Double> threshold = Arrays.asList(0.1, 0.9, 0.2);
        Map<String, Map<String, Boolean>> impsBiddersFilterMap = new HashMap<>();

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
                            boolean isKeptInAuction = probas[i][1] > threshold.get(i);

                            impsBiddersFilterMap.computeIfAbsent(impId, k -> new HashMap<>())
                                    .put(bidder, isKeptInAuction);
                        }
                    } catch (OrtException e) {
                        throw new RuntimeException(e);
                    }
                });

        // exploration result for analytics adapter
        final String greenbidsId = UUID.randomUUID().toString(); //"test-greenbids-id"; //UUID.randomUUID().toString();
        final int hashInt = Integer.parseInt(
                greenbidsId.substring(greenbidsId.length() - 4), 16);
        final boolean isExploration = hashInt < EXPLORATION_RATE * RANGE_16_BIT_INTEGER_DIVISION_BASIS;

        // update Bid Request with filtered bidders
        List<Imp> impsWithFilteredBidders = updateImps(bidRequest, impsBiddersFilterMap);
        BidRequest updatedBidRequest = isExploration
                ? bidRequest.toBuilder().imp(impsWithFilteredBidders).build()
                : bidRequest;


        final Map<String, Ort2ImpExtResult> ort2ImpExtResultMap = createOrtb2ImpExt(
                bidRequest, impsBiddersFilterMap, greenbidsId, isExploration);
        final AnalyticsResult analyticsResult = AnalyticsResult.of(
                "success", ort2ImpExtResultMap, null, null);

        // update invocation result
        InvocationResult<AuctionRequestPayload> invocationResult = InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.update)
                .errors(null)
                .debugMessages(null)
                .analyticsTags(null)
                .payloadUpdate(payload -> AuctionRequestPayloadImpl.of(updatedBidRequest))
                .analyticsTags(toAnalyticsTags(Collections.singletonList(analyticsResult)))
                .build();

        System.out.println(
                "GreenbidsRealTimeDataProcessedAuctionRequestHook/call" + "\n" +
                        "impsBiddersFilterMap: " + impsBiddersFilterMap + "\n" +
                        "impsWithFilteredBidders: " + impsWithFilteredBidders + "\n" +
                        //"updatedBidRequest: " + updatedBidRequest + "\n" +
                        "AnalyticsTag: " + toAnalyticsTags(Collections.singletonList(analyticsResult)) + "\n" +
                        "invocationResult: " + invocationResult + "\n" +
                        "ort2ImpExtResultMap: " + ort2ImpExtResultMap + "\n" +
                        "analyticsResult: " + analyticsResult
        );

        return Future.succeededFuture(invocationResult);
    }

    private Map<String, Ort2ImpExtResult> createOrtb2ImpExt(
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
                            return Ort2ImpExtResult.of(
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

    private ObjectNode toObjectNode(Map<String, Ort2ImpExtResult> values) {
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
                                            .country("UK")
                                            .hostname(hostname)
                                            .device(greenbidsUserAgent.getDevice())
                                            //.isPc(greenbidsUserAgent.isPC())
                                            //.isBot(greenbidsUserAgent.isBot())
                                            .isMobile("False")
                                            .isTablet("False")
                                            //.isTouchCapable(false)
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

    private static String[][] convertToArray(List<ThrottlingMessage> messages) {
        String[][] result = new String[messages.size()][10];
        for (int i = 0; i < messages.size(); i++) {
            ThrottlingMessage message = messages.get(i);
            result[i][0] = message.getBrowser();
            result[i][1] = message.getBidder();
            result[i][2] = message.getAdUnitCode();
            result[i][3] = message.getCountry();
            result[i][4] = message.getHostname();
            result[i][5] = message.getDevice();
            result[i][6] = message.getIsMobile();
            result[i][7] = message.getIsTablet();
            result[i][8] = message.getHourBucket();
            result[i][9] = message.getMinuteQuadrant();
        }
        return result;
    }

    @Override
    public String code() {
        return CODE;
    }
}
