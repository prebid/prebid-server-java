package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.DataObject;
import com.iab.openrtb.request.ImageObject;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Request;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Asset;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtOptions;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.VideoStoredDataResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BidResponseCreator {

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };

    private static final String CACHE = "cache";
    private static final String PREBID_EXT = "prebid";

    private final CacheService cacheService;
    private final BidderCatalog bidderCatalog;
    private final EventsService eventsService;
    private final StoredRequestProcessor storedRequestProcessor;
    private final boolean generateBidId;
    private final int truncateAttrChars;
    private final Clock clock;
    private final JacksonMapper mapper;

    private final String cacheHost;
    private final String cachePath;
    private final String cacheAssetUrlTemplate;

    public BidResponseCreator(CacheService cacheService,
                              BidderCatalog bidderCatalog,
                              EventsService eventsService,
                              StoredRequestProcessor storedRequestProcessor,
                              boolean generateBidId,
                              int truncateAttrChars,
                              Clock clock,
                              JacksonMapper mapper) {

        this.cacheService = Objects.requireNonNull(cacheService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.eventsService = Objects.requireNonNull(eventsService);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.generateBidId = generateBidId;
        this.truncateAttrChars = validateTruncateAttrChars(truncateAttrChars);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);

        cacheHost = Objects.requireNonNull(cacheService.getEndpointHost());
        cachePath = Objects.requireNonNull(cacheService.getEndpointPath());
        cacheAssetUrlTemplate = Objects.requireNonNull(cacheService.getCachedAssetURLTemplate());
    }

    /**
     * Creates an OpenRTB {@link BidResponse} from the bids supplied by the bidder,
     * including processing of winning bids with cache IDs.
     */
    Future<BidResponse> create(List<BidderResponse> bidderResponses,
                               AuctionContext auctionContext,
                               BidRequestCacheInfo cacheInfo,
                               boolean debugEnabled) {

        final long auctionTimestamp = auctionTimestamp(auctionContext);

        if (isEmptyBidderResponses(bidderResponses)) {
            final BidRequest bidRequest = auctionContext.getBidRequest();
            return Future.succeededFuture(BidResponse.builder()
                    .id(bidRequest.getId())
                    .cur(bidRequest.getCur().get(0))
                    .nbr(0) // signal "Unknown Error"
                    .seatbid(Collections.emptyList())
                    .ext(mapper.mapper().valueToTree(toExtBidResponse(
                            bidderResponses,
                            auctionContext,
                            CacheServiceResult.empty(),
                            VideoStoredDataResult.empty(),
                            auctionTimestamp,
                            debugEnabled,
                            null)))
                    .build());
        }

        return cacheBidsAndCreateResponse(
                bidderResponses,
                auctionContext,
                cacheInfo,
                auctionTimestamp,
                debugEnabled);
    }

    private static int validateTruncateAttrChars(int truncateAttrChars) {
        if (truncateAttrChars < 0 || truncateAttrChars > 255) {
            throw new IllegalArgumentException("truncateAttrChars must be between 0 and 255");
        }

        return truncateAttrChars;
    }

    /**
     * Checks whether bidder responses are empty or contain no bids.
     */
    private static boolean isEmptyBidderResponses(List<BidderResponse> bidderResponses) {
        return bidderResponses.isEmpty() || bidderResponses.stream()
                .map(bidderResponse -> bidderResponse.getSeatBid().getBids())
                .allMatch(CollectionUtils::isEmpty);
    }

    private Future<BidResponse> cacheBidsAndCreateResponse(List<BidderResponse> bidderResponses,
                                                           AuctionContext auctionContext,
                                                           BidRequestCacheInfo cacheInfo,
                                                           long auctionTimestamp,
                                                           boolean debugEnabled) {

        final BidRequest bidRequest = auctionContext.getBidRequest();

        bidderResponses.forEach(BidResponseCreator::removeRedundantBids);

        ExtRequestTargeting targeting = targeting(bidRequest);
        final Set<Bid> winningBids = newOrEmptySet(targeting);
        final Set<Bid> winningBidsByBidder = newOrEmptySet(targeting);

        // determine winning bids only if targeting is present
        if (targeting != null) {
            populateWinningBids(bidderResponses, winningBids, winningBidsByBidder);
        }

        final Set<Bid> bidsToCache = cacheInfo.isShouldCacheWinningBidsOnly()
                ? winningBids
                : bidderResponses.stream().flatMap(BidResponseCreator::getBids).collect(Collectors.toSet());

        final EventsContext eventsContext = EventsContext.builder()
                .enabledForAccount(eventsEnabledForAccount(auctionContext))
                .enabledForRequest(eventsEnabledForRequest(auctionContext))
                .auctionTimestamp(auctionTimestamp)
                .integration(integrationFrom(auctionContext))
                .build();

        return toBidsWithCacheIds(
                bidderResponses,
                bidsToCache,
                auctionContext,
                cacheInfo,
                eventsContext)
                .compose(cacheResult -> videoStoredDataResult(bidRequest.getImp(), auctionContext.getTimeout())
                        .map(videoStoredDataResult -> toBidResponse(
                                bidderResponses,
                                auctionContext,
                                targeting,
                                winningBids,
                                winningBidsByBidder,
                                cacheInfo,
                                cacheResult,
                                videoStoredDataResult,
                                eventsContext,
                                auctionTimestamp,
                                debugEnabled)));
    }

    private static ExtRequestTargeting targeting(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        return prebid != null ? prebid.getTargeting() : null;
    }

    /**
     * Extracts auction timestamp from {@link ExtRequest} or get it from {@link Clock} if it is null.
     */
    private long auctionTimestamp(AuctionContext auctionContext) {
        final ExtRequest requestExt = auctionContext.getBidRequest().getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final Long auctionTimestamp = prebid != null ? prebid.getAuctiontimestamp() : null;
        return auctionTimestamp != null ? auctionTimestamp : clock.millis();
    }

    /**
     * Returns {@link ExtBidResponse} object, populated with response time, errors and debug info (if requested)
     * from all bidders.
     */
    private ExtBidResponse toExtBidResponse(List<BidderResponse> bidderResponses,
                                            AuctionContext auctionContext,
                                            CacheServiceResult cacheResult,
                                            VideoStoredDataResult videoStoredDataResult,
                                            long auctionTimestamp,
                                            boolean debugEnabled,
                                            Map<String, List<ExtBidderError>> bidErrors) {

        final BidRequest bidRequest = auctionContext.getBidRequest();

        final ExtResponseDebug extResponseDebug = debugEnabled
                ? ExtResponseDebug.of(toExtHttpCalls(bidderResponses, cacheResult), bidRequest)
                : null;
        final Map<String, List<ExtBidderError>> errors =
                toExtBidderErrors(bidderResponses, auctionContext, cacheResult, videoStoredDataResult, bidErrors);
        final Map<String, Integer> responseTimeMillis = toResponseTimes(bidderResponses, cacheResult);

        return ExtBidResponse.of(extResponseDebug, errors, responseTimeMillis, bidRequest.getTmax(), null,
                ExtBidResponsePrebid.of(auctionTimestamp));
    }

    private static void removeRedundantBids(BidderResponse bidderResponse) {
        final List<BidderBid> responseBidderBids = bidderResponse.getSeatBid().getBids();
        final Map<String, List<BidderBid>> impIdToBidderBid = responseBidderBids.stream()
                .collect(Collectors.groupingBy(bidderBid -> bidderBid.getBid().getImpid()));

        final List<BidderBid> mostValuableBids = impIdToBidderBid.values().stream()
                .map(BidResponseCreator::mostValuableBid)
                .collect(Collectors.toList());

        responseBidderBids.retainAll(mostValuableBids);
    }

    private static BidderBid mostValuableBid(List<BidderBid> bidderBids) {
        if (bidderBids.size() == 1) {
            return bidderBids.get(0);
        }

        final List<BidderBid> dealBidderBids = bidderBids.stream()
                .filter(bidderBid -> StringUtils.isNotBlank(bidderBid.getBid().getDealid()))
                .collect(Collectors.toList());

        List<BidderBid> processedBidderBids = dealBidderBids.isEmpty() ? bidderBids : dealBidderBids;

        return processedBidderBids.stream()
                .max(Comparator.comparing(bidderBid -> bidderBid.getBid().getPrice(), Comparator.naturalOrder()))
                .orElse(bidderBids.get(0));
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

    private static Stream<Bid> getBids(BidderResponse bidderResponse) {
        return Stream.of(bidderResponse)
                .map(BidderResponse::getSeatBid)
                .filter(Objects::nonNull)
                .map(BidderSeatBid::getBids)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(BidderBid::getBid);
    }

    /**
     * Corresponds cacheId (or null if not present) to each {@link Bid}.
     */
    private Future<CacheServiceResult> toBidsWithCacheIds(List<BidderResponse> bidderResponses,
                                                          Set<Bid> bidsToCache,
                                                          AuctionContext auctionContext,
                                                          BidRequestCacheInfo cacheInfo,
                                                          EventsContext eventsContext) {

        if (!cacheInfo.isDoCaching()) {
            return Future.succeededFuture(CacheServiceResult.of(null, null, toMapBidsWithEmptyCacheIds(bidsToCache)));
        }

        // do not submit bids with zero price to prebid cache
        final List<Bid> bidsWithNonZeroPrice = bidsToCache.stream()
                .filter(bid -> bid.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        final boolean shouldCacheVideoBids = cacheInfo.isShouldCacheVideoBids();

        final Map<String, List<String>> bidderToVideoBidIdsToModify =
                shouldCacheVideoBids && eventsEnabledForAccount(auctionContext)
                        ? getBidderAndVideoBidIdsToModify(bidderResponses, auctionContext.getBidRequest().getImp())
                        : Collections.emptyMap();
        final Map<String, List<String>> bidderToBidIds = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, bidderResponse -> getBids(bidderResponse)
                        .map(Bid::getId)
                        .collect(Collectors.toList())));

        final CacheContext cacheContext = CacheContext.builder()
                .cacheBidsTtl(cacheInfo.getCacheBidsTtl())
                .cacheVideoBidsTtl(cacheInfo.getCacheVideoBidsTtl())
                .shouldCacheBids(cacheInfo.isShouldCacheBids())
                .shouldCacheVideoBids(shouldCacheVideoBids)
                .bidderToVideoBidIdsToModify(bidderToVideoBidIdsToModify)
                .bidderToBidIds(bidderToBidIds)
                .build();

        return cacheService.cacheBidsOpenrtb(bidsWithNonZeroPrice, auctionContext, cacheContext, eventsContext)
                .map(cacheResult -> addNotCachedBids(cacheResult, bidsToCache));
    }

    private Map<String, List<String>> getBidderAndVideoBidIdsToModify(List<BidderResponse> bidderResponses,
                                                                      List<Imp> imps) {

        return bidderResponses.stream()
                .filter(bidderResponse -> bidderCatalog.isModifyingVastXmlAllowed(bidderResponse.getBidder()))
                .collect(Collectors.toMap(BidderResponse::getBidder, bidderResponse -> getBids(bidderResponse)
                        .filter(bid -> isVideoBid(bid, imps))
                        .map(Bid::getId)
                        .collect(Collectors.toList())));
    }

    private static boolean isVideoBid(Bid bid, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> imp.getVideo() != null)
                .map(Imp::getId)
                .anyMatch(impId -> bid.getImpid().equals(impId));
    }

    /**
     * Creates a map with {@link Bid} as a key and null as a value.
     */
    private static Map<Bid, CacheIdInfo> toMapBidsWithEmptyCacheIds(Set<Bid> bids) {
        return bids.stream()
                .collect(Collectors.toMap(Function.identity(), ignored -> CacheIdInfo.empty()));
    }

    /**
     * Adds bids with no cache id info.
     */
    private static CacheServiceResult addNotCachedBids(CacheServiceResult cacheResult, Set<Bid> bids) {
        final Map<Bid, CacheIdInfo> bidToCacheIdInfo = cacheResult.getCacheBids();

        if (bids.size() > bidToCacheIdInfo.size()) {
            final Map<Bid, CacheIdInfo> updatedBidToCacheIdInfo = new HashMap<>(bidToCacheIdInfo);
            for (Bid bid : bids) {
                if (!updatedBidToCacheIdInfo.containsKey(bid)) {
                    updatedBidToCacheIdInfo.put(bid, CacheIdInfo.empty());
                }
            }
            return CacheServiceResult.of(cacheResult.getHttpCall(), cacheResult.getError(), updatedBidToCacheIdInfo);
        }
        return cacheResult;
    }

    private static Map<String, List<ExtHttpCall>> toExtHttpCalls(List<BidderResponse> bidderResponses,
                                                                 CacheServiceResult cacheResult) {
        final Map<String, List<ExtHttpCall>> bidderHttpCalls = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder,
                        bidderResponse -> ListUtils.emptyIfNull(bidderResponse.getSeatBid().getHttpCalls())));

        final DebugHttpCall httpCall = cacheResult.getHttpCall();
        final ExtHttpCall cacheExtHttpCall = httpCall != null ? toExtHttpCall(httpCall) : null;
        final Map<String, List<ExtHttpCall>> cacheHttpCalls = cacheExtHttpCall != null
                ? Collections.singletonMap(CACHE, Collections.singletonList(cacheExtHttpCall))
                : Collections.emptyMap();

        final Map<String, List<ExtHttpCall>> httpCalls = new HashMap<>();
        httpCalls.putAll(bidderHttpCalls);
        httpCalls.putAll(cacheHttpCalls);
        return httpCalls.isEmpty() ? null : httpCalls;
    }

    private static ExtHttpCall toExtHttpCall(DebugHttpCall debugHttpCall) {
        return ExtHttpCall.builder()
                .uri(debugHttpCall.getRequestUri())
                .requestbody(debugHttpCall.getRequestBody())
                .status(debugHttpCall.getResponseStatus())
                .responsebody(debugHttpCall.getResponseBody())
                .build();
    }

    private Map<String, List<ExtBidderError>> toExtBidderErrors(List<BidderResponse> bidderResponses,
                                                                AuctionContext auctionContext,
                                                                CacheServiceResult cacheResult,
                                                                VideoStoredDataResult videoStoredDataResult,
                                                                Map<String, List<ExtBidderError>> bidErrors) {
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Map<String, List<ExtBidderError>> errors = new HashMap<>();

        errors.putAll(extractBidderErrors(bidderResponses));
        errors.putAll(extractDeprecatedBiddersErrors(bidRequest));
        errors.putAll(extractPrebidErrors(cacheResult, videoStoredDataResult, auctionContext));
        if (MapUtils.isNotEmpty(bidErrors)) {
            addBidErrors(errors, bidErrors);
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Returns a map with bidder name as a key and list of {@link ExtBidderError}s as a value.
     */
    private static Map<String, List<ExtBidderError>> extractBidderErrors(List<BidderResponse> bidderResponses) {
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
    private static Map<String, List<ExtBidderError>> extractPrebidErrors(CacheServiceResult cacheResult,
                                                                         VideoStoredDataResult videoStoredDataResult,
                                                                         AuctionContext auctionContext) {
        final List<ExtBidderError> cacheErrors = extractCacheErrors(cacheResult);
        final List<ExtBidderError> storedErrors = extractStoredErrors(videoStoredDataResult);
        final List<ExtBidderError> contextErrors = extractContextErrors(auctionContext);
        if (cacheErrors.isEmpty() && storedErrors.isEmpty() && contextErrors.isEmpty()) {
            return Collections.emptyMap();
        }

        final List<ExtBidderError> collectedErrors = Stream.concat(contextErrors.stream(),
                Stream.concat(storedErrors.stream(), cacheErrors.stream()))
                .collect(Collectors.toList());
        return Collections.singletonMap(PREBID_EXT, collectedErrors);
    }

    /**
     * Returns a list of {@link ExtBidderError}s of cache errors.
     */
    private static List<ExtBidderError> extractCacheErrors(CacheServiceResult cacheResult) {
        final Throwable error = cacheResult.getError();
        if (error != null) {
            final ExtBidderError extBidderError = ExtBidderError.of(BidderError.Type.generic.getCode(),
                    error.getMessage());
            return Collections.singletonList(extBidderError);
        }
        return Collections.emptyList();
    }

    /**
     * Returns a list of {@link ExtBidderError}s of stored request errors.
     */
    private static List<ExtBidderError> extractStoredErrors(VideoStoredDataResult videoStoredDataResult) {
        final List<String> errors = videoStoredDataResult.getErrors();
        if (CollectionUtils.isNotEmpty(errors)) {
            return errors.stream()
                    .map(message -> ExtBidderError.of(BidderError.Type.generic.getCode(), message))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Returns a list of {@link ExtBidderError}s of auction context prebid errors.
     */
    private static List<ExtBidderError> extractContextErrors(AuctionContext auctionContext) {
        return auctionContext.getPrebidErrors().stream()
                .map(message -> ExtBidderError.of(BidderError.Type.generic.getCode(), message))
                .collect(Collectors.toList());
    }

    /**
     * Adds bid errors: if value by key exists - add errors to its list, otherwise - add an entry.
     */
    private static void addBidErrors(Map<String, List<ExtBidderError>> errors,
                                     Map<String, List<ExtBidderError>> bidErrors) {
        for (Map.Entry<String, List<ExtBidderError>> errorEntry : bidErrors.entrySet()) {
            final List<ExtBidderError> extBidderErrors = errors.get(errorEntry.getKey());
            if (extBidderErrors != null) {
                extBidderErrors.addAll(errorEntry.getValue());
            } else {
                errors.put(errorEntry.getKey(), errorEntry.getValue());
            }
        }
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Returns a map with response time by bidders and cache.
     */
    private static Map<String, Integer> toResponseTimes(List<BidderResponse> bidderResponses,
                                                        CacheServiceResult cacheResult) {
        final Map<String, Integer> responseTimeMillis = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, BidderResponse::getResponseTime));

        final DebugHttpCall debugHttpCall = cacheResult.getHttpCall();
        final Integer cacheResponseTime = debugHttpCall != null ? debugHttpCall.getResponseTimeMillis() : null;
        if (cacheResponseTime != null) {
            responseTimeMillis.put(CACHE, cacheResponseTime);
        }
        return responseTimeMillis;
    }

    /**
     * Returns {@link BidResponse} based on list of {@link BidderResponse}s and {@link CacheServiceResult}.
     */
    private BidResponse toBidResponse(List<BidderResponse> bidderResponses,
                                      AuctionContext auctionContext,
                                      ExtRequestTargeting targeting,
                                      Set<Bid> winningBids,
                                      Set<Bid> winningBidsByBidder,
                                      BidRequestCacheInfo cacheInfo,
                                      CacheServiceResult cacheResult,
                                      VideoStoredDataResult videoStoredDataResult,
                                      EventsContext eventsContext,
                                      long auctionTimestamp,
                                      boolean debugEnabled) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();

        final Map<String, List<ExtBidderError>> bidErrors = new HashMap<>();
        final List<SeatBid> seatBids = bidderResponses.stream()
                .filter(bidderResponse -> !bidderResponse.getSeatBid().getBids().isEmpty())
                .map(bidderResponse -> toSeatBid(
                        bidderResponse,
                        targeting,
                        bidRequest,
                        winningBids,
                        winningBidsByBidder,
                        cacheInfo,
                        cacheResult.getCacheBids(),
                        videoStoredDataResult,
                        account,
                        bidErrors,
                        eventsContext))
                .collect(Collectors.toList());

        final ExtBidResponse extBidResponse = toExtBidResponse(
                bidderResponses,
                auctionContext,
                cacheResult,
                videoStoredDataResult,
                auctionTimestamp,
                debugEnabled,
                bidErrors);

        return BidResponse.builder()
                .id(bidRequest.getId())
                .cur(bidRequest.getCur().get(0))
                .seatbid(seatBids)
                .ext(mapper.mapper().valueToTree(extBidResponse))
                .build();
    }

    private Future<VideoStoredDataResult> videoStoredDataResult(List<Imp> imps, Timeout timeout) {
        final List<String> errors = new ArrayList<>();
        final List<Imp> storedVideoInjectableImps = new ArrayList<>();
        for (Imp imp : imps) {
            try {
                if (checkEchoVideoAttrs(imp)) {
                    storedVideoInjectableImps.add(imp);
                }
            } catch (InvalidRequestException e) {
                errors.add(e.getMessage());
            }
        }

        return storedRequestProcessor.videoStoredDataResult(storedVideoInjectableImps, errors, timeout)
                .otherwise(throwable -> VideoStoredDataResult.of(Collections.emptyMap(),
                        Collections.singletonList(throwable.getMessage())));
    }

    /**
     * Checks if imp.ext.prebid.options.echovideoattrs equals true.
     */
    private boolean checkEchoVideoAttrs(Imp imp) {
        if (imp.getExt() != null) {
            try {
                final ExtImp extImp = mapper.mapper().treeToValue(imp.getExt(), ExtImp.class);
                final ExtImpPrebid prebid = extImp.getPrebid();
                final ExtOptions options = prebid != null ? prebid.getOptions() : null;
                final Boolean echoVideoAttrs = options != null ? options.getEchoVideoAttrs() : null;
                return BooleanUtils.toBoolean(echoVideoAttrs);
            } catch (JsonProcessingException e) {
                throw new InvalidRequestException(String.format(
                        "Incorrect Imp extension format for Imp with id %s: %s", imp.getId(), e.getMessage()));
            }
        }
        return false;
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private SeatBid toSeatBid(BidderResponse bidderResponse,
                              ExtRequestTargeting targeting,
                              BidRequest bidRequest,
                              Set<Bid> winningBids,
                              Set<Bid> winningBidsByBidder,
                              BidRequestCacheInfo cacheInfo,
                              Map<Bid, CacheIdInfo> cachedBids,
                              VideoStoredDataResult videoStoredDataResult,
                              Account account,
                              Map<String, List<ExtBidderError>> bidErrors,
                              EventsContext eventsContext) {

        final String bidder = bidderResponse.getBidder();

        final List<Bid> bids = bidderResponse.getSeatBid().getBids().stream()
                .map(bidderBid -> toBid(
                        bidderBid,
                        bidder,
                        targeting,
                        bidRequest,
                        winningBids,
                        winningBidsByBidder,
                        cacheInfo,
                        cachedBids,
                        videoStoredDataResult.getImpIdToStoredVideo(),
                        account,
                        eventsContext,
                        bidErrors))
                .filter(Objects::nonNull)
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
    private Bid toBid(BidderBid bidderBid,
                      String bidder,
                      ExtRequestTargeting targeting,
                      BidRequest bidRequest,
                      Set<Bid> winningBids,
                      Set<Bid> winningBidsByBidder,
                      BidRequestCacheInfo cacheInfo,
                      Map<Bid, CacheIdInfo> bidsWithCacheIds,
                      Map<String, Video> impIdToStoredVideo,
                      Account account,
                      EventsContext eventsContext,
                      Map<String, List<ExtBidderError>> bidErrors) {

        final Bid bid = bidderBid.getBid();
        final BidType bidType = bidderBid.getType();

        final boolean isApp = bidRequest.getApp() != null;
        if (isApp && bidType.equals(BidType.xNative)) {
            try {
                addNativeMarkup(bid, bidRequest.getImp());
            } catch (PreBidException e) {
                bidErrors.putIfAbsent(bidder, new ArrayList<>());
                bidErrors.get(bidder)
                        .add(ExtBidderError.of(BidderError.Type.bad_server_response.getCode(), e.getMessage()));
                return null;
            }
        }

        final CacheIdInfo cacheIdInfo = bidsWithCacheIds.get(bid);
        final String cacheId = cacheIdInfo != null ? cacheIdInfo.getCacheId() : null;
        final String videoCacheId = cacheIdInfo != null ? cacheIdInfo.getVideoCacheId() : null;

        if ((videoCacheId != null && !cacheInfo.isReturnCreativeVideoBids())
                || (cacheId != null && !cacheInfo.isReturnCreativeBids())) {
            bid.setAdm(null);
        }

        final Map<String, String> targetingKeywords;
        final ExtResponseCache cache;

        if (targeting != null && winningBidsByBidder.contains(bid)) {
            final TargetingKeywordsCreator keywordsCreator = keywordsCreator(targeting, isApp, bidRequest, account);
            final Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType =
                    keywordsCreatorByBidType(targeting, isApp, bidRequest, account);
            final boolean isWinningBid = winningBids.contains(bid);
            targetingKeywords = keywordsCreatorByBidType.getOrDefault(bidType, keywordsCreator)
                    .makeFor(bid, bidder, isWinningBid, cacheId, videoCacheId);

            final CacheAsset bids = cacheId != null ? toCacheAsset(cacheId) : null;
            final CacheAsset vastXml = videoCacheId != null ? toCacheAsset(videoCacheId) : null;
            cache = bids != null || vastXml != null ? ExtResponseCache.of(bids, vastXml) : null;
        } else {
            targetingKeywords = null;
            cache = null;
        }

        final String generatedBidId = generateBidId ? UUID.randomUUID().toString() : null;
        final String eventBidId = ObjectUtils.defaultIfNull(generatedBidId, bid.getId());
        final Video storedVideo = impIdToStoredVideo.get(bid.getImpid());
        final Events events = createEvents(bidder, account, eventBidId, eventsContext);
        final ExtBidPrebidVideo extBidPrebidVideo = getExtBidPrebidVideo(bid.getExt());
        final ExtBidPrebid prebidExt = ExtBidPrebid.builder()
                .bidid(generatedBidId)
                .type(bidType)
                .targeting(targetingKeywords)
                .cache(cache)
                .storedRequestAttributes(storedVideo)
                .events(events)
                .video(extBidPrebidVideo)
                .build();

        final ExtPrebid<ExtBidPrebid, ObjectNode> bidExt = ExtPrebid.of(prebidExt, bid.getExt());
        bid.setExt(mapper.mapper().valueToTree(bidExt));

        return bid;
    }

    private void addNativeMarkup(Bid bid, List<Imp> imps) {
        final Response nativeMarkup;
        try {
            nativeMarkup = mapper.decodeValue(bid.getAdm(), Response.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }

        final List<Asset> responseAssets = nativeMarkup.getAssets();
        if (CollectionUtils.isNotEmpty(responseAssets)) {
            final Native nativeImp = imps.stream()
                    .filter(imp -> imp.getId().equals(bid.getImpid()) && imp.getXNative() != null)
                    .findFirst()
                    .map(Imp::getXNative)
                    .orElseThrow(() -> new PreBidException("Could not find native imp"));

            final Request nativeRequest;
            try {
                nativeRequest = mapper.mapper().readValue(nativeImp.getRequest(), Request.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(e.getMessage());
            }

            responseAssets.forEach(asset -> setAssetTypes(asset, nativeRequest.getAssets()));
            bid.setAdm(mapper.encode(nativeMarkup));
        }
    }

    private static void setAssetTypes(Asset responseAsset, List<com.iab.openrtb.request.Asset> requestAssets) {
        if (responseAsset.getImg() != null) {
            final ImageObject img = getAssetById(responseAsset.getId(), requestAssets).getImg();
            final Integer type = img != null ? img.getType() : null;
            if (type != null) {
                responseAsset.getImg().setType(type);
            } else {
                throw new PreBidException(String.format("Response has an Image asset with ID:%s present that doesn't "
                        + "exist in the request", responseAsset.getId()));
            }
        }
        if (responseAsset.getData() != null) {
            final DataObject data = getAssetById(responseAsset.getId(), requestAssets).getData();
            final Integer type = data != null ? data.getType() : null;
            if (type != null) {
                responseAsset.getData().setType(type);
            } else {
                throw new PreBidException(String.format("Response has a Data asset with ID:%s present that doesn't "
                        + "exist in the request", responseAsset.getId()));
            }
        }
    }

    private static com.iab.openrtb.request.Asset getAssetById(int assetId,
                                                              List<com.iab.openrtb.request.Asset> requestAssets) {
        return requestAssets.stream()
                .filter(asset -> asset.getId() == assetId)
                .findFirst()
                .orElse(com.iab.openrtb.request.Asset.EMPTY);
    }

    private Events createEvents(String bidder,
                                Account account,
                                String eventBidId,
                                EventsContext eventsContext) {

        return eventsContext.isEnabledForAccount() && eventsContext.isEnabledForRequest()
                ? eventsService.createEvent(
                eventBidId,
                bidder,
                account.getId(),
                eventsContext.getAuctionTimestamp(),
                eventsContext.getIntegration())
                : null;
    }

    private static boolean eventsEnabledForAccount(AuctionContext auctionContext) {
        return BooleanUtils.isTrue(auctionContext.getAccount().getEventsEnabled());
    }

    private static boolean eventsEnabledForRequest(AuctionContext auctionContext) {
        return eventsEnabledForChannel(auctionContext) || eventsAllowedByRequest(auctionContext);
    }

    private static boolean eventsEnabledForChannel(AuctionContext auctionContext) {
        final AccountAnalyticsConfig analyticsConfig = auctionContext.getAccount().getAnalyticsConfig();
        final Map<String, Boolean> channelConfig = analyticsConfig != null ? analyticsConfig.getAuctionEvents() : null;

        return channelConfig != null
                && BooleanUtils.toBoolean(channelConfig.get(channelFromRequest(auctionContext.getBidRequest())));
    }

    private static String channelFromRequest(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidChannel channel = prebid != null ? prebid.getChannel() : null;

        return channel != null ? channel.getName() : null;
    }

    private static boolean eventsAllowedByRequest(AuctionContext auctionContext) {
        final ExtRequest requestExt = auctionContext.getBidRequest().getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;

        return prebid != null && prebid.getEvents() != null;
    }

    /**
     * Extracts targeting keywords settings from the bid request and creates {@link TargetingKeywordsCreator}
     * instance if it is present.
     */
    private TargetingKeywordsCreator keywordsCreator(
            ExtRequestTargeting targeting, boolean isApp, BidRequest bidRequest, Account account) {

        final JsonNode priceGranularityNode = targeting.getPricegranularity();
        return priceGranularityNode == null || priceGranularityNode.isNull()
                ? null
                : createKeywordsCreator(targeting, isApp, priceGranularityNode, bidRequest, account);
    }

    /**
     * Returns a map of {@link BidType} to correspondent {@link TargetingKeywordsCreator}
     * extracted from {@link ExtRequestTargeting} if it exists.
     */
    private Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType(ExtRequestTargeting targeting,
                                                                            boolean isApp,
                                                                            BidRequest bidRequest,
                                                                            Account account) {

        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = targeting.getMediatypepricegranularity();

        if (mediaTypePriceGranularity == null) {
            return Collections.emptyMap();
        }

        final Map<BidType, TargetingKeywordsCreator> result = new EnumMap<>(BidType.class);

        final ObjectNode banner = mediaTypePriceGranularity.getBanner();
        final boolean isBannerNull = banner == null || banner.isNull();
        if (!isBannerNull) {
            result.put(BidType.banner, createKeywordsCreator(targeting, isApp, banner, bidRequest, account));
        }

        final ObjectNode video = mediaTypePriceGranularity.getVideo();
        final boolean isVideoNull = video == null || video.isNull();
        if (!isVideoNull) {
            result.put(BidType.video, createKeywordsCreator(targeting, isApp, video, bidRequest, account));
        }

        final ObjectNode xNative = mediaTypePriceGranularity.getXNative();
        final boolean isNativeNull = xNative == null || xNative.isNull();
        if (!isNativeNull) {
            result.put(BidType.xNative, createKeywordsCreator(targeting, isApp, xNative, bidRequest, account));
        }

        return result;
    }

    private TargetingKeywordsCreator createKeywordsCreator(ExtRequestTargeting targeting,
                                                           boolean isApp,
                                                           JsonNode priceGranularity,
                                                           BidRequest bidRequest,
                                                           Account account) {

        return TargetingKeywordsCreator.create(
                parsePriceGranularity(priceGranularity),
                targeting.getIncludewinners(),
                targeting.getIncludebidderkeys(),
                isApp,
                resolveTruncateAttrChars(targeting, account),
                cacheHost,
                cachePath,
                TargetingKeywordsResolver.create(bidRequest, mapper));
    }

    /**
     * Returns max targeting keyword length.
     */
    private int resolveTruncateAttrChars(ExtRequestTargeting targeting, Account account) {
        return ObjectUtils.firstNonNull(
                truncateAttrCharsOrNull(targeting.getTruncateattrchars()),
                truncateAttrCharsOrNull(account.getTruncateTargetAttr()),
                truncateAttrChars);
    }

    private static Integer truncateAttrCharsOrNull(Integer value) {
        return value != null && value >= 0 && value <= 255 ? value : null;
    }

    /**
     * Parse {@link JsonNode} to {@link List} of {@link ExtPriceGranularity}.
     * <p>
     * Throws {@link PreBidException} in case of errors during decoding price granularity.
     */
    private ExtPriceGranularity parsePriceGranularity(JsonNode priceGranularity) {
        try {
            return mapper.mapper().treeToValue(priceGranularity, ExtPriceGranularity.class);
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

    private String integrationFrom(AuctionContext auctionContext) {
        final ExtRequest extRequest = auctionContext.getBidRequest().getExt();
        final ExtRequestPrebid prebid = extRequest == null ? null : extRequest.getPrebid();

        return prebid != null ? prebid.getIntegration() : null;
    }

    /**
     * Creates {@link ExtBidPrebidVideo} from bid extension.
     */
    private ExtBidPrebidVideo getExtBidPrebidVideo(ObjectNode bidExt) {
        final ExtPrebid<ExtBidPrebid, ObjectNode> extPrebid = mapper.mapper()
                .convertValue(bidExt, EXT_PREBID_TYPE_REFERENCE);
        final ExtBidPrebid extBidPrebid = extPrebid != null ? extPrebid.getPrebid() : null;
        return extBidPrebid != null ? extBidPrebid.getVideo() : null;
    }
}
