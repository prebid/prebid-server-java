package org.prebid.server.analytics.reporter.greenbids;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
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
import org.prebid.server.analytics.reporter.greenbids.model.ExplorationResult;
import org.prebid.server.analytics.reporter.greenbids.model.ExtBanner;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAdUnit;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsBid;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsPrebidExt;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsSource;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsUnifiedCode;
import org.prebid.server.analytics.reporter.greenbids.model.MediaTypes;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpExtResult;
import org.prebid.server.analytics.reporter.greenbids.model.Ortb2ImpResult;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceGroup;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStage;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStageOutcome;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GreenbidsAnalyticsReporter implements AnalyticsReporter {

    private static final String BID_REQUEST_ANALYTICS_EXTENSION_NAME = "greenbids";
    private static final int RANGE_16_BIT_INTEGER_DIVISION_BASIS = 0x10000;
    private static final String ANALYTICS_REQUEST_ORIGIN_HEADER = "X-Request-Origin";
    private static final String PREBID_SERVER_HEADER_VALUE = "Prebid Server";
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
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
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
            return Future.failedFuture(new PreBidException("Unprocessable event received"));
        }

        if (bidResponse == null || auctionContext == null) {
            return Future.failedFuture(new PreBidException("Bid response or auction context cannot be null"));
        }

        final GreenbidsPrebidExt greenbidsBidRequestExt = parseBidRequestExt(auctionContext.getBidRequest());

        if (greenbidsBidRequestExt == null) {
            return Future.succeededFuture();
        }

        final String billingId = UUID.randomUUID().toString();

        final Map<String, Ortb2ImpExtResult> analyticsResultFromAnalyticsTag = extractAnalyticsResultFromAnalyticsTag(bidResponse);

        final String greenbidsId = analyticsResultFromAnalyticsTag.values().stream()
                .map(ortb2ImpExtResult ->
                        Optional.ofNullable(ortb2ImpExtResult.getGreenbids())
                                .map(ExplorationResult::getFingerprint)
                                .orElse(UUID.randomUUID().toString()))
                .toString();

        System.out.println(
                "GreenbidsAnalyticsReporter/processEvent" + "\n" +
                        "analyticsResultFromAnalyticsTag: " + analyticsResultFromAnalyticsTag + "\n" +
                        "greenbidsId: " + greenbidsId
        );

        if (!isSampled(greenbidsBidRequestExt.getGreenbidsSampling(), greenbidsId)) {
            return Future.succeededFuture();
        }

        final String commonMessageJson;
        try {
            final CommonMessage commonMessage = createBidMessage(
                    auctionContext,
                    bidResponse,
                    greenbidsId,
                    billingId,
                    greenbidsBidRequestExt,
                    analyticsResultFromAnalyticsTag);
            commonMessageJson = jacksonMapper.encodeToString(commonMessage);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        } catch (EncodeException e) {
            return Future.failedFuture(new PreBidException("Failed to encode as JSON: ", e));
        }

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .add(ANALYTICS_REQUEST_ORIGIN_HEADER, PREBID_SERVER_HEADER_VALUE);

        Optional.ofNullable(auctionContext.getBidRequest())
                .map(BidRequest::getDevice)
                .map(Device::getUa)
                .ifPresent(userAgent -> headers.add(HttpUtil.USER_AGENT_HEADER, userAgent));

        final Future<HttpClientResponse> responseFuture = httpClient.post(
                greenbidsAnalyticsProperties.getAnalyticsServerUrl(),
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


        return responseFuture.compose(this::processAnalyticServerResponse);
    }

    private Map<String, Ortb2ImpExtResult> extractAnalyticsResultFromAnalyticsTag(BidResponse bidResponse) {
        final Optional<ExtBidResponse> extBidResponse = Optional.ofNullable(bidResponse)
                .map(BidResponse::getExt);

        final Optional<ExtBidResponsePrebid> extBidResponsePrebid = extBidResponse
                .map(ExtBidResponse::getPrebid);

        final Optional<ExtModules> extModules = extBidResponsePrebid
                .map(ExtBidResponsePrebid::getModules);

        final List<ExtModulesTraceStageOutcome> stageOutcomes = Optional.ofNullable(bidResponse)
                .map(BidResponse::getExt)
                .map(ExtBidResponse::getPrebid)
                .map(ExtBidResponsePrebid::getModules)
                .map(ExtModules::getTrace)
                .map(ExtModulesTrace::getStages)
                .flatMap(stages -> stages.stream()
                        .filter(stage -> Stage.processed_auction_request.equals(stage.getStage()))
                        .findFirst())
                .map(ExtModulesTraceStage::getOutcomes).orElse(null); // OK

        final Optional<ExtModulesTraceInvocationResult> extModulesTraceInvocationResult = Optional.ofNullable(stageOutcomes)
                .flatMap(outcomes -> outcomes.stream()
                        .filter(outcome -> "auction-request".equals(outcome.getEntity()))
                        .findFirst())
                .map(ExtModulesTraceStageOutcome::getGroups)
                .flatMap(groups -> groups.stream().findFirst())
                .map(ExtModulesTraceGroup::getInvocationResults)
                .flatMap(invocationResults -> invocationResults.stream()
                        .filter(invocationResult -> "greenbids-real-time-data"
                                .equals(invocationResult.getHookId().getModuleCode()))
                        .findFirst()); // OK

        final Optional<ObjectNode> analyticsResultValue = extModulesTraceInvocationResult
                .map(ExtModulesTraceInvocationResult::getAnalyticsTags)
                .map(ExtModulesTraceAnalyticsTags::getActivities)
                .flatMap(activities -> activities.stream()
                        .flatMap(activity -> activity.getResults().stream())
                        .findFirst())
                .map(ExtModulesTraceAnalyticsResult::getValues);

        final Map<String, Ortb2ImpExtResult> parsedAnalyticsResultsMap =  analyticsResultValue
                .map(analyticsResult -> {
                    try {
                        Map<String, Ortb2ImpExtResult> parsedAnalyticsResult = new HashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = analyticsResult.fields();
                        // iterate over elements of objectNode by imp
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String impId = field.getKey();
                            JsonNode explorationResultNode = field.getValue();
                            Ortb2ImpExtResult ortb2ImpExtResult = jacksonMapper.mapper().treeToValue(explorationResultNode, Ortb2ImpExtResult.class);
                            parsedAnalyticsResult.put(impId, ortb2ImpExtResult);
                        }

                        System.out.println(
                                "GreenbidsAnalyticsReporter/ extractAnalyticsResultFromAnalyticsTag/ analyticsResultValue" + "\n" +
                                        "parsedAnalyticsResult: " + parsedAnalyticsResult + "\n" +
                                        "fields: " + fields
                        );

                        return parsedAnalyticsResult;
                    } catch (JsonProcessingException e) {
                        throw new PreBidException("analytics result parsing error", e);
                    }
                }).orElse(null);

        System.out.println(
                "GreenbidsAnalyticsReporter/ extractAnalyticsResultFromAnalyticsTag" + "\n" +
                        "extBidResponsePrebid: " + extBidResponsePrebid + "\n" +
                        "extModules: " + extModules + "\n" +
                        "outcomes: " + stageOutcomes + "\n" +
                        "extModulesTraceInvocationResult: " + extModulesTraceInvocationResult + "\n" +
                        "analyticsResultValue: " + analyticsResultValue + "\n" +
                        "parsedAnalyticsResultsMap: " + parsedAnalyticsResultsMap
        );

        return parsedAnalyticsResultsMap;

        /*
        return Optional.ofNullable(bidResponse)
                .map(BidResponse::getExt)
                .map(ExtBidResponse::getPrebid)
                .map(ExtBidResponsePrebid::getModules)
                .map(ExtModules::getTrace)
                .map(ExtModulesTrace::getStages)
                .flatMap(stages -> stages.stream()
                        .filter(stage -> Stage.processed_auction_request.equals(stage.getStage()))
                        .findFirst())
                .map(ExtModulesTraceStage::getOutcomes) // OK
                .flatMap(outcomes -> outcomes.stream()
                        .filter(outcome -> "auction-request".equals(outcome.getEntity()))
                        .findFirst())
                .map(ExtModulesTraceStageOutcome::getGroups)
                .flatMap(groups -> groups.stream().findFirst())
                .map(ExtModulesTraceGroup::getInvocationResults)
                .flatMap(invocationResults -> invocationResults.stream()
                        .filter(invocationResult -> "greenbids-real-time-data"
                                .equals(invocationResult.getHookId().getModuleCode()))
                        .findFirst()) // OK
                .map(ExtModulesTraceInvocationResult::getAnalyticsTags)
                .map(ExtModulesTraceAnalyticsTags::getActivities)
                .flatMap(activities -> activities.stream()
                        .flatMap(activity -> activity.getResults().stream())
                        .findFirst())
                .map(ExtModulesTraceAnalyticsResult::getValues)
                .map(values -> values.get("greenbidsId"))
                .map(JsonNode::asText).orElse(null);
         */
    }

    private GreenbidsPrebidExt parseBidRequestExt(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAnalytics)
                .filter(this::isNotEmptyObjectNode)
                .map(analytics -> (ObjectNode) analytics.get(BID_REQUEST_ANALYTICS_EXTENSION_NAME))
                .map(this::toGreenbidsPrebidExt)
                .orElse(null);
    }

    private GreenbidsPrebidExt toGreenbidsPrebidExt(ObjectNode adapterNode) {
        try {
            return jacksonMapper.mapper().treeToValue(adapterNode, GreenbidsPrebidExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding bid request analytics extension: " + e.getMessage(), e);
        }
    }

    private boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
    }

    private Future<Void> processAnalyticServerResponse(HttpClientResponse response) {
        final int responseStatusCode = response.getStatusCode();
        if (responseStatusCode >= 200 && responseStatusCode < 300) {
            return Future.succeededFuture();
        }
        return Future.failedFuture(new PreBidException("Unexpected response status: " + response.getStatusCode()));
    }

    private boolean isSampled(Double samplingRate, String greenbidsId) {
        if (samplingRate == null) {
            logger.warn("Warning: Sampling rate is not defined in request. Set sampling at "
                    + greenbidsAnalyticsProperties.getDefaultSamplingRate());
            return true;
        }

        if (samplingRate < 0 || samplingRate > 1) {
            logger.warn("Warning: Sampling rate must be between 0 and 1");
            return true;
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
            GreenbidsPrebidExt greenbidsImpExt,
            Map<String, Ortb2ImpExtResult> analyticsResultFromAnalyticsTag) {
        final Optional<BidRequest> bidRequest = Optional.ofNullable(auctionContext.getBidRequest());

        final List<Imp> imps = bidRequest
                .map(BidRequest::getImp)
                .filter(CollectionUtils::isNotEmpty)
                .orElseThrow(() -> new PreBidException("Impressions list should not be empty"));

        final long auctionElapsed = bidRequest
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getAuctiontimestamp)
                .map(timestamp -> clock.millis() - timestamp).orElse(0L);

        final Map<String, Bid> seatsWithBids = getSeatsWithBids(bidResponse);

        final Map<String, NonBid> seatsWithNonBids = getSeatsWithNonBids(auctionContext);

        final List<GreenbidsAdUnit> adUnitsWithBidResponses = imps.stream().map(imp -> createAdUnit(
                imp, seatsWithBids, seatsWithNonBids, bidResponse.getCur(), analyticsResultFromAnalyticsTag.get(imp.getId()))).toList();

        final String auctionId = bidRequest
                .map(BidRequest::getId)
                .orElse(null);

        final String referrer = bidRequest
                .map(BidRequest::getSite)
                .map(Site::getPage)
                .orElse(null);

        final Double greenbidsSamplingRate = Optional.ofNullable(greenbidsImpExt.getGreenbidsSampling())
                .orElse(greenbidsAnalyticsProperties.getDefaultSamplingRate());

        return CommonMessage.builder()
                        .version(greenbidsAnalyticsProperties.getAnalyticsServerVersion())
                        .auctionId(auctionId)
                        .referrer(referrer)
                        .sampling(greenbidsSamplingRate)
                        .prebidServer(prebidVersionProvider.getNameVersionRecord())
                        .greenbidsId(greenbidsId)
                        .pbuid(greenbidsImpExt.getPbuid())
                        .billingId(billingId)
                        .adUnits(adUnitsWithBidResponses)
                        .auctionElapsed(auctionElapsed)
                        .build();
    }

    private static Map<String, Bid> getSeatsWithBids(BidResponse bidResponse) {
        return Stream.ofNullable(bidResponse.getSeatbid())
                .flatMap(Collection::stream)
                .filter(seatBid -> !seatBid.getBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatBid::getSeat,
                                seatBid -> seatBid.getBid().getFirst(),
                                (existing, replacement) -> existing));
    }

    private static Map<String, NonBid> getSeatsWithNonBids(AuctionContext auctionContext) {
        return auctionContext.getBidRejectionTrackers().entrySet().stream()
                .map(entry -> toSeatNonBid(entry.getKey(), entry.getValue()))
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .collect(
                        Collectors.toMap(
                                SeatNonBid::getSeat,
                                seatNonBid -> seatNonBid.getNonBid().getFirst(),
                                (existing, replacement) -> existing));
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
            Map<String, NonBid> seatsWithNonBids,
            String currency,
            Ortb2ImpExtResult analyticsResultFromAnalyticsTag) {
        final ExtBanner extBanner = getExtBanner(imp.getBanner());
        final Video video = imp.getVideo();
        final Native nativeObject = imp.getXNative();

        final MediaTypes mediaTypes = MediaTypes.of(extBanner, video, nativeObject);

        final ObjectNode impExt = imp.getExt();
        final String adUnitCode = imp.getId();

        final ExtImpPrebid impExtPrebid = Optional.ofNullable(impExt)
                .map(ext -> ext.get("prebid"))
                .map(this::extImpPrebid)
                .orElseThrow(() -> new PreBidException("imp.ext.prebid should not be empty"));

        final GreenbidsUnifiedCode greenbidsUnifiedCode = getGpid(impExt)
                .or(() -> getStoredRequestId(impExtPrebid))
                .orElseGet(() -> GreenbidsUnifiedCode.of(
                        adUnitCode, GreenbidsSource.AD_UNIT_CODE_SOURCE.getValue()));

        final List<GreenbidsBid> bids = extractBidders(
                imp.getId(), seatsWithBids, seatsWithNonBids, impExtPrebid, currency);

        return GreenbidsAdUnit.builder()
                .code(adUnitCode)
                .unifiedCode(greenbidsUnifiedCode)
                .mediaTypes(mediaTypes)
                .bids(bids)
                .ortb2ImpResult(Ortb2ImpResult.of(analyticsResultFromAnalyticsTag))
                .build();
    }

    private static ExtBanner getExtBanner(Banner banner) {
        if (banner == null) {
            return null;
        }

        final List<List<Integer>> sizes = Optional.ofNullable(banner.getFormat())
                .filter(formats -> !formats.isEmpty())
                .map(formats -> formats.stream()
                        .map(format -> List.of(format.getW(), format.getH()))
                        .collect(Collectors.toList()))
                .orElse(banner.getW() != null && banner.getH() != null
                        ? List.of(List.of(banner.getW(), banner.getH()))
                        : Collections.emptyList());

        return ExtBanner.builder()
                .sizes(sizes)
                .pos(banner.getPos())
                .name(banner.getId())
                .build();
    }

    private List<GreenbidsBid> extractBidders(
            String impId,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids,
            ExtImpPrebid impExtPrebid,
            String currency) {
        final ObjectNode bidders = impExtPrebid.getBidder();

        return Stream.concat(
                seatsWithBids.entrySet().stream()
                        .filter(entry -> entry.getValue().getImpid().equals(impId))
                        .map(entry -> GreenbidsBid.ofBid(
                                entry.getKey(), entry.getValue(), bidders.get(entry.getKey()), currency)),
                seatsWithNonBids.entrySet().stream()
                        .filter(entry -> entry.getValue().getImpId().equals(impId))
                        .map(entry -> GreenbidsBid.ofNonBid(
                                entry.getKey(), entry.getValue(), bidders.get(entry.getKey()), currency)))
                .toList();
    }

    private static Optional<GreenbidsUnifiedCode> getGpid(ObjectNode impExt) {
        return Optional.ofNullable(impExt)
                .map(ext -> ext.get("gpid"))
                .map(JsonNode::asText)
                .map(gpid ->
                        GreenbidsUnifiedCode.of(gpid, GreenbidsSource.GPID_SOURCE.getValue()));
    }

    private Optional<GreenbidsUnifiedCode> getStoredRequestId(ExtImpPrebid extImpPrebid) {
        return Optional.ofNullable(extImpPrebid.getStoredrequest())
                .map(ExtStoredRequest::getId)
                .map(storedRequestId ->
                        GreenbidsUnifiedCode.of(
                                storedRequestId, GreenbidsSource.STORED_REQUEST_ID_SOURCE.getValue()));
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
