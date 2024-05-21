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
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.greenbids.model.AdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.Greenbids;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBids;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2Imp;
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

    private static final int MAX_16_BIT_INTEGER = 0xFFFF;
    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;
    private static final String PREBID_SERVER_VERSION = "3.0.0+server";
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
        final AuctionContext greenbidsAuctionContext;
        final BidResponse greenbidsBidResponse;

        if (event instanceof AmpEvent ampEvent) {
            greenbidsAuctionContext = ampEvent.getAuctionContext();
            greenbidsBidResponse = ampEvent.getBidResponse();
        } else if (event instanceof AuctionEvent auctionEvent) {
            greenbidsAuctionContext = auctionEvent.getAuctionContext();
            greenbidsBidResponse = auctionEvent.getBidResponse();
        } else {
            greenbidsAuctionContext = null;
            greenbidsBidResponse = null;
        }

        if (greenbidsBidResponse == null || greenbidsAuctionContext == null) {
            return Future.failedFuture("Bid response or auction context cannot be null");
        }

        final String greenbidsId = UUID.randomUUID().toString();
        final String billingId = UUID.randomUUID().toString();
        final String fingerprint = UUID.randomUUID().toString();
        final String tid = UUID.randomUUID().toString();

        isSampled(greenbidsAnalyticsProperties.getGreenbidsSampling(), greenbidsId)
                .compose(isSampled -> {
                    if (isSampled) {
                        final Future<CommonMessage> bidMessage = createBidMessage(
                                greenbidsAuctionContext,
                                greenbidsBidResponse,
                                greenbidsId,
                                billingId,
                                fingerprint,
                                tid);

                        return bidMessage.onSuccess(commonMessage -> {
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
                                Future.failedFuture("Failed to encode as JSON: " + e.getMessage());
                            }
                        });
                    }
                    return null;
                });

        return Future.succeededFuture();
    }

    private Future<Boolean> isSampled(
            double samplingRate,
            String greenbidsId) {
        final Promise<Boolean> promise = Promise.promise();
        try {
            if (samplingRate < 0 || samplingRate > 1) {
                Future.failedFuture("Warning: Sampling rate must be between 0 and 1");
            }

            final double exploratorySamplingRate = samplingRate
                    * greenbidsAnalyticsProperties.getExploratorySamplingSplit();
            final double throttledSamplingRate = samplingRate
                    * (1.0 - greenbidsAnalyticsProperties.getExploratorySamplingSplit());

            final long hashInt = Integer.parseInt(
                    greenbidsId.substring(greenbidsId.length() - 4), 16
            ) % RANGE_16_BIT_INTEGER_DIVISION_BASIS;
            final boolean isPrimarySampled = hashInt < exploratorySamplingRate * MAX_16_BIT_INTEGER;

            promise.complete(isPrimarySampled || hashInt >= (1 - throttledSamplingRate) * MAX_16_BIT_INTEGER);

        } catch (IllegalArgumentException e) {
            promise.fail(e);
        }

        return promise.future();
    }

    public Future<CommonMessage> createBidMessage(
            AuctionContext auctionContext,
            BidResponse bidResponse,
            String greenbidsId,
            String billingId,
            String fingerprint,
            String tid) {
        final Promise<CommonMessage> promise = Promise.promise();

        try {
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
                    imps, seatsWithBids, seatsWithNonBids, fingerprint, tid);

            final String auctionId = Optional.ofNullable(auctionContext)
                    .map(AuctionContext::getBidRequest)
                    .map(BidRequest::getId)
                    .orElse(null);

            final String referrer = Optional.ofNullable(auctionContext)
                    .map(AuctionContext::getBidRequest)
                    .map(BidRequest::getSite)
                    .map(Site::getPage)
                    .orElse(null);

            promise.complete(
                    CommonMessage.builder()
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
                            .build());

        } catch (IllegalArgumentException e) {
            promise.fail(e);
        }

        return promise.future();
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
            Map<String, NonBid> seatsWithNonBids,
            String fingerprint,
            String tid) {
        return imps.stream().map(imp -> createAdUnit(
                imp, seatsWithBids, seatsWithNonBids, fingerprint, tid)).toList();
    }

    private AdUnit createAdUnit(
            Imp imp,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids,
            String fingerprint,
            String tid) {
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

        final List<GreenbidsBids> bids = extractBidders(imp, seatsWithBids, seatsWithNonBids);

        final String adUnitCode = getAdUnitCode(imp);

        final Map<String, Boolean> keptInAuction = bids.stream()
                .collect(Collectors.toMap(
                        GreenbidsBids::getBidder,
                        bidder -> true,
                        (existing, replacement) -> existing
                ));

        final Greenbids greenbids = Greenbids.builder()
                .fingerprint(fingerprint)
                .keptInAuction(keptInAuction)
                .isExploration(true)
                .build();

        final Ortb2Imp.Ext ext = Ortb2Imp.Ext.builder()
                .tid(tid)
                .greenbids(greenbids)
                .build();

        final Ortb2Imp ortb2Imp = Ortb2Imp.builder()
                .ext(ext)
                .build();

        return AdUnit.builder()
                .code(adUnitCode)
                .mediaTypes(mediaTypes)
                .bids(bids)
                .ortb2Imp(ortb2Imp)
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
