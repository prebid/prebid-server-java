package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBidder;
import org.prebid.server.analytics.reporter.greenbids.model.HttpUtil;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GreenbidsAnalyticsReporter implements AnalyticsReporter {

    private final GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;
    private final JacksonMapper jacksonMapper;

    public GreenbidsAnalyticsReporter(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            JacksonMapper jacksonMapper) {
        this.greenbidsAnalyticsProperties = Objects.requireNonNull(greenbidsAnalyticsProperties);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
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

        final String greenbidsId = UUID.randomUUID().toString();
        final String billingId = UUID.randomUUID().toString();
        final boolean isSampled = isSampled(greenbidsAnalyticsProperties.getGreenbidsSampling(), greenbidsId);

        if (isSampled) {
            final CommonMessage commonMessage = createBidMessage(
                    greenbidsAuctionContext, greenbidsBidResponse, greenbidsId, billingId);

            try {
                String commonMessageJson = jacksonMapper.encodeToString(commonMessage);
                HttpUtil.sendJson(commonMessageJson, greenbidsAnalyticsProperties.getAnalyticsServer());
            } catch (EncodeException e) {
                throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
            }
        }

        return Future.succeededFuture();
    }

    private boolean isSampled(
            double samplingRate,
            String greenbidsId) {
        if (samplingRate < 0 || samplingRate > 1) {
            throw new IllegalArgumentException("Warning: Sampling rate must be between 0 and 1");
        }

        final double exploratorySamplingRate = samplingRate
                * greenbidsAnalyticsProperties.getExploratorySamplingSplit();
        final double throttledSamplingRate = samplingRate
                * (1.0 - greenbidsAnalyticsProperties.getExploratorySamplingSplit());

        final long hashInt = Math.abs(greenbidsId.hashCode()) % 0x10000;

        final boolean isPrimarySampled = hashInt < exploratorySamplingRate * 0xFFFF;
        if (isPrimarySampled) {
            return true;
        }
        return hashInt >= (1 - throttledSamplingRate) * 0xFFFF;
    }

    public CommonMessage createBidMessage(
            AuctionContext auctionContext,
            BidResponse bidResponse,
            String greenbidsId,
            String billingId) {
        final List<Imp> imps = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getBidRequest)
                .map(BidRequest::getImp)
                .orElse(Collections.emptyList());

        if (CollectionUtils.isEmpty(imps)) {
            throw new IllegalArgumentException("Imps is null or empty");
        }

        final long auctionElapsed = Optional.ofNullable(auctionContext.getBidRequest())
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAuctiontimestamp)
                .map(timestamp -> System.currentTimeMillis() - timestamp).orElse(0L);

        final Map<String, Bid> seatsWithBids = Stream.ofNullable(bidResponse.getSeatbid())
                .flatMap(Collection::stream)
                .filter(seatBid -> !seatBid.getBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatBid::getSeat,
                                seatBid -> seatBid.getBid().getFirst(),
                                (existing, replacement) -> existing));

        final List<SeatNonBid> seatNonBids = auctionContext.getBidRejectionTrackers().entrySet().stream()
                .map(entry -> toSeatNonBid(entry.getKey(), entry.getValue()))
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .toList();

        final Map<String, NonBid> seatsWithNonBids = Stream.ofNullable(seatNonBids)
                .flatMap(Collection::stream)
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatNonBid::getSeat,
                                seatNonBid -> seatNonBid.getNonBid().getFirst(),
                                (existing, replacement) -> existing));

        final List<AdUnit> adUnitsWithBidResponses = extractAdUnitsWithBidResponses(
                imps, seatsWithBids, seatsWithNonBids);

        final String auctionId = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getBidRequest)
                .map(BidRequest::getId)
                .orElse(null);

        final String referrer = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getBidRequest)
                .map(BidRequest::getSite)
                .map(Site::getPage)
                .orElse(null);

        return CommonMessage.builder()
                .version(greenbidsAnalyticsProperties.getAnalyticsServerVersion())
                .auctionId(auctionId)
                .referrer(referrer)
                .sampling(greenbidsAnalyticsProperties.getGreenbidsSampling())
                .prebid("prebid.version")
                .greenbidsId(greenbidsId)
                .pbuid(greenbidsAnalyticsProperties.getPbuid())
                .billingId(billingId)
                .adUnits(adUnitsWithBidResponses)
                .auctionElapsed(auctionElapsed)
                .build();
    }

    private static SeatNonBid toSeatNonBid(String bidder, BidRejectionTracker bidRejectionTracker) {
        final List<NonBid> nonBids = bidRejectionTracker.getRejectionReasons().entrySet().stream()
                .map(entry -> NonBid.of(entry.getKey(), entry.getValue()))
                .toList();

        return SeatNonBid.of(bidder, nonBids);
    }

    public List<AdUnit> extractAdUnitsWithBidResponses(
            List<Imp> imps,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids) {
        return imps.stream().map(imp -> createAdUnit(imp, seatsWithBids, seatsWithNonBids)).toList();
    }

    private AdUnit createAdUnit(Imp imp, Map<String, Bid> seatsWithBids, Map<String, NonBid> seatsWithNonBids) {
        final Banner banner = imp.getBanner();
        final Video video = imp.getVideo();
        final Native nativeObject = imp.getXNative();

        final Optional<List<Format>> format = Optional.ofNullable(banner)
                .map(Banner::getFormat)
                .filter(f -> !CollectionUtils.isEmpty(f));

        final Integer width = format.map(f -> f.getFirst().getW()).orElse(null);
        final Integer height = format.map(f -> f.getFirst().getH()).orElse(null);

        final List<List<Integer>> bannerWidthHeight = width != null && height != null ? List.of(
                List.of(width, height),
                List.of(width, height)) : null;

        final ExtBanner extBanner = ExtBanner.builder()
                .sizes(bannerWidthHeight)
                .pos(Optional.ofNullable(banner).map(Banner::getPos).orElse(null))
                .name(Optional.ofNullable(banner).map(Banner::getId).orElse(null))
                .build();

        final MediaTypes mediaTypes = MediaTypes.builder()
                .banner(extBanner)
                .video(video)
                .nativeObject(nativeObject)
                .build();

        final List<GreenbidsBidder> bidders = extractBidders(imp, seatsWithBids, seatsWithNonBids);

        final String adUnitCode = getAdUnitCode(imp);

        return AdUnit.builder()
                .code(adUnitCode)
                .mediaTypes(mediaTypes)
                .bidders(bidders)
                .build();
    }

    private List<GreenbidsBidder> extractBidders(
            Imp imp, Map<String, Bid> seatsWithBids, Map<String, NonBid> seatsWithNonBids) {
        final List<GreenbidsBidder> bidders = new ArrayList<>();

        final Map<String, Bid> seatsWithBidsForImp = seatsWithBids.entrySet().stream()
                .filter(entry -> entry.getValue().getImpid().equals(imp.getId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final Map<String, NonBid> seatsWithNonBidsForImp = seatsWithNonBids.entrySet().stream()
                .filter(entry -> entry.getValue().getImpId().equals(imp.getId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        seatsWithBidsForImp.forEach((seat, bid) -> {
            GreenbidsBidder bidder = GreenbidsBidder.ofBid(seat, bid);
            bidders.add(bidder);
        });

        seatsWithNonBidsForImp.forEach((seat, nonBid) -> {
            GreenbidsBidder bidder = GreenbidsBidder.ofNonBid(seat, nonBid);
            bidders.add(bidder);
        });

        return bidders;
    }

    private String getAdUnitCode(Imp imp) {
        final ObjectNode impExt = imp.getExt();

        return Optional.ofNullable(getGpid(impExt))
                .or(() -> Optional.ofNullable(storedRequestId(impExt)))
                .orElse(imp.getId());
    }

    private static String getGpid(ObjectNode impExt) {
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("gpid"))
                .map(JsonNode::asText)
                .orElse(null);
    }

    private String storedRequestId(ObjectNode impExt) {
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("prebid"))
                .map(this::extImpPrebid)
                .map(ExtImpPrebid::getStoredrequest)
                .map(ExtStoredRequest::getId)
                .orElse(null);
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return jacksonMapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
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
