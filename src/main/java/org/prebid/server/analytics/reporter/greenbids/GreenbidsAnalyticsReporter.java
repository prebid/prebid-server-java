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
import org.prebid.server.analytics.reporter.greenbids.model.CommonMessage;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBids;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsPrebidExt;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsUnifiedCode;
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
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
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

    private static final String PUBLISHER_ID_DYNAMIC_PARAM = "pbuid";
    private static final String GREENBIDS_SAMPLING_DYNAMIC_PARAM = "greenbidsSampling";
    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids";
    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;
    private static final Logger logger = LoggerFactory.getLogger(GreenbidsAnalyticsReporter.class);
    private final GreenbidsAnalyticsProperties greenbidsAnalyticsProperties;
    private final JacksonMapper jacksonMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final PrebidVersionProvider prebidVersionProvider;

    public GreenbidsAnalyticsReporter(
            GreenbidsAnalyticsProperties greenbidsAnalyticsProperties,
            JacksonMapper jacksonMapper,
            HttpClient httpClient,
            Clock clock,
            PrebidVersionProvider prebidVersionProvider) {
        this.greenbidsAnalyticsProperties = Objects.requireNonNull(greenbidsAnalyticsProperties);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
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
            return Future.failedFuture(new PreBidException("Bid response or auction context cannot be null"));
        }

        final GreenbidsPrebidExt greenbidsImpExt = parseBidRequestExt(auctionContext.getBidRequest());

        final String greenbidsId = UUID.randomUUID().toString();
        final String billingId = UUID.randomUUID().toString();

        if (!isSampled(greenbidsImpExt.getGreenbidsSampling(), greenbidsId)) {
            return Future.succeededFuture();
        }

        final String commonMessageJson;
        try {
            final CommonMessage commonMessage = createBidMessage(
                    auctionContext,
                    bidResponse,
                    greenbidsId,
                    billingId,
                    greenbidsImpExt);
            commonMessageJson = jacksonMapper.encodeToString(commonMessage);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        } catch (EncodeException e) {
            return Future.failedFuture(new PreBidException("Failed to encode as JSON: ", e));
        }

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);

        final Future<HttpClientResponse> responseFuture = httpClient.post(
                greenbidsAnalyticsProperties.getAnalyticsServer(),
                headers,
                commonMessageJson,
                greenbidsAnalyticsProperties.getTimeoutMs());

        responseFuture
                .onSuccess(response ->
                        System.out.println(
                                "Analytics Server response body: " +
                                        response.getStatusCode() + "\n" +
                                        response.getHeaders() + "\n" +
                                        response.getBody() + "\n" +
                                        commonMessageJson
                        ))
                .onFailure(error -> System.out.println("Can't send payload to Analytics Server: " + error));

        return processAnalyticServerResponse(responseFuture);
    }

    private GreenbidsPrebidExt parseBidRequestExt(BidRequest bidRequest) {
        final Optional<ObjectNode> adapterNode = Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAnalytics)
                .filter(this::isNotEmptyObjectNode)
                .map(analytics -> (ObjectNode) analytics.get(BID_REQUEST_ANALYTICS_EXTENSION_NAME));

        final String pbuid = adapterNode.map(node -> node.get(PUBLISHER_ID_DYNAMIC_PARAM))
                .map(JsonNode::asText)
                .orElse(null);

        final Double greenbidsSampling = adapterNode.map(node -> node.get(GREENBIDS_SAMPLING_DYNAMIC_PARAM))
                .map(JsonNode::asDouble)
                .orElse(null);

        return GreenbidsPrebidExt.builder().pbuid(pbuid).greenbidsSampling(greenbidsSampling).build();
    }

    private boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
    }

    private Future<Void> processAnalyticServerResponse(Future<HttpClientResponse> responseFuture) {
        return responseFuture.compose(response -> {
            final int responseStatusCode = response.getStatusCode();
            if (responseStatusCode == 202 || responseStatusCode == 200) {
                return Future.succeededFuture();
            } else {
                return Future.failedFuture(
                        new PreBidException("Unexpected response status: " + response.getStatusCode()));
            }
        });
    }

    private boolean isSampled(double samplingRate, String greenbidsId) {
        if (samplingRate < 0 || samplingRate > 1) {
            logger.warn("Warning: Sampling rate must be between 0 and 1");
            return false;
        }

        final double exploratorySamplingRate = samplingRate
                * greenbidsAnalyticsProperties.getExploratorySamplingSplit();
        final double throttledSamplingRate = samplingRate
                * (1.0 - greenbidsAnalyticsProperties.getExploratorySamplingSplit());

        final int hashInt = Integer.parseInt(
                greenbidsId.substring(greenbidsId.length() - 4), 16);
        final boolean isPrimarySampled = hashInt < exploratorySamplingRate * RANGE_16_BIT_INTEGER_DIVISION_BASIS;
        final boolean isExtraSampledOutOfExploration = hashInt >= (1 - throttledSamplingRate)
                * RANGE_16_BIT_INTEGER_DIVISION_BASIS;

        return isPrimarySampled || isExtraSampledOutOfExploration;
    }

    private CommonMessage createBidMessage(
            AuctionContext auctionContext,
            BidResponse bidResponse,
            String greenbidsId,
            String billingId,
            GreenbidsPrebidExt greenbidsImpExt) {
        final Optional<BidRequest> bidRequest = Optional.ofNullable(auctionContext.getBidRequest());

        final List<Imp> imps = bidRequest
                .map(BidRequest::getImp)
                .filter(CollectionUtils::isNotEmpty)
                .orElseThrow(() -> new PreBidException("AdUnits list should not be empty"));

        final long auctionElapsed = bidRequest
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAuctiontimestamp)
                .map(timestamp -> clock.millis() - timestamp).orElse(0L);

        final Map<String, Bid> seatsWithBids = Stream.ofNullable(bidResponse.getSeatbid())
                .flatMap(Collection::stream)
                .filter(seatBid -> !seatBid.getBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatBid::getSeat,
                                seatBid -> seatBid.getBid().getFirst(),
                                (existing, replacement) -> existing));

        final Map<String, NonBid> seatsWithNonBids = auctionContext.getBidRejectionTrackers().entrySet().stream()
                .map(entry -> toSeatNonBid(entry.getKey(), entry.getValue()))
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatNonBid::getSeat,
                                seatNonBid -> seatNonBid.getNonBid().getFirst(),
                                (existing, replacement) -> existing));

        final List<GreenbidsAdUnit> adUnitsWithBidResponses = imps.stream().map(imp -> createAdUnit(
                imp, seatsWithBids, seatsWithNonBids)).toList();

        final String auctionId = bidRequest
                .map(BidRequest::getId)
                .orElse(null);

        final String referrer = bidRequest
                .map(BidRequest::getSite)
                .map(Site::getPage)
                .orElse(null);

        return CommonMessage.builder()
                        .version(greenbidsAnalyticsProperties.getAnalyticsServerVersion())
                        .auctionId(auctionId)
                        .referrer(referrer)
                        .sampling(greenbidsImpExt.getGreenbidsSampling())
                        .prebidServer(prebidVersionProvider.getNameVersionRecord())
                        .greenbidsId(greenbidsId)
                        .pbuid(greenbidsImpExt.getPbuid())
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

    private GreenbidsAdUnit createAdUnit(
            Imp imp,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids) {
        final ExtBanner extBanner = getExtBanner(imp.getBanner());
        final Video video = imp.getVideo();
        final Native nativeObject = imp.getXNative();

        final MediaTypes mediaTypes = MediaTypes.builder()
                .banner(extBanner)
                .video(video)
                .nativeObject(nativeObject)
                .build();

        final List<GreenbidsBids> bids = extractBidders(imp, seatsWithBids, seatsWithNonBids);

        final ObjectNode impExt = imp.getExt();
        final Optional<String> gpidOption = getGpid(impExt);
        final Optional<String> storedRequestIdOption = getStoredRequestId(impExt);
        final String adUnitCode = imp.getId();

        final String gpidWithFallback = gpidOption.or(() -> storedRequestIdOption)
                .orElse(adUnitCode);

        final GreenbidsUnifiedCode.Source codeTypeSource = gpidOption
                .map(gpid -> GreenbidsUnifiedCode.Source.gpidSource)
                .or(() -> storedRequestIdOption
                        .map(storedRequestId -> GreenbidsUnifiedCode.Source.storedRequestIdSource))
                .orElse(GreenbidsUnifiedCode.Source.adUnitCodeSource);

        final GreenbidsUnifiedCode greenbidsUnifiedCode = GreenbidsUnifiedCode.builder()
                .value(gpidWithFallback)
                .source(codeTypeSource)
                .build();

        return GreenbidsAdUnit.builder()
                .code(adUnitCode)
                .unifiedCode(greenbidsUnifiedCode)
                .mediaTypes(mediaTypes)
                .bids(bids)
                .build();
    }

    public static ExtBanner getExtBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        final List<List<Integer>> sizes = Optional.ofNullable(banner.getFormat())
                .filter(formats -> !formats.isEmpty())
                .map(formats -> formats.stream()
                        .map(format -> List.of(format.getW(), format.getH()))
                        .collect(Collectors.toList()))
                .orElseGet(() -> {
                    if (banner.getW() != null && banner.getH() != null) {
                        return List.of(List.of(banner.getW(), banner.getH()));
                    }
                    return Collections.emptyList();
                });

        return ExtBanner.builder()
                .sizes(sizes)
                .pos(banner.getPos())
                .name(banner.getId())
                .build();
    }

    private List<GreenbidsBids> extractBidders(
            Imp imp, Map<String, Bid> seatsWithBids, Map<String, NonBid> seatsWithNonBids) {
        return Stream.concat(
                seatsWithBids.entrySet().stream()
                        .filter(entry -> entry.getValue().getImpid().equals(imp.getId()))
                        .map(entry -> GreenbidsBids.ofBid(entry.getKey(), entry.getValue())),
                seatsWithNonBids.entrySet().stream()
                        .filter(entry -> entry.getValue().getImpId().equals(imp.getId()))
                        .map(entry -> GreenbidsBids.ofNonBid(entry.getKey(), entry.getValue())))
                .collect(Collectors.toList());
    }

    private String getGpidWithFallback(Imp imp) {
        final ObjectNode impExt = imp.getExt();

        return getGpid(impExt)
                .or(() -> getStoredRequestId(impExt))
                .orElse(imp.getId());
    }

    private static Optional<String> getGpid(ObjectNode impExt) {
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("gpid"))
                .map(JsonNode::asText);
    }

    private Optional<String> getStoredRequestId(ObjectNode impExt) {
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("prebid"))
                .map(this::extImpPrebid)
                .map(ExtImpPrebid::getStoredrequest)
                .map(ExtStoredRequest::getId);
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
