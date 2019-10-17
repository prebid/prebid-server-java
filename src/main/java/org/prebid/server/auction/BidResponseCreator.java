package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.cache.model.CacheHttpCall;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BidResponseCreator {

    private static final String CACHE = "cache";
    private static final String PREBID_EXT = "prebid";

    private final BidderCatalog bidderCatalog;
    private final EventsService eventsService;
    private final String cacheHost;
    private final String cachePath;
    private final String cacheAssetUrlTemplate;

    public BidResponseCreator(BidderCatalog bidderCatalog, EventsService eventsService,
                              String cacheHost, String cachePath, String cacheAssetUrlTemplate) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.eventsService = Objects.requireNonNull(eventsService);
        this.cacheHost = Objects.requireNonNull(cacheHost);
        this.cachePath = Objects.requireNonNull(cachePath);
        this.cacheAssetUrlTemplate = Objects.requireNonNull(cacheAssetUrlTemplate);
    }

    /**
     * Creates an OpenRTB {@link BidResponse} from the bids supplied by the bidder,
     * including processing of winning bids with cache IDs.
     */
    BidResponse create(List<BidderResponse> bidderResponses, BidRequest bidRequest, ExtRequestTargeting targeting,
                       BidRequestCacheInfo cacheInfo, CacheServiceResult cacheResult,
                       Account account, boolean debugEnabled) {
        final ExtBidResponse bidResponseExt = toExtBidResponse(bidderResponses, bidRequest, cacheResult, debugEnabled);

        final BidResponse.BidResponseBuilder bidResponseBuilder = BidResponse.builder()
                .id(bidRequest.getId())
                .cur(bidRequest.getCur().get(0))
                .ext(Json.mapper.valueToTree(bidResponseExt));

        if (checkEmptyResponses(bidderResponses)) {
            return bidResponseBuilder
                    .nbr(2)  // signal "Invalid Request"
                    .seatbid(Collections.emptyList())
                    .build();
        }

        final Set<Bid> winningBids = newOrEmptySet(targeting);
        final Set<Bid> winningBidsByBidder = newOrEmptySet(targeting);

        // determine winning bids only if targeting is present
        if (targeting != null) {
            populateWinningBids(bidderResponses, winningBids, winningBidsByBidder);
        }

        final List<SeatBid> seatBids = bidderResponses.stream()
                .filter(bidderResponse -> !bidderResponse.getSeatBid().getBids().isEmpty())
                .map(bidderResponse -> toSeatBid(bidderResponse, targeting, bidRequest.getApp() != null,
                        winningBids, winningBidsByBidder, cacheInfo, cacheResult.getCacheBids(), account))
                .collect(Collectors.toList());

        return bidResponseBuilder
                .seatbid(seatBids)
                .build();
    }

    /**
     * Creates {@link ExtBidResponse} populated with response time, errors and debug info (if requested) from all
     * bidders.
     */
    private ExtBidResponse toExtBidResponse(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                            CacheServiceResult cacheResult, boolean debugEnabled) {

        final Map<String, List<ExtHttpCall>> httpCalls = debugEnabled ? toExtHttpCalls(bidderResponses, cacheResult)
                : null;
        final ExtResponseDebug extResponseDebug = httpCalls != null ? ExtResponseDebug.of(httpCalls, bidRequest) : null;

        final Map<String, List<ExtBidderError>> errors = toExtBidderErrors(bidderResponses, bidRequest, cacheResult);

        final Map<String, Integer> responseTimeMillis = toResponseTimes(bidderResponses, cacheResult);

        return ExtBidResponse.of(extResponseDebug, errors, responseTimeMillis, bidRequest.getTmax(), null);
    }

    private static Map<String, List<ExtHttpCall>> toExtHttpCalls(List<BidderResponse> bidderResponses,
                                                                 CacheServiceResult cacheResult) {
        final Map<String, List<ExtHttpCall>> bidderHttpCalls = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder,
                        bidderResponse -> ListUtils.emptyIfNull(bidderResponse.getSeatBid().getHttpCalls())));

        final CacheHttpCall httpCall = cacheResult.getHttpCall();
        final ExtHttpCall cacheExtHttpCall = httpCall != null ? toExtHttpCall(httpCall) : null;
        final Map<String, List<ExtHttpCall>> cacheHttpCalls = cacheExtHttpCall != null
                ? Collections.singletonMap(CACHE, Collections.singletonList(cacheExtHttpCall))
                : Collections.emptyMap();

        final Map<String, List<ExtHttpCall>> httpCalls = new HashMap<>();
        httpCalls.putAll(bidderHttpCalls);
        httpCalls.putAll(cacheHttpCalls);
        return httpCalls.isEmpty() ? null : httpCalls;
    }

    private static ExtHttpCall toExtHttpCall(CacheHttpCall cacheHttpCall) {
        final CacheHttpRequest request = cacheHttpCall.getRequest();
        final CacheHttpResponse response = cacheHttpCall.getResponse();

        return ExtHttpCall.builder()
                .uri(request.getUri())
                .requestbody(request.getBody())
                .status(response != null ? response.getStatusCode() : null)
                .responsebody(response != null ? response.getBody() : null)
                .build();
    }

    private Map<String, List<ExtBidderError>> toExtBidderErrors(List<BidderResponse> bidderResponses,
                                                                BidRequest bidRequest, CacheServiceResult cacheResult) {
        final Map<String, List<ExtBidderError>> errors = new HashMap<>();

        errors.putAll(extractBidderErrors(bidderResponses));
        errors.putAll(extractDeprecatedBiddersErrors(bidRequest));
        errors.putAll(extractCacheErrors(cacheResult));

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Returns a map with bidder name as a key and list of {@link ExtBidderError}s as a value.
     */
    private Map<String, List<ExtBidderError>> extractBidderErrors(List<BidderResponse> bidderResponses) {
        return bidderResponses.stream()
                .filter(bidderResponse -> CollectionUtils.isNotEmpty(bidderResponse.getSeatBid().getErrors()))
                .collect(Collectors.toMap(BidderResponse::getBidder,
                        bidderResponse -> errorsDetails(bidderResponse.getSeatBid().getErrors())));
    }

    /**
     * Maps a list of {@link BidderError} to a list of {@link ExtBidderError}s.
     */
    private static List<ExtBidderError> errorsDetails(List<BidderError> errors) {
        return errors.stream()
                .map(bidderError -> ExtBidderError.of(bidderError.getType().getCode(), bidderError.getMessage()))
                .collect(Collectors.toList());
    }

    /**
     * Returns a map with deprecated bidder name as a key and list of {@link ExtBidderError}s as a value.
     */
    private Map<String, List<ExtBidderError>> extractDeprecatedBiddersErrors(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .filter(imp -> imp.getExt() != null)
                .flatMap(imp -> asStream(imp.getExt().fieldNames()))
                .distinct()
                .filter(bidderCatalog::isDeprecatedName)
                .collect(Collectors.toMap(Function.identity(),
                        bidder -> Collections.singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                bidderCatalog.errorForDeprecatedName(bidder)))));
    }

    /**
     * Returns a singleton map with "prebid" as a key and list of {@link ExtBidderError}s cache errors as a value.
     */
    private static Map<String, List<ExtBidderError>> extractCacheErrors(CacheServiceResult cacheResult) {
        final Throwable error = cacheResult.getError();
        if (error != null) {
            final ExtBidderError extBidderError = ExtBidderError.of(BidderError.Type.generic.getCode(),
                    error.getMessage());
            return Collections.singletonMap(PREBID_EXT, Collections.singletonList(extBidderError));
        }
        return Collections.emptyMap();
    }

    /**
     * Returns a map with response time by bidders and cache.
     */
    private static Map<String, Integer> toResponseTimes(List<BidderResponse> bidderResponses,
                                                        CacheServiceResult cacheResult) {
        final Map<String, Integer> responseTimeMillis = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, BidderResponse::getResponseTime));

        final CacheHttpCall cacheHttpCall = cacheResult.getHttpCall();
        final Integer cacheResponseTime = cacheHttpCall != null ? cacheHttpCall.getResponseTimeMillis() : null;
        if (cacheResponseTime != null) {
            responseTimeMillis.put(CACHE, cacheResponseTime);
        }
        return responseTimeMillis;
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Checks whether bidder responses are empty or contain no bids.
     */
    private static boolean checkEmptyResponses(List<BidderResponse> bidderResponses) {
        return bidderResponses.isEmpty() || bidderResponses.stream()
                .map(bidderResponse -> bidderResponse.getSeatBid().getBids())
                .allMatch(CollectionUtils::isEmpty);
    }

    /**
     * Returns new {@link HashSet} in case of existing keywordsCreator or empty collection if null.
     */
    private static Set<Bid> newOrEmptySet(ExtRequestTargeting targeting) {
        return targeting != null ? new HashSet<>() : Collections.emptySet();
    }

    /**
     * Populates 2 input sets:
     * <p>
     * - winning bids for each impId (ad unit code) through all bidder responses.
     * <br>
     * - winning bids for each impId but for separate bidder.
     * <p>
     * Winning bid is the one with the highest price.
     */
    private static void populateWinningBids(List<BidderResponse> bidderResponses, Set<Bid> winningBids,
                                            Set<Bid> winningBidsByBidder) {
        final Map<String, Bid> winningBidsMap = new HashMap<>(); // impId -> Bid
        final Map<String, Map<String, Bid>> winningBidsByBidderMap = new HashMap<>(); // impId -> [bidder -> Bid]

        for (BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();

            for (BidderBid bidderBid : bidderResponse.getSeatBid().getBids()) {
                final Bid bid = bidderBid.getBid();

                tryAddWinningBid(bid, winningBidsMap);
                tryAddWinningBidByBidder(bid, bidder, winningBidsByBidderMap);
            }
        }

        winningBids.addAll(winningBidsMap.values());

        final List<Bid> bidsByBidder = winningBidsByBidderMap.values().stream()
                .flatMap(bidsByBidderMap -> bidsByBidderMap.values().stream())
                .collect(Collectors.toList());
        winningBidsByBidder.addAll(bidsByBidder);
    }


    /**
     * Tries to add a winning bid for each impId.
     */
    private static void tryAddWinningBid(Bid bid, Map<String, Bid> winningBids) {
        final String impId = bid.getImpid();

        if (!winningBids.containsKey(impId) || bid.getPrice().compareTo(winningBids.get(impId).getPrice()) > 0) {
            winningBids.put(impId, bid);
        }
    }

    /**
     * Tries to add a winning bid for each impId for separate bidder.
     */
    private static void tryAddWinningBidByBidder(Bid bid, String bidder,
                                                 Map<String, Map<String, Bid>> winningBidsByBidder) {
        final String impId = bid.getImpid();

        if (!winningBidsByBidder.containsKey(impId)) {
            final Map<String, Bid> bidsByBidder = new HashMap<>();
            bidsByBidder.put(bidder, bid);

            winningBidsByBidder.put(impId, bidsByBidder);
        } else {
            final Map<String, Bid> bidsByBidder = winningBidsByBidder.get(impId);

            if (!bidsByBidder.containsKey(bidder)
                    || bid.getPrice().compareTo(bidsByBidder.get(bidder).getPrice()) > 0) {
                bidsByBidder.put(bidder, bid);
            }
        }
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private SeatBid toSeatBid(BidderResponse bidderResponse, ExtRequestTargeting targeting, boolean isApp,
                              Set<Bid> winningBids, Set<Bid> winningBidsByBidder, BidRequestCacheInfo cacheInfo,
                              Map<Bid, CacheIdInfo> cachedBids, Account account) {
        final String bidder = bidderResponse.getBidder();

        final List<Bid> bids = bidderResponse.getSeatBid().getBids().stream()
                .map(bidderBid -> toBid(bidderBid, bidder, targeting, isApp, winningBids, winningBidsByBidder,
                        cacheInfo, cachedBids, account))
                .collect(Collectors.toList());

        return SeatBid.builder()
                .seat(bidder)
                .bid(bids)
                .group(0) // prebid cannot support roadblocking
                .build();
    }

    /**
     * Returns an OpenRTB {@link Bid} with "prebid" and "bidder" extension fields populated.
     */
    private Bid toBid(BidderBid bidderBid, String bidder, ExtRequestTargeting targeting, boolean isApp,
                      Set<Bid> winningBids, Set<Bid> winningBidsByBidder, BidRequestCacheInfo cacheInfo,
                      Map<Bid, CacheIdInfo> bidsWithCacheIds, Account account) {

        final Bid bid = bidderBid.getBid();
        final BidType bidType = bidderBid.getType();

        final boolean eventsEnabled = Objects.equals(account.getEventsEnabled(), true);
        final Events events = eventsEnabled ? eventsService.createEvent(bid.getId(), account.getId()) : null;

        final Map<String, String> targetingKeywords;
        final ExtResponseCache cache;

        if (targeting != null && winningBidsByBidder.contains(bid)) {
            final CacheIdInfo cacheIdInfo = bidsWithCacheIds.get(bid);
            final String cacheId = cacheIdInfo != null ? cacheIdInfo.getCacheId() : null;
            final String videoCacheId = cacheIdInfo != null ? cacheIdInfo.getVideoCacheId() : null;

            if ((videoCacheId != null && !cacheInfo.isReturnCreativeVideoBids())
                    || (cacheId != null && !cacheInfo.isReturnCreativeBids())) {
                bid.setAdm(null);
            }

            final TargetingKeywordsCreator keywordsCreator = keywordsCreator(targeting, isApp);
            final Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType =
                    keywordsCreatorByBidType(targeting, isApp);
            final boolean isWinningBid = winningBids.contains(bid);
            final String winUrl = eventsEnabled
                    ? HttpUtil.encodeUrl(eventsService.winUrlTargeting(account.getId()))
                    : null;
            targetingKeywords = keywordsCreatorByBidType.getOrDefault(bidType, keywordsCreator)
                    .makeFor(bid, bidder, isWinningBid, cacheId, videoCacheId, cacheHost, cachePath, winUrl);

            final CacheAsset bids = cacheId != null ? toCacheAsset(cacheId) : null;
            final CacheAsset vastXml = videoCacheId != null ? toCacheAsset(videoCacheId) : null;
            cache = bids != null || vastXml != null ? ExtResponseCache.of(bids, vastXml) : null;
        } else {
            targetingKeywords = null;
            cache = null;
        }

        final ExtBidPrebid prebidExt = ExtBidPrebid.of(bidType, targetingKeywords, cache, events);
        final ExtPrebid<ExtBidPrebid, ObjectNode> bidExt = ExtPrebid.of(prebidExt, bid.getExt());
        bid.setExt(Json.mapper.valueToTree(bidExt));

        return bid;
    }

    /**
     * Extracts targeting keywords settings from the bid request and creates {@link TargetingKeywordsCreator}
     * instance if they are present.
     * <p>
     */
    private static TargetingKeywordsCreator keywordsCreator(ExtRequestTargeting targeting, boolean isApp) {
        return TargetingKeywordsCreator.create(parsePriceGranularity(targeting.getPricegranularity()),
                targeting.getIncludewinners(), targeting.getIncludebidderkeys(), isApp);
    }

    /**
     * Returns a map of {@link BidType} to correspondent {@link TargetingKeywordsCreator}
     * extracted from {@link ExtRequestTargeting} if it exists.
     */
    private static Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType(ExtRequestTargeting targeting,
                                                                                   boolean isApp) {
        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = targeting.getMediatypepricegranularity();

        if (mediaTypePriceGranularity == null) {
            return Collections.emptyMap();
        }

        final Map<BidType, TargetingKeywordsCreator> result = new HashMap<>();

        final JsonNode banner = mediaTypePriceGranularity.getBanner();
        final boolean isBannerNull = banner == null || banner.isNull();
        if (!isBannerNull) {
            result.put(BidType.banner, TargetingKeywordsCreator.create(parsePriceGranularity(banner),
                    targeting.getIncludewinners(), targeting.getIncludebidderkeys(), isApp));
        }

        final JsonNode video = mediaTypePriceGranularity.getVideo();
        final boolean isVideoNull = video == null || video.isNull();
        if (!isVideoNull) {
            result.put(BidType.video, TargetingKeywordsCreator.create(parsePriceGranularity(video),
                    targeting.getIncludewinners(), targeting.getIncludebidderkeys(), isApp));
        }

        final JsonNode xNative = mediaTypePriceGranularity.getXNative();
        final boolean isNativeNull = xNative == null || xNative.isNull();
        if (!isNativeNull) {
            result.put(BidType.xNative, TargetingKeywordsCreator.create(parsePriceGranularity(xNative),
                    targeting.getIncludewinners(), targeting.getIncludebidderkeys(), isApp));
        }

        return result;
    }

    /**
     * Parse {@link JsonNode} to {@link List} of {@link ExtPriceGranularity}. Throws {@link PreBidException} in
     * case of errors during decoding pricegranularity.
     */
    private static ExtPriceGranularity parsePriceGranularity(JsonNode priceGranularity) {
        try {
            return Json.mapper.treeToValue(priceGranularity, ExtPriceGranularity.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.prebid.targeting.pricegranularity: %s",
                    e.getMessage()), e);
        }
    }

    /**
     * Creates {@link CacheAsset} for the given cache ID.
     */
    private CacheAsset toCacheAsset(String cacheId) {
        return CacheAsset.of(cacheAssetUrlTemplate.concat(cacheId), cacheId);
    }
}
