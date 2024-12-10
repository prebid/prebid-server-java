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
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
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
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
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

        final GreenbidsPrebidExt greenbidsBidRequestExt = Optional.ofNullable(
                parseBidRequestExt(auctionContext.getBidRequest()))
                .orElse(parseAccountConfig(auctionContext));

        if (greenbidsBidRequestExt == null) {
            return Future.succeededFuture();
        }

        final String billingId = UUID.randomUUID().toString();

        final Map<String, Ortb2ImpExtResult> analyticsResultFromAnalyticsTag = extractAnalyticsResultFromAnalyticsTag(
                auctionContext);

        final String greenbidsId = greenbidsId(analyticsResultFromAnalyticsTag);

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

        return responseFuture.compose(this::processAnalyticServerResponse);
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

    private GreenbidsPrebidExt parseAccountConfig(AuctionContext auctionContext) {
        final Map<String, ObjectNode> modules = Optional.ofNullable(auctionContext)
                .map(AuctionContext::getAccount)
                .map(Account::getAnalytics)
                .map(AccountAnalyticsConfig::getModules)
                .orElse(null);

        GreenbidsPrebidExt greenbidsPrebidExt = null;
        if (modules != null && modules.containsKey("greenbids")) {
            final ObjectNode moduleConfig = modules.get("greenbids");
            greenbidsPrebidExt = toGreenbidsPrebidExt(moduleConfig);
        }

        return greenbidsPrebidExt;
    }

    private boolean isNotEmptyObjectNode(JsonNode analytics) {
        return analytics != null && analytics.isObject() && !analytics.isEmpty();
    }

    private GreenbidsPrebidExt toGreenbidsPrebidExt(ObjectNode adapterNode) {
        try {
            return jacksonMapper.mapper().treeToValue(adapterNode, GreenbidsPrebidExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding bid request analytics extension: " + e.getMessage(), e);
        }
    }

    private Map<String, Ortb2ImpExtResult> extractAnalyticsResultFromAnalyticsTag(AuctionContext auctionContext) {
        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getHookExecutionContext)
                .map(HookExecutionContext::getStageOutcomes)
                .map(stages -> stages.get(Stage.processed_auction_request))
                .stream()
                .flatMap(Collection::stream)
                .filter(stageExecutionOutcome -> "auction-request".equals(stageExecutionOutcome.getEntity()))
                .map(StageExecutionOutcome::getGroups)
                .flatMap(Collection::stream)
                .map(GroupExecutionOutcome::getHooks)
                .flatMap(Collection::stream)
                .filter(hook -> "greenbids-real-time-data".equals(hook.getHookId().getModuleCode()))
                .filter(hook -> hook.getStatus() == ExecutionStatus.success)
                .map(HookExecutionOutcome::getAnalyticsTags)
                .map(Tags::activities)
                .flatMap(Collection::stream)
                .filter(activity -> "greenbids-filter".equals(activity.name()))
                .map(Activity::results)
                .map(List::getFirst)
                .map(Result::values)
                .map(this::parseAnalyticsResult)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing));
    }

    private Map<String, Ortb2ImpExtResult> parseAnalyticsResult(ObjectNode analyticsResult) {
        try {
            final Map<String, Ortb2ImpExtResult> parsedAnalyticsResult = new HashMap<>();
            final Iterator<Map.Entry<String, JsonNode>> fields = analyticsResult.fields();

            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                final String impId = field.getKey();
                final JsonNode explorationResultNode = field.getValue();
                final Ortb2ImpExtResult ortb2ImpExtResult = jacksonMapper.mapper()
                        .treeToValue(explorationResultNode, Ortb2ImpExtResult.class);
                parsedAnalyticsResult.put(impId, ortb2ImpExtResult);
            }

            return parsedAnalyticsResult;
        } catch (JsonProcessingException e) {
            throw new PreBidException("Analytics result parsing error", e);
        }
    }

    private String greenbidsId(Map<String, Ortb2ImpExtResult> analyticsResultFromAnalyticsTag) {
        return Optional.ofNullable(analyticsResultFromAnalyticsTag)
                .map(Map::values)
                .map(Collection::stream)
                .flatMap(Stream::findFirst)
                .map(Ortb2ImpExtResult::getGreenbids)
                .map(ExplorationResult::getFingerprint)
                .orElseGet(() -> UUID.randomUUID().toString());
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

        final List<GreenbidsAdUnit> adUnitsWithBidResponses = imps.stream().map(imp ->
                createAdUnit(
                        imp, seatsWithBids, seatsWithNonBids, bidResponse.getCur(), analyticsResultFromAnalyticsTag))
                .toList();

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
        final List<NonBid> nonBids = bidRejectionTracker.getRejectedImps().entrySet().stream()
                .map(entry -> NonBid.of(entry.getKey(), entry.getValue()))
                .toList();

        return SeatNonBid.of(bidder, nonBids);
    }

    private GreenbidsAdUnit createAdUnit(
            Imp imp,
            Map<String, Bid> seatsWithBids,
            Map<String, NonBid> seatsWithNonBids,
            String currency,
            Map<String, Ortb2ImpExtResult> analyticsResultFromAnalyticsTag) {
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

        final Ortb2ImpResult ortb2ImpResult = Optional.ofNullable(analyticsResultFromAnalyticsTag)
                .map(analyticsResult -> analyticsResult.get(imp.getId()))
                .map(Ortb2ImpResult::of)
                .orElse(null);

        return GreenbidsAdUnit.builder()
                .code(adUnitCode)
                .unifiedCode(greenbidsUnifiedCode)
                .mediaTypes(mediaTypes)
                .bids(bids)
                .ortb2ImpResult(ortb2ImpResult)
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
