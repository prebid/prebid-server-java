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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.GreenbidsUserAgent;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.ThrottlingMessage;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class GreenbidsRealTimeDataProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "greenbids-real-time-data-processed-auction-request-hook";
    private static final String ACTIVITY = "isKeptInAuction";
    private static final String SUCCESS_STATUS = "success";
    //private static final List<Pattern> PHONE_PATTERNS =
    //        createPatterns("Phone", "iPhone", "Android.*Mobile", "Mobile.*Android");
    //private static final List<Pattern> TABLET_PATTERNS =
    //        createPatterns("tablet", "iPad", "Windows NT.*touch", "touch.*Windows NT", "Android");


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

        OrtSession.Result results;
        try {
            results = modelRunner.runModel(throttlingInferenceRow);
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }

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
                    } catch (OrtException e) {
                        throw new RuntimeException(e);
                    }
                });

        return Future.succeededFuture();
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
