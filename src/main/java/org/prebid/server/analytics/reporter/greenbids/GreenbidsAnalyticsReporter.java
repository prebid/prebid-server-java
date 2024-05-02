package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBidder;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsConfig;
import org.prebid.server.analytics.reporter.greenbids.model.HttpUtil;
import org.prebid.server.analytics.reporter.greenbids.model.JsonUtil;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.UUID.randomUUID;

public class GreenbidsAnalyticsReporter implements AnalyticsReporter {

    private static final Logger logger = LoggerFactory.getLogger(GreenbidsAnalyticsReporter.class.getName());
    private static final String ANALYTICS_SERVER = "https://a.greenbids.ai/";
    public String pbuid;
    public Double greenbidsSampling;
    public Double exploratorySamplingSplit;
    private GreenbidsConfig greenbidsConfig;
    private final JacksonMapper jacksonMapper;
    public Boolean isSampled;
    public String greenbidsId;
    public String billingId;


    public GreenbidsAnalyticsReporter(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            JacksonMapper jacksonMapper
    ) {
        this.pbuid = Objects.requireNonNull(greenbidsAnalyticsProperties.getPbuid());
        this.greenbidsSampling = greenbidsAnalyticsProperties.getGreenbidsSampling();
        this.exploratorySamplingSplit = 0.9;
        this.greenbidsConfig = GreenbidsConfig.of(
                greenbidsAnalyticsProperties.getPbuid(),
                greenbidsAnalyticsProperties.getGreenbidsSampling()
                //greenbidsAnalyticsProperties.getExploratorySamplingSplit()
        );
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.isSampled = null;
        this.greenbidsId = randomUUID().toString();
        this.billingId = null;
    }


    public CommonMessage createCommonMessage(AuctionContext auctionContext, Long auctionElapsed) {
        return new CommonMessage(
                auctionContext,
                greenbidsConfig,
                auctionElapsed,
                greenbidsId,
                billingId
        );
    }

    private String getGpid(ObjectNode impExt) {
        //final JsonNode gpidNode = impExt.get("gpid");
        //return gpidNode != null && gpidNode.isObject() ? gpidNode.asText() : null;
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("prebid"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return jacksonMapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    private String storedRequestId(ObjectNode impExt) {
        //final ExtImpPrebid extImpPrebid = extImpPrebid(impExt.get("prebid"));
        //final ExtStoredRequest storedRequest = extImpPrebid != null ? extImpPrebid.getStoredrequest() : null;
        //return storedRequest != null ? storedRequest.getId() : null;
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("prebid"))
                .map(this::extImpPrebid)
                .map(ExtImpPrebid::getStoredrequest)
                .map(ExtStoredRequest::getId)
                .orElse(null);
    }

    private String getAdUnitCode(Imp imp) {
        ObjectNode impExt = imp.getExt();

        //String gpid = getGpid(impExt);
        //if (gpid!= null) {
        //    return gpid;
        //}

        //String storedRequestId = storedRequestId(impExt);
        //if (storedRequestId!= null) {
        //    return storedRequestId;
        //}

        String adUnitCodeStoredRequestId = Optional.ofNullable(getGpid(impExt))
                .orElseGet(() -> storedRequestId(impExt));

        if (adUnitCodeStoredRequestId!= null) {
                return adUnitCodeStoredRequestId;
        }

        return imp.getId();
    }

    public CommonMessage addBidResponseToMessage(
            CommonMessage commonMessage,
            List<Imp> imps,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids
    ) {
        commonMessage.adUnits = imps.stream().map(imp -> {

            // extract media types
            Banner banner = imp.getBanner();
            Video video = imp.getVideo();
            Native nativeObject = imp.getXNative();

            Integer width = banner.getFormat().get(0).getW();
            Integer height = banner.getFormat().get(0).getH();

            ExtBanner extBanner = ExtBanner.builder()
                    .sizes(width != null && height != null ? Arrays.asList(Arrays.asList(width, height), Arrays.asList(width, height)) : null)
                    .pos(banner.getPos())
                    .name(banner.getId())
                    .build();

            MediaTypes mediaTypes = MediaTypes.builder()
                    .banner(extBanner)
                    .video(video)
                    .nativeObject(nativeObject)
                    .build();

            // extract bidders;
            List<GreenbidsBidder> bidders = new ArrayList<>();

            // filter bidders in imp
            Map<String, Bid> seatsWithBidsForImp = seatsWithBids.entrySet().stream()
                    .filter(entry -> entry.getValue().getImpid().equals(imp.getId()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            // filter non bidders in imp
            Map<String, NonBid> seatsWithNonBidsForImp = seatsWithNonBids.entrySet().stream()
                    .filter(entry -> entry.getValue().getImpId().equals(imp.getId()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            seatsWithBidsForImp.forEach((seat, bid) -> {
                GreenbidsBidder bidder = GreenbidsBidder.builder()
                        .bidder(seat)
                        .isTimeout(false)
                        .hasBid(bid != null)
                        .build();
                bidders.add(bidder);
            });

            seatsWithNonBidsForImp.forEach((seat, nonBid) -> {
                GreenbidsBidder bidder = GreenbidsBidder.builder()
                        .bidder(seat)
                        .isTimeout(nonBid.getStatusCode().code == BidRejectionReason.TIMED_OUT.code)
                        .hasBid(false)
                        .build();
                bidders.add(bidder);
            });

            System.out.println(
                    "[TEST] GreenbidsAnalyticsReporter/addBidResponseToMessage " +
                            "\n   commonMessage: " + commonMessage +
                            "\n   imp: " + imp +
                            "\n   seatsWithBidsForImp: " + seatsWithBidsForImp +
                            "\n   seatsWithNonBidsForImp: " + seatsWithNonBidsForImp +
                            "\n   bidders: " + bidders +
                            "\n   mediaTypes: " + mediaTypes
            );

            // fallback adunitcode
            String adUnitCode = getAdUnitCode(imp);

            return AdUnit.builder()
                    .code(adUnitCode)
                    .mediaTypes(mediaTypes)
                    .bidders(bidders)
                    .build();
        }).toList();

        System.out.println(
                "[TEST] GreenbidsAnalyticsReporter/addBidResponseToMessageV2/ " +
                        "\n   commonMessage: " + commonMessage +
                        "\n   version: " + commonMessage.version +
                        "\n   auctionId: " + commonMessage.auctionId +
                        "\n   referrer: " + commonMessage.referrer +
                        "\n   sampling: " + commonMessage.sampling +
                        "\n   greenbidsId: " + commonMessage.greenbidsId +
                        "\n   pbuid: " + commonMessage.pbuid +
                        "\n   billingId: " + commonMessage.billingId +
                        "\n   adUnits: " + commonMessage.adUnits +
                        "\n   auctionElapsed: " + commonMessage.auctionElapsed
        );

        return commonMessage;
    }

    private static SeatNonBid toSeatNonBid(String bidder, BidRejectionTracker bidRejectionTracker) {
        final List<NonBid> nonBids = bidRejectionTracker.getRejectionReasons().entrySet().stream()
                .map(entry -> NonBid.of(entry.getKey(), entry.getValue()))
                .toList();

        return SeatNonBid.of(bidder, nonBids);
    }

    public CommonMessage createBidMessage(
            AuctionContext auctionContext,
            BidResponse bidResponse
    ){
        // get auctionId
        String auctionId = auctionContext.getBidRequest().getId();

        // get adunits
        List<Imp> imps = auctionContext.getBidRequest().getImp();

        if (imps == null || imps.isEmpty()) {
            throw new IllegalArgumentException("imps is null or empty");
        }

        // get auction timestamp
        //Long auctionTimestamp = auctionContext.getBidRequest().getExt().getPrebid().getAuctiontimestamp();
        Long auctionTimestamp = Optional.of(auctionContext.getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAuctiontimestamp)
                .orElse(null);

        long auctionElapsed = auctionTimestamp != null ? System.currentTimeMillis() - auctionTimestamp : 0L;

        // get bids
        Map<String, Bid> seatsWithBids = Optional.ofNullable(bidResponse.getSeatbid())
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(seatBid -> !seatBid.getBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatBid::getSeat,
                                seatBid -> seatBid.getBid().get(0),
                                (existing, replacement) -> existing
                        )
                );

        // get timeoutBids + nonBids
        List<SeatNonBid> seatNonBids = auctionContext.getBidRejectionTrackers().entrySet().stream()
                .map(entry -> toSeatNonBid(entry.getKey(), entry.getValue()))
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .toList();


        Map<String, NonBid> seatsWithNonBids = Optional.of(seatNonBids)
                .orElseGet(Collections::emptyList)
                .stream()
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatNonBid::getSeat,
                                seatNonBid -> seatNonBid.getNonBid().get(0),
                                (existing, replacement) -> existing
                        )
                );

        System.out.println(
                "[TEST] GreenbidsAnalyticsReporter/createBidMessage " +
                        "\n   auctionId: " + auctionId +
                        "\n   imps: " + imps +
                        "\n   auctionTimestamp: " + auctionTimestamp +
                        "\n   auctionElapsed: " + auctionElapsed +
                        "\n   seatsWithBids: " + seatsWithBids +
                        "\n   seatsWithNonBids: " + seatsWithNonBids
        );

        CommonMessage commonMessage = createCommonMessage(auctionContext, auctionElapsed);

        return addBidResponseToMessage(
                commonMessage,
                imps,
                seatsWithBids,
                seatsWithNonBids
        );
    }

    @Override
    public <T> Future<Void> processEvent(T event){
        AuctionContext greenbidsAuctionContext = null;
        BidResponse greenbidsBidResponse = null;

        if (event instanceof AmpEvent ampEvent) {
            greenbidsAuctionContext = ampEvent.getAuctionContext();
            greenbidsBidResponse = ampEvent.getBidResponse();
        } else if (event instanceof AuctionEvent auctionEvent) {
            greenbidsAuctionContext = auctionEvent.getAuctionContext();
            greenbidsBidResponse = auctionEvent.getBidResponse();
        }
        assert greenbidsBidResponse != null && greenbidsAuctionContext != null;

        this.greenbidsId = randomUUID().toString();
        this.isSampled = isSampled(greenbidsConfig.getGreenbidsSampling(), greenbidsId);

        System.out.println(
                "[TEST] GreenbidsAnalyticsReporter/processEvent " +
                        "\n   greenbidsId: " + greenbidsId +
                        "\n   isSampled: " + isSampled
        );

        CommonMessage commonMessage = createBidMessage(greenbidsAuctionContext, greenbidsBidResponse);

        String commonMessageJson = null;
        try {
            commonMessageJson = JsonUtil.toJson(commonMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println(
                "[TEST] GreenbidsAnalyticsReporter/processEventV2 " +
                        "\n   commonMessageJson: " + commonMessageJson
        );

        HttpUtil.sendJson(commonMessageJson, ANALYTICS_SERVER);

        return Future.succeededFuture();
    }


    public boolean isSampled(
            double samplingRate,
            String greenbidsId
    ) {
        if (samplingRate < 0 || samplingRate > 1) {
            System.out.println("Warning: Sampling rate must be between 0 and 1");
            return true;
        }

        double exploratorySamplingRate = samplingRate * this.exploratorySamplingSplit;
        double throttledSamplingRate  = samplingRate * (1.0 - this.exploratorySamplingSplit);

        long hashInt = Math.abs(greenbidsId.hashCode());
        hashInt = hashInt % 0x10000;

        boolean isPrimarySampled = hashInt < exploratorySamplingRate * 0xFFFF;
        if (isPrimarySampled) {
            return true;
        }
        return hashInt >= (1 - throttledSamplingRate) * 0xFFFF;
    }

    @Override
    public int vendorId() {
        return 0;
    }

    @Override
    public String name() {
        return "greenbids";
    }
}
