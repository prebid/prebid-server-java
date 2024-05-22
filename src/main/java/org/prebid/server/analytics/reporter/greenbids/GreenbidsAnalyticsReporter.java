package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBids;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;

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

    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;
    private static final String PREBID_SERVER_VERSION = "3.0.0+server";
    public static final Logger logger = LoggerFactory.getLogger(GreenbidsAnalyticsReporter.class);
    private final GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;
    private final JacksonMapper jacksonMapper;
    private final HttpClient httpClient;

    public GreenbidsAnalyticsReporter(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            JacksonMapper jacksonMapper,
            HttpClient httpClient) {
        this.greenbidsAnalyticsProperties = Objects.requireNonNull(greenbidsAnalyticsProperties);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public <T> Future<Void> processEvent(T event) {
        final AuctionContext auctionContext;
        final BidResponse bidResponse;

        if (event instanceof AmpEvent ampEvent) {
            auctionContext = ampEvent.getAuctionContext();
            bidResponse = ampEvent.getBidResponse();
        } else if (event instanceof AuctionEvent auctionEvent) {
            auctionContext = auctionEvent.getAuctionContext();
            bidResponse = auctionEvent.getBidResponse();
        } else {
            auctionContext = null;
            bidResponse = null;
        }

        if (bidResponse == null || auctionContext == null) {
            return Future.failedFuture("Bid response or auction context cannot be null");
        }

        final String greenbidsId = UUID.randomUUID().toString();
        final String billingId = UUID.randomUUID().toString();

        final Boolean isSampled = isSampled(greenbidsAnalyticsProperties.getGreenbidsSampling(), greenbidsId);
        if (isSampled) {
            final CommonMessage commonMessage = createBidMessage(
                    auctionContext,
                    bidResponse,
                    greenbidsId,
                    billingId);

            try {
                final String commonMessageJson = jacksonMapper.encodeToString(commonMessage);

                final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);

                httpClient.post(
                        greenbidsAnalyticsProperties.getAnalyticsServer(),
                        headers,
                        commonMessageJson,
                        greenbidsAnalyticsProperties.getTimeoutMs());

            } catch (EncodeException e) {
                return Future.failedFuture("Failed to encode as JSON: " + e.getMessage());
            }
        }

        return Future.succeededFuture();
    }

    private Boolean isSampled(double samplingRate, String greenbidsId) {
        if (samplingRate < 0 || samplingRate > 1) {
            logger.warn("Warning: Sampling rate must be between 0 and 1");
        }

        final double exploratorySamplingRate = samplingRate
                * greenbidsAnalyticsProperties.getExploratorySamplingSplit();
        final double throttledSamplingRate = samplingRate
                * (1.0 - greenbidsAnalyticsProperties.getExploratorySamplingSplit());

        final long hashInt = Integer.parseInt(
                greenbidsId.substring(greenbidsId.length() - 4), 16);
        final boolean isPrimarySampled = hashInt < exploratorySamplingRate * RANGE_16_BIT_INTEGER_DIVISION_BASIS;

        return isPrimarySampled
                || hashInt >= (1 - throttledSamplingRate) * RANGE_16_BIT_INTEGER_DIVISION_BASIS;
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
            throw new IllegalArgumentException("AdUnits list should not be empty");
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
                        .prebid(PREBID_SERVER_VERSION)
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

    private List<AdUnit> extractAdUnitsWithBidResponses(
            List<Imp> imps,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids) {
        return imps.stream().map(imp -> createAdUnit(
                imp, seatsWithBids, seatsWithNonBids)).toList();
    }

    private AdUnit createAdUnit(
            Imp imp,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids) {
        final Banner banner = imp.getBanner();
        final Video video = imp.getVideo();
        final Native nativeObject = imp.getXNative();

        if (banner == null) {
            Future.failedFuture(new PreBidException("Error: Banner should be non-null"));
        }

        // Error is here
        final List<List<Integer>> bannerWidthHeight = Optional.ofNullable(banner)
                .map(Banner::getFormat)
                .map(formats -> formats.stream().map(format -> List.of(format.getW(), format.getH())))
                .orElse(null).collect(Collectors.toList());

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

        final List<GreenbidsBids> bids = extractBidders(imp, seatsWithBids, seatsWithNonBids);

        final String adUnitCode = getAdUnitCode(imp);

        return AdUnit.builder()
                .code(adUnitCode)
                .mediaTypes(mediaTypes)
                .bids(bids)
                .build();
    }

    private List<GreenbidsBids> extractBidders(
            Imp imp, Map<String, Bid> seatsWithBids, Map<String, NonBid> seatsWithNonBids) {
        final List<GreenbidsBids> bidders = new ArrayList<>();

        final Map<String, Bid> seatsWithBidsForImp = seatsWithBids.entrySet().stream()
                .filter(entry -> entry.getValue().getImpid().equals(imp.getId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final Map<String, NonBid> seatsWithNonBidsForImp = seatsWithNonBids.entrySet().stream()
                .filter(entry -> entry.getValue().getImpId().equals(imp.getId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        seatsWithBidsForImp.forEach((seat, bid) -> {
            final GreenbidsBids bidder = GreenbidsBids.ofBid(seat, bid);
            bidders.add(bidder);
        });

        seatsWithNonBidsForImp.forEach((seat, nonBid) -> {
            final GreenbidsBids bidder = GreenbidsBids.ofNonBid(seat, nonBid);
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
