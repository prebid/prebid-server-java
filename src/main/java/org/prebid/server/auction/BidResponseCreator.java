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
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.TargetingBidInfo;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.identity.IdGeneratorType;
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
import org.prebid.server.vast.VastModifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
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

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>>() {
            };

    private static final String CACHE = "cache";
    private static final String PREBID_EXT = "prebid";
    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";
    private static final String SKADN_PROPERTY = "skadn";
    private static final Integer DEFAULT_BID_LIMIT_MIN = 1;

    private final CacheService cacheService;
    private final BidderCatalog bidderCatalog;
    private final VastModifier vastModifier;
    private final EventsService eventsService;
    private final StoredRequestProcessor storedRequestProcessor;
    private final IdGenerator bidIdGenerator;
    private final int truncateAttrChars;
    private final Clock clock;
    private final JacksonMapper mapper;

    private final String cacheHost;
    private final String cachePath;
    private final String cacheAssetUrlTemplate;
    private final WinningBidComparator winningBidComparator;

    public BidResponseCreator(CacheService cacheService,
                              BidderCatalog bidderCatalog,
                              VastModifier vastModifier,
                              EventsService eventsService,
                              StoredRequestProcessor storedRequestProcessor,
                              WinningBidComparator winningBidComparator,
                              IdGenerator bidIdGenerator,
                              int truncateAttrChars,
                              Clock clock,
                              JacksonMapper mapper) {

        this.cacheService = Objects.requireNonNull(cacheService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.vastModifier = Objects.requireNonNull(vastModifier);
        this.eventsService = Objects.requireNonNull(eventsService);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.winningBidComparator = Objects.requireNonNull(winningBidComparator);
        this.bidIdGenerator = Objects.requireNonNull(bidIdGenerator);
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
                               Map<String, MultiBidConfig> bidderToMultiBids,
                               boolean debugEnabled) {

        final long auctionTimestamp = auctionTimestamp(auctionContext);
        final Account account = auctionContext.getAccount();

        final EventsContext eventsContext = EventsContext.builder()
                .enabledForAccount(eventsEnabledForAccount(auctionContext))
                .enabledForRequest(eventsEnabledForRequest(auctionContext))
                .auctionTimestamp(auctionTimestamp)
                .integration(integrationFrom(auctionContext))
                .build();

        final Map<String, String> bidIdToGeneratedBidId = new HashMap<>();
        updateBidAdmInBidderResponses(bidderResponses, account, bidIdToGeneratedBidId, eventsContext);

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
                bidderToMultiBids,
                bidIdToGeneratedBidId,
                eventsContext,
                debugEnabled);
    }

    private void updateBidAdmInBidderResponses(List<BidderResponse> bidderResponses,
                                               Account account,
                                               Map<String, String> bidIdToGeneratedBidId,
                                               EventsContext eventsContext) {
        for (BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();

            for (BidderBid bidderBid : bidderResponse.getSeatBid().getBids()) {
                final Bid bid = bidderBid.getBid();
                final String generatedBidId = bidIdGenerator.getType() != IdGeneratorType.none
                        ? bidIdGenerator.generateId()
                        : null;
                final String bidId = bid.getId();
                bidIdToGeneratedBidId.put(bidId, generatedBidId);

                if (bidderBid.getType().equals(BidType.video)) {
                    final String adm = vastModifier.createBidVastXml(
                            bidder,
                            bid.getAdm(),
                            bid.getNurl(),
                            generatedBidId == null ? bidId : generatedBidId,
                            account.getId(),
                            eventsContext);

                    bid.setAdm(adm);
                }
            }
        }
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
                                                           Map<String, MultiBidConfig> bidderToMultiBids,
                                                           Map<String, String> bidIdToGeneratedBidId,
                                                           EventsContext eventsContext,
                                                           boolean debugEnabled) {
        final BidRequest bidRequest = auctionContext.getBidRequest();

        final List<Imp> imps = bidRequest.getImp();
        final Map<BidderResponse, List<TargetingBidInfo>> bidderResponseToTargetingBidInfos =
                toBidderResponseWithTargetingBidInfos(bidderResponses, imps, bidderToMultiBids, bidIdToGeneratedBidId);

        final Set<BidInfo> bidInfos = bidderResponseToTargetingBidInfos.values().stream()
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(Collection::stream)
                .map(TargetingBidInfo::getBidInfo)
                .collect(Collectors.toSet());

        final ExtRequestTargeting targeting = targeting(bidRequest);

        final Set<BidInfo> winningBidInfos = targeting == null
                ? null
                : bidderResponseToTargetingBidInfos.values().stream()
                .flatMap(Collection::stream)
                .filter(TargetingBidInfo::isWinningBid)
                .map(TargetingBidInfo::getBidInfo)
                .collect(Collectors.toSet());

        final Set<BidInfo> bidsToCache = cacheInfo.isShouldCacheWinningBidsOnly() ? winningBidInfos : bidInfos;

        return cacheBids(bidsToCache, auctionContext, cacheInfo, eventsContext)
                .compose(cacheResult -> videoStoredDataResult(auctionContext)
                        .map(videoStoredDataResult -> toBidResponse(
                                bidderResponseToTargetingBidInfos,
                                auctionContext,
                                targeting,
                                cacheInfo,
                                cacheResult,
                                videoStoredDataResult,
                                eventsContext,
                                debugEnabled)));
    }

    private static ExtRequestTargeting targeting(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        return prebid != null ? prebid.getTargeting() : null;
    }

    private Map<BidderResponse, List<TargetingBidInfo>> toBidderResponseWithTargetingBidInfos(
            List<BidderResponse> bidderResponses,
            List<Imp> imps,
            Map<String, MultiBidConfig> bidderToMultiBids,
            Map<String, String> bidIdToGeneratedBidId) {

        final Map<BidderResponse, List<BidInfo>> bidderResponseToReducedBidInfos = bidderResponses.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        bidderResponse -> toSortedMultiBidInfo(bidderResponse, imps, bidderToMultiBids,
                                bidIdToGeneratedBidId)));

        final Map<String, Map<String, List<BidInfo>>> impIdToBidderToBidInfos = bidderResponseToReducedBidInfos.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(
                        bidInfo -> bidInfo.getCorrespondingImp().getId(),
                        Collectors.groupingBy(BidInfo::getBidder)));

        // Best bids from bidders for imp
        final Set<BidInfo> winningBids = new HashSet<>();
        // All bids from bidder for imp
        final Set<BidInfo> winningBidsByBidder = new HashSet<>();

        for (Map<String, List<BidInfo>> bidderToBidInfos : impIdToBidderToBidInfos.values()) {

            bidderToBidInfos.values().forEach(winningBidsByBidder::addAll);

            bidderToBidInfos.values().stream()
                    .flatMap(Collection::stream)
                    .max(winningBidComparator)
                    .ifPresent(winningBids::add);
        }

        return bidderResponseToReducedBidInfos.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        responseToBidInfos -> toTargetingBidInfo(
                                responseToBidInfos.getValue(),
                                responseToBidInfos.getKey().getBidder(),
                                bidderToMultiBids,
                                winningBids,
                                winningBidsByBidder)));
    }

    private List<BidInfo> toSortedMultiBidInfo(BidderResponse bidderResponse,
                                               List<Imp> imps,
                                               Map<String, MultiBidConfig> bidderToMultiBids,
                                               Map<String, String> bidIdToGeneratedBidId) {
        final List<BidInfo> bidInfos = toBidInfo(bidderResponse, imps, bidIdToGeneratedBidId);
        final Map<String, List<BidInfo>> impIdToBidInfos = bidInfos.stream()
                .collect(Collectors.groupingBy(bidInfo -> bidInfo.getCorrespondingImp().getId()));

        final MultiBidConfig multiBid = bidderToMultiBids.get(bidderResponse.getBidder());
        final Integer bidLimit = multiBid != null ? multiBid.getMaxBids() : DEFAULT_BID_LIMIT_MIN;

        return impIdToBidInfos.values().stream()
                .map(impIdBidInfos -> sortReducedBidInfo(impIdBidInfos, bidLimit))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<BidInfo> toBidInfo(BidderResponse bidderResponse,
                                    List<Imp> imps,
                                    Map<String, String> bidIdToGeneratedBidId) {
        return Stream.of(bidderResponse)
                .map(BidderResponse::getSeatBid)
                .filter(Objects::nonNull)
                .map(BidderSeatBid::getBids)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bidderBid ->
                        toBidInfo(bidderBid.getBid(),
                                bidderBid.getType(),
                                imps,
                                bidderResponse.getBidder(),
                                bidIdToGeneratedBidId))
                .collect(Collectors.toList());
    }

    private BidInfo toBidInfo(Bid bid,
                              BidType type,
                              List<Imp> imps,
                              String bidder,
                              Map<String, String> bidIdToGeneratedBidId) {
        return BidInfo.builder()
                .generatedBidId(bidIdToGeneratedBidId.get(bid.getId()))
                .bid(bid)
                .bidType(type)
                .bidder(bidder)
                .correspondingImp(correspondingImp(bid, imps))
                .build();
    }

    private static Imp correspondingImp(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        return imps.stream()
                .filter(imp -> Objects.equals(impId, imp.getId()))
                .findFirst()
                // Should never occur. See ResponseBidValidator
                .orElseThrow(
                        () -> new PreBidException(String.format("Bid with impId %s doesn't have matched imp", impId)));
    }

    private List<BidInfo> sortReducedBidInfo(List<BidInfo> bidInfos, int limit) {
        return bidInfos.stream()
                .sorted(winningBidComparator.reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<TargetingBidInfo> toTargetingBidInfo(List<BidInfo> bidderBidInfos,
                                                      String bidder,
                                                      Map<String, MultiBidConfig> bidderToMultiBids,
                                                      Set<BidInfo> winningBids,
                                                      Set<BidInfo> winningBidsByBidder) {
        final Map<String, List<BidInfo>> impIdToBidInfos = bidderBidInfos.stream()
                .collect(Collectors.groupingBy(bidInfo -> bidInfo.getCorrespondingImp().getId()));

        return impIdToBidInfos.values().stream()
                .map(bidInfos -> createTargetingBidInfo(bidInfos, bidder, bidderToMultiBids, winningBids,
                        winningBidsByBidder))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<TargetingBidInfo> createTargetingBidInfo(List<BidInfo> bidderImpIdBidInfos,
                                                          String bidder,
                                                          Map<String, MultiBidConfig> bidderToMultiBids,
                                                          Set<BidInfo> winningBids,
                                                          Set<BidInfo> winningBidsByBidder) {
        final List<TargetingBidInfo> targetingBidInfos = new ArrayList<>();

        final MultiBidConfig multiBid = bidderToMultiBids.get(bidder);
        final String bidderCodePrefix = multiBid != null ? multiBid.getTargetBidderCodePrefix() : null;

        final int multiBidSize = bidderImpIdBidInfos.size();
        for (int i = 0; i < multiBidSize; i++) {
            // first bid have highest value and can't be extra bid.
            final boolean isFirstBid = i == 0;
            final String targetingBidderCode = isFirstBid
                    ? bidder
                    : bidderCodePrefix == null ? null : String.format("%s%s", bidderCodePrefix, i + 1);

            final BidInfo bidInfo = bidderImpIdBidInfos.get(i);
            final TargetingBidInfo targetingBidInfo = TargetingBidInfo.builder()
                    .bidInfo(bidInfo)
                    .isTargetingEnabled(targetingBidderCode != null)
                    .isBidderWinningBid(winningBidsByBidder.contains(bidInfo))
                    .isWinningBid(winningBids.contains(bidInfo))
                    .isAddTargetBidderCode(targetingBidderCode != null && multiBidSize > 1)
                    .bidderCode(targetingBidderCode)
                    .build();

            targetingBidInfos.add(targetingBidInfo);
        }

        return targetingBidInfos;
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
    private ExtBidResponse toExtBidResponse(Collection<BidderResponse> bidderResponses,
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
        final Map<String, List<ExtBidderError>> warnings = debugEnabled
                ? toExtBidderWarnings(auctionContext)
                : null;
        final Map<String, Integer> responseTimeMillis = toResponseTimes(bidderResponses, cacheResult);

        return ExtBidResponse.of(extResponseDebug, errors, warnings, responseTimeMillis, bidRequest.getTmax(), null,
                ExtBidResponsePrebid.of(auctionTimestamp));
    }

    /**
     * Corresponds cacheId (or null if not present) to each {@link Bid}.
     */
    private Future<CacheServiceResult> cacheBids(Set<BidInfo> bidsToCache,
                                                 AuctionContext auctionContext,
                                                 BidRequestCacheInfo cacheInfo,
                                                 EventsContext eventsContext) {
        if (!cacheInfo.isDoCaching()) {
            return Future.succeededFuture(CacheServiceResult.of(null, null, toMapBidsWithEmptyCacheIds(bidsToCache)));
        }

        // do not submit non deals bids with zero price to prebid cache
        final List<BidInfo> bidsValidToBeCached = bidsToCache.stream()
                .filter(BidResponseCreator::isValidForCaching)
                .collect(Collectors.toList());

        final CacheContext cacheContext = CacheContext.builder()
                .cacheBidsTtl(cacheInfo.getCacheBidsTtl())
                .cacheVideoBidsTtl(cacheInfo.getCacheVideoBidsTtl())
                .shouldCacheBids(cacheInfo.isShouldCacheBids())
                .shouldCacheVideoBids(cacheInfo.isShouldCacheVideoBids())
                .build();

        return cacheService.cacheBidsOpenrtb(bidsValidToBeCached, auctionContext, cacheContext, eventsContext)
                .map(cacheResult -> addNotCachedBids(cacheResult, bidsToCache));
    }

    private static boolean isValidForCaching(BidInfo bidInfo) {
        final Bid bid = bidInfo.getBid();
        final BigDecimal price = bid.getPrice();
        return bid.getDealid() != null ? price.compareTo(BigDecimal.ZERO) >= 0 : price.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Creates a map with {@link Bid} as a key and null as a value.
     */
    private static Map<Bid, CacheInfo> toMapBidsWithEmptyCacheIds(Set<BidInfo> bids) {
        return bids.stream()
                .map(BidInfo::getBid)
                .collect(Collectors.toMap(Function.identity(), ignored -> CacheInfo.empty()));
    }

    /**
     * Adds bids with no cache id info.
     */
    private static CacheServiceResult addNotCachedBids(CacheServiceResult cacheResult, Set<BidInfo> bidInfos) {
        final Map<Bid, CacheInfo> bidToCacheId = cacheResult.getCacheBids();

        if (bidInfos.size() > bidToCacheId.size()) {
            final Map<Bid, CacheInfo> updatedBidToCacheInfo = new HashMap<>(bidToCacheId);
            for (BidInfo bidInfo : bidInfos) {
                final Bid bid = bidInfo.getBid();
                if (!updatedBidToCacheInfo.containsKey(bid)) {
                    updatedBidToCacheInfo.put(bid, CacheInfo.empty());
                }
            }
            return CacheServiceResult.of(cacheResult.getHttpCall(), cacheResult.getError(), updatedBidToCacheInfo);
        }
        return cacheResult;
    }

    private static Map<String, List<ExtHttpCall>> toExtHttpCalls(Collection<BidderResponse> bidderResponses,
                                                                 CacheServiceResult cacheResult) {
        final Map<String, List<ExtHttpCall>> bidderHttpCalls = bidderResponses.stream()
                .collect(Collectors.toMap(
                        BidderResponse::getBidder,
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

    private Map<String, List<ExtBidderError>> toExtBidderErrors(Collection<BidderResponse> bidderResponses,
                                                                AuctionContext auctionContext,
                                                                CacheServiceResult cacheResult,
                                                                VideoStoredDataResult videoStoredDataResult,
                                                                Map<String, List<ExtBidderError>> bidErrors) {
        final Map<String, List<ExtBidderError>> errors = new HashMap<>();

        errors.putAll(extractBidderErrors(bidderResponses));
        errors.putAll(extractDeprecatedBiddersErrors(auctionContext.getBidRequest()));
        errors.putAll(extractPrebidErrors(videoStoredDataResult, auctionContext));
        errors.putAll(extractCacheErrors(cacheResult));
        if (MapUtils.isNotEmpty(bidErrors)) {
            addBidErrors(errors, bidErrors);
        }

        return errors.isEmpty() ? null : errors;
    }

    /**
     * Returns a map with bidder name as a key and list of {@link ExtBidderError}s as a value.
     */
    private static Map<String, List<ExtBidderError>> extractBidderErrors(Collection<BidderResponse> bidderResponses) {
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
     * Returns a singleton map with "prebid" as a key and list of {@link ExtBidderError}s errors as a value.
     */
    private static Map<String, List<ExtBidderError>> extractPrebidErrors(VideoStoredDataResult videoStoredDataResult,
                                                                         AuctionContext auctionContext) {
        final List<ExtBidderError> storedErrors = extractStoredErrors(videoStoredDataResult);
        final List<ExtBidderError> contextErrors = extractContextErrors(auctionContext);
        if (storedErrors.isEmpty() && contextErrors.isEmpty()) {
            return Collections.emptyMap();
        }

        final List<ExtBidderError> collectedErrors = Stream.concat(contextErrors.stream(), storedErrors.stream())
                .collect(Collectors.toList());
        return Collections.singletonMap(PREBID_EXT, collectedErrors);
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
     * Returns a singleton map with "cache" as a key and list of {@link ExtBidderError}s cache errors as a value.
     */
    private static Map<String, List<ExtBidderError>> extractCacheErrors(CacheServiceResult cacheResult) {
        final Throwable error = cacheResult.getError();
        if (error != null) {
            final ExtBidderError extBidderError = ExtBidderError.of(BidderError.Type.generic.getCode(),
                    error.getMessage());
            return Collections.singletonMap(CACHE, Collections.singletonList(extBidderError));
        }
        return Collections.emptyMap();
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

    private Map<String, List<ExtBidderError>> toExtBidderWarnings(AuctionContext auctionContext) {
        final Map<String, List<ExtBidderError>> warnings = new HashMap<>(extractContextWarnings(auctionContext));

        return warnings.isEmpty() ? null : warnings;
    }

    private static Map<String, List<ExtBidderError>> extractContextWarnings(AuctionContext auctionContext) {
        final List<ExtBidderError> contextWarnings = auctionContext.getDebugWarnings().stream()
                .map(message -> ExtBidderError.of(BidderError.Type.generic.getCode(), message))
                .collect(Collectors.toList());

        return contextWarnings.isEmpty()
                ? Collections.emptyMap()
                : Collections.singletonMap(PREBID_EXT, contextWarnings);
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Returns a map with response time by bidders and cache.
     */
    private static Map<String, Integer> toResponseTimes(Collection<BidderResponse> bidderResponses,
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
    private BidResponse toBidResponse(Map<BidderResponse, List<TargetingBidInfo>> bidderResponseToTargetingBidInfos,
                                      AuctionContext auctionContext,
                                      ExtRequestTargeting targeting,
                                      BidRequestCacheInfo requestCacheInfo,
                                      CacheServiceResult cacheResult,
                                      VideoStoredDataResult videoStoredDataResult,
                                      EventsContext eventsContext,
                                      boolean debugEnabled) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();

        final Map<String, List<ExtBidderError>> bidErrors = new HashMap<>();
        final List<SeatBid> seatBids = bidderResponseToTargetingBidInfos.values().stream()
                .filter(CollectionUtils::isNotEmpty)
                .map(targetingBidInfos -> toSeatBid(
                        targetingBidInfos,
                        targeting,
                        bidRequest,
                        requestCacheInfo,
                        cacheResult.getCacheBids(),
                        videoStoredDataResult,
                        account,
                        bidErrors,
                        eventsContext))
                .collect(Collectors.toList());

        final Long auctionTimestamp = eventsContext.getAuctionTimestamp();
        final ExtBidResponse extBidResponse = toExtBidResponse(
                bidderResponseToTargetingBidInfos.keySet(),
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

    private Future<VideoStoredDataResult> videoStoredDataResult(AuctionContext auctionContext) {
        final List<Imp> imps = auctionContext.getBidRequest().getImp();
        final String accountId = auctionContext.getAccount().getId();
        final Timeout timeout = auctionContext.getTimeout();

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

        return storedRequestProcessor.videoStoredDataResult(accountId, storedVideoInjectableImps, errors, timeout)
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
    private SeatBid toSeatBid(List<TargetingBidInfo> targetingBidInfos,
                              ExtRequestTargeting targeting,
                              BidRequest bidRequest,
                              BidRequestCacheInfo requestCacheInfo,
                              Map<Bid, CacheInfo> bidToCacheInfo,
                              VideoStoredDataResult videoStoredDataResult,
                              Account account,
                              Map<String, List<ExtBidderError>> bidErrors,
                              EventsContext eventsContext) {

        final String bidder = targetingBidInfos.stream()
                .map(TargetingBidInfo::getBidInfo)
                .map(BidInfo::getBidder)
                .findFirst()
                // Should never occur
                .orElseThrow(() -> new IllegalArgumentException("Bidder was not defined for bidInfo"));

        final List<Bid> bids = targetingBidInfos.stream()
                .map(targetingBidInfo -> toBid(
                        targetingBidInfo,
                        targeting,
                        bidRequest,
                        requestCacheInfo,
                        bidToCacheInfo,
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
    private Bid toBid(TargetingBidInfo targetingBidInfo,
                      ExtRequestTargeting targeting,
                      BidRequest bidRequest,
                      BidRequestCacheInfo requestCacheInfo,
                      Map<Bid, CacheInfo> bidsWithCacheIds,
                      Map<String, Video> impIdToStoredVideo,
                      Account account,
                      EventsContext eventsContext,
                      Map<String, List<ExtBidderError>> bidErrors) {
        final BidInfo bidInfo = targetingBidInfo.getBidInfo();
        final Bid bid = bidInfo.getBid();
        final BidType bidType = bidInfo.getBidType();
        final String bidder = bidInfo.getBidder();

        final CacheInfo cacheInfo = bidsWithCacheIds.get(bid);
        final String cacheId = cacheInfo != null ? cacheInfo.getCacheId() : null;
        final String videoCacheId = cacheInfo != null ? cacheInfo.getVideoCacheId() : null;

        if ((videoCacheId != null && !requestCacheInfo.isReturnCreativeVideoBids())
                || (cacheId != null && !requestCacheInfo.isReturnCreativeBids())) {
            bid.setAdm(null);
        }

        final boolean isApp = bidRequest.getApp() != null;
        if (isApp && bidType.equals(BidType.xNative) && bid.getAdm() != null) {
            try {
                addNativeMarkup(bid, bidRequest.getImp());
            } catch (PreBidException e) {
                bidErrors.computeIfAbsent(bidder, ignored -> new ArrayList<>())
                        .add(ExtBidderError.of(BidderError.Type.bad_server_response.getCode(), e.getMessage()));
                return null;
            }
        }

        final Map<String, String> targetingKeywords;
        final String bidderCode = targetingBidInfo.getBidderCode();
        if (targeting != null && targetingBidInfo.isTargetingEnabled() && targetingBidInfo.isBidderWinningBid()) {
            final TargetingKeywordsCreator keywordsCreator = resolveKeywordsCreator(bidType, targeting, isApp,
                    bidRequest, account);

            final boolean isWinningBid = targetingBidInfo.isWinningBid();
            targetingKeywords = keywordsCreator.makeFor(bid, bidderCode, isWinningBid, cacheId, bidType.getName(),
                    videoCacheId);
        } else {
            targetingKeywords = null;
        }

        final CacheAsset bids = cacheId != null ? toCacheAsset(cacheId) : null;
        final CacheAsset vastXml = videoCacheId != null ? toCacheAsset(videoCacheId) : null;
        final ExtResponseCache cache = bids != null || vastXml != null ? ExtResponseCache.of(bids, vastXml) : null;

        final Video storedVideo = impIdToStoredVideo.get(bid.getImpid());
        final Events events = createEvents(bidder, account, bidInfo.getBidId(), eventsContext);
        final ExtBidPrebidVideo extBidPrebidVideo = getExtBidPrebidVideo(bid.getExt());

        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder()
                .bidid(bidInfo.getGeneratedBidId())
                .type(bidType)
                .targeting(targetingKeywords)
                .targetBidderCode(targetingBidInfo.isAddTargetBidderCode() ? bidderCode : null)
                .cache(cache)
                .storedRequestAttributes(storedVideo)
                .events(events)
                .video(extBidPrebidVideo)
                .build();

        bid.setExt(createBidExt(bid.getExt(), extBidPrebid));

        final Integer ttl = cacheInfo != null ? ObjectUtils.max(cacheInfo.getTtl(), cacheInfo.getVideoTtl()) : null;
        bid.setExp(ttl);

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
        final AccountAnalyticsConfig analyticsConfig = ObjectUtils.defaultIfNull(
                auctionContext.getAccount().getAnalyticsConfig(), AccountAnalyticsConfig.fallback());
        final Map<String, Boolean> channelConfig = analyticsConfig.getAuctionEvents();

        final String channelFromRequest = channelFromRequest(auctionContext.getBidRequest());

        return MapUtils.emptyIfNull(channelConfig).entrySet().stream()
                .filter(entry -> StringUtils.equalsIgnoreCase(channelFromRequest, entry.getKey()))
                .findFirst()
                .map(entry -> BooleanUtils.isTrue(entry.getValue()))
                .orElse(Boolean.FALSE);
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

    private TargetingKeywordsCreator resolveKeywordsCreator(BidType bidType,
                                                            ExtRequestTargeting targeting,
                                                            boolean isApp,
                                                            BidRequest bidRequest,
                                                            Account account) {
        final Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType =
                keywordsCreatorByBidType(targeting, isApp, bidRequest, account);

        return keywordsCreatorByBidType.getOrDefault(bidType, keywordsCreator(targeting, isApp, bidRequest, account));
    }

    /**
     * Extracts targeting keywords settings from the bid request and creates {@link TargetingKeywordsCreator}
     * instance if it is present.
     */
    private TargetingKeywordsCreator keywordsCreator(ExtRequestTargeting targeting,
                                                     boolean isApp,
                                                     BidRequest bidRequest,
                                                     Account account) {

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
                BooleanUtils.isTrue(targeting.getIncludeformat()),
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

    // will be updated in https://github.com/prebid/prebid-server-java/pull/1126
    private ObjectNode createBidExt(ObjectNode existingBidExt, ExtBidPrebid extBidPrebid) {
        JsonNode skadnObject = mapper.mapper().createObjectNode();
        JsonNode origBidPrice = null;
        JsonNode origBidCur = null;
        if (existingBidExt != null && !existingBidExt.isEmpty()) {
            skadnObject = getAndRemoveProperty(SKADN_PROPERTY, existingBidExt);
            origBidPrice = getAndRemoveProperty(ORIGINAL_BID_CPM, existingBidExt);
            origBidCur = getAndRemoveProperty(ORIGINAL_BID_CURRENCY, existingBidExt);
        }
        final ObjectNode extPrebidBidder = existingBidExt != null && !existingBidExt.isEmpty() ? existingBidExt : null;
        final ExtPrebid<ExtBidPrebid, ObjectNode> bidExt = ExtPrebid.of(extBidPrebid, extPrebidBidder);
        final ObjectNode updatedBidExt = mapper.mapper().valueToTree(bidExt);
        if (skadnObject != null && !skadnObject.isEmpty()) {
            updatedBidExt.set(SKADN_PROPERTY, skadnObject);
        }
        if (origBidPrice != null) {
            updatedBidExt.set(ORIGINAL_BID_CPM, origBidPrice);
        }
        if (origBidCur != null) {
            updatedBidExt.set(ORIGINAL_BID_CURRENCY, origBidCur);
        }

        return updatedBidExt;
    }

    private JsonNode getAndRemoveProperty(String propertyName, ObjectNode node) {
        final JsonNode property = node.get(propertyName);
        node.remove(propertyName);

        return property;
    }
}
