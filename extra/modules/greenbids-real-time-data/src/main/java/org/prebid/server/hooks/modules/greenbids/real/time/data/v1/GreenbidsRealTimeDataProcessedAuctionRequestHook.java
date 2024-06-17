package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public GreenbidsRealTimeDataProcessedAuctionRequestHook(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.jacksonMapper = new JacksonMapper(mapper);
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

        System.out.println(
                "GreenbidsRealTimeDataProcessedAuctionRequestHook/call" + "\n" +
                        "greenbidsUserAgent: " + greenbidsUserAgent + "\n" + // OK
                        "device: " + greenbidsUserAgent.getDevice() + "\n" + // OK
                        "browser: " + greenbidsUserAgent.getBrowser() + "\n" + // OK
                        "isPC: " + greenbidsUserAgent.isPC() + "\n" + // OK
                        "isBot: " + greenbidsUserAgent.isBot() + "\n" // OK
        );

        final BidRequest bidRequest = auctionContext.getBidRequest();

        List<ThrottlingMessage> throttlingMessages = extractThrottlingMessages(
                bidRequest,
                greenbidsUserAgent,
                hourBucket,
                minuteQuadrant);

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
                                            .hourBucket(hourBucket)
                                            .minuteQuadrant(minuteQuadrant)
                                            .adUnitCode(impId)
                                            .bidder(bidderName)
                                            .hostname(hostname)
                                            .device(greenbidsUserAgent.getDevice())
                                            .browser(greenbidsUserAgent.getBrowser())
                                            .isPc(greenbidsUserAgent.isPC())
                                            .isBot(greenbidsUserAgent.isBot())
                                            .isMobile(false)
                                            .isTablet(false)
                                            .isTouchCapable(false)
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

    @Override
    public String code() {
        return CODE;
    }
}
