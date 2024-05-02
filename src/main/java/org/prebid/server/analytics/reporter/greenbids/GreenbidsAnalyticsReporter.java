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
import java.util.UUID;

public class GreenbidsAnalyticsReporter implements AnalyticsReporter {

    private static final String ANALYTICS_SERVER = "https://a.greenbids.ai/";
    public String pbuid;
    public Double greenbidsSampling;
    public Double exploratorySamplingSplit;
    private final GreenbidsConfig greenbidsConfig;
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
        );
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.isSampled = null;
        this.greenbidsId = null;
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
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("prebid"))
                .map(this::extImpPrebid)
                .map(ExtImpPrebid::getStoredrequest)
                .map(ExtStoredRequest::getId)
                .orElse(null);
    }

    private String getAdUnitCode(Imp imp) {
        final ObjectNode impExt = imp.getExt();

        final String adUnitCodeStoredRequestId = Optional.ofNullable(getGpid(impExt))
                .orElseGet(() -> storedRequestId(impExt));

        if (adUnitCodeStoredRequestId != null) {
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
            final Banner banner = imp.getBanner();
            final Video video = imp.getVideo();
            final Native nativeObject = imp.getXNative();

            final Integer width = banner.getFormat().get(0).getW();
            final Integer height = banner.getFormat().get(0).getH();

            final ExtBanner extBanner = ExtBanner.builder()
                    .sizes(
                            width != null && height != null ? Arrays.asList(
                                    Arrays.asList(width, height),
                                    Arrays.asList(width, height)
                            ) : null
                    )
                    .pos(banner.getPos())
                    .name(banner.getId())
                    .build();

            final MediaTypes mediaTypes = MediaTypes.builder()
                    .banner(extBanner)
                    .video(video)
                    .nativeObject(nativeObject)
                    .build();

            final List<GreenbidsBidder> bidders = new ArrayList<>();

            final Map<String, Bid> seatsWithBidsForImp = seatsWithBids.entrySet().stream()
                    .filter(entry -> entry.getValue().getImpid().equals(imp.getId()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            final Map<String, NonBid> seatsWithNonBidsForImp = seatsWithNonBids.entrySet().stream()
                    .filter(entry -> entry.getValue().getImpId().equals(imp.getId()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            seatsWithBidsForImp.forEach((seat, bid) -> {
                final GreenbidsBidder bidder = GreenbidsBidder.builder()
                        .bidder(seat)
                        .isTimeout(false)
                        .hasBid(bid != null)
                        .build();
                bidders.add(bidder);
            });

            seatsWithNonBidsForImp.forEach((seat, nonBid) -> {
                final GreenbidsBidder bidder = GreenbidsBidder.builder()
                        .bidder(seat)
                        .isTimeout(nonBid.getStatusCode().code == BidRejectionReason.TIMED_OUT.code)
                        .hasBid(false)
                        .build();
                bidders.add(bidder);
            });

            final String adUnitCode = getAdUnitCode(imp);

            return AdUnit.builder()
                    .code(adUnitCode)
                    .mediaTypes(mediaTypes)
                    .bidders(bidders)
                    .build();
        }).toList();

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
    ) {
        final List<Imp> imps = auctionContext.getBidRequest().getImp();

        if (imps == null || imps.isEmpty()) {
            throw new IllegalArgumentException("imps is null or empty");
        }

        final Long auctionTimestamp = Optional.of(auctionContext.getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAuctiontimestamp)
                .orElse(null);

        final long auctionElapsed = auctionTimestamp != null ? System.currentTimeMillis() - auctionTimestamp : 0L;

        final Map<String, Bid> seatsWithBids = Optional.ofNullable(bidResponse.getSeatbid())
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

        final List<SeatNonBid> seatNonBids = auctionContext.getBidRejectionTrackers().entrySet().stream()
                .map(entry -> toSeatNonBid(entry.getKey(), entry.getValue()))
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .toList();

        final Map<String, NonBid> seatsWithNonBids = Optional.of(seatNonBids)
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

        final CommonMessage commonMessage = createCommonMessage(auctionContext, auctionElapsed);

        return addBidResponseToMessage(
                commonMessage,
                imps,
                seatsWithBids,
                seatsWithNonBids
        );
    }

    @Override
    public <T> Future<Void> processEvent(T event) {
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

        this.greenbidsId = UUID.randomUUID().toString();
        this.billingId = UUID.randomUUID().toString();
        this.isSampled = isSampled(greenbidsConfig.getGreenbidsSampling(), greenbidsId);

        final CommonMessage commonMessage = createBidMessage(greenbidsAuctionContext, greenbidsBidResponse);

        String commonMessageJson = null;
        try {
            commonMessageJson = JsonUtil.toJson(commonMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }

        HttpUtil.sendJson(commonMessageJson, ANALYTICS_SERVER);

        return Future.succeededFuture();
    }

    public boolean isSampled(
            double samplingRate,
            String greenbidsId
    ) {
        if (samplingRate < 0 || samplingRate > 1) {
            throw new IllegalArgumentException("Warning: Sampling rate must be between 0 and 1");
        }

        final double exploratorySamplingRate = samplingRate * this.exploratorySamplingSplit;
        final double throttledSamplingRate = samplingRate * (1.0 - this.exploratorySamplingSplit);

        long hashInt = Math.abs(greenbidsId.hashCode());
        hashInt = hashInt % 0x10000;

        final boolean isPrimarySampled = hashInt < exploratorySamplingRate * 0xFFFF;
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
