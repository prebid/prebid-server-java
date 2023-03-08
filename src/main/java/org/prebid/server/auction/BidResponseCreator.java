package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.categorymapping.CategoryMappingService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidInfo;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.BidderResponseInfo;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.CategoryMappingResult;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.TargetingInfo;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.requestfactory.Ortb2ImplicitParametersResolver;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.BidderSeatBidInfo;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.events.EventsContext;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.identity.IdGeneratorType;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpAuctionEnvironment;
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
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponseFledge;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtDebugPgmetrics;
import org.prebid.server.proto.openrtb.ext.response.ExtDebugTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.NonBid;
import org.prebid.server.proto.openrtb.ext.response.seatnonbid.SeatNonBid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountAuctionEventConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.settings.model.VideoStoredDataResult;
import org.prebid.server.util.LineItemUtil;
import org.prebid.server.util.StreamUtil;
import org.prebid.server.vast.VastModifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidResponseCreator {

    private static final String CACHE = "cache";
    private static final String PREBID_EXT = "prebid";
    private static final Integer DEFAULT_BID_LIMIT_MIN = 1;

    private final CacheService cacheService;
    private final BidderCatalog bidderCatalog;
    private final VastModifier vastModifier;
    private final EventsService eventsService;
    private final StoredRequestProcessor storedRequestProcessor;
    private final WinningBidComparatorFactory winningBidComparatorFactory;
    private final IdGenerator bidIdGenerator;
    private final HookStageExecutor hookStageExecutor;
    private final CategoryMappingService categoryMappingService;
    private final int truncateAttrChars;
    private final Clock clock;
    private final JacksonMapper mapper;

    private final String cacheHost;
    private final String cachePath;
    private final String cacheAssetUrlTemplate;

    public BidResponseCreator(CacheService cacheService,
                              BidderCatalog bidderCatalog,
                              VastModifier vastModifier,
                              EventsService eventsService,
                              StoredRequestProcessor storedRequestProcessor,
                              WinningBidComparatorFactory winningBidComparatorFactory,
                              IdGenerator bidIdGenerator,
                              HookStageExecutor hookStageExecutor,
                              CategoryMappingService categoryMappingService,
                              int truncateAttrChars,
                              Clock clock,
                              JacksonMapper mapper) {

        this.cacheService = Objects.requireNonNull(cacheService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.vastModifier = Objects.requireNonNull(vastModifier);
        this.eventsService = Objects.requireNonNull(eventsService);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.winningBidComparatorFactory = Objects.requireNonNull(winningBidComparatorFactory);
        this.bidIdGenerator = Objects.requireNonNull(bidIdGenerator);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.categoryMappingService = Objects.requireNonNull(categoryMappingService);
        this.truncateAttrChars = validateTruncateAttrChars(truncateAttrChars);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);

        cacheHost = Objects.requireNonNull(cacheService.getEndpointHost());
        cachePath = Objects.requireNonNull(cacheService.getEndpointPath());
        cacheAssetUrlTemplate = Objects.requireNonNull(cacheService.getCachedAssetURLTemplate());
    }

    private static int validateTruncateAttrChars(int truncateAttrChars) {
        if (truncateAttrChars < 0 || truncateAttrChars > 255) {
            throw new IllegalArgumentException("truncateAttrChars must be between 0 and 255");
        }
        return truncateAttrChars;
    }

    /**
     * Creates an OpenRTB {@link BidResponse} from the bids supplied by the bidder,
     * including processing of winning bids with cache IDs.
     */
    Future<BidResponse> create(AuctionContext auctionContext,
                               BidRequestCacheInfo cacheInfo,
                               Map<String, MultiBidConfig> bidderToMultiBids) {

        final List<AuctionParticipation> auctionParticipations = auctionContext.getAuctionParticipations();
        final List<Imp> imps = auctionContext.getBidRequest().getImp();
        final EventsContext eventsContext = createEventsContext(auctionContext);

        final List<BidderResponse> bidderResponses = auctionParticipations.stream()
                .filter(auctionParticipation -> !auctionParticipation.isRequestBlocked())
                .map(AuctionParticipation::getBidderResponse)
                .toList();

        return videoStoredDataResult(auctionContext).compose(videoStoredDataResult ->
                invokeProcessedBidderResponseHooks(
                        updateBids(bidderResponses, videoStoredDataResult, auctionContext, eventsContext, imps),
                        auctionContext)

                        .compose(updatedResponses ->
                                invokeAllProcessedBidResponsesHook(updatedResponses, auctionContext))

                        .compose(updatedResponses ->
                                createCategoryMapping(auctionContext, updatedResponses))

                        .compose(categoryMappingResult -> cacheBidsAndCreateResponse(
                                toBidderResponseInfos(categoryMappingResult, imps),
                                auctionContext,
                                cacheInfo,
                                bidderToMultiBids,
                                videoStoredDataResult,
                                eventsContext))

                        .map(bidResponse -> populateSeatNonBid(auctionContext, bidResponse)));
    }

    private List<BidderResponse> updateBids(List<BidderResponse> bidderResponses,
                                            VideoStoredDataResult videoStoredDataResult,
                                            AuctionContext auctionContext,
                                            EventsContext eventsContext,
                                            List<Imp> imps) {

        final List<BidderResponse> result = new ArrayList<>();

        for (final BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();

            final List<BidderBid> modifiedBidderBids = new ArrayList<>();
            final BidderSeatBid seatBid = bidderResponse.getSeatBid();
            for (final BidderBid bidderBid : seatBid.getBids()) {
                final Bid receivedBid = bidderBid.getBid();
                final BidType bidType = bidderBid.getType();

                final Imp correspondingImp = correspondingImp(receivedBid, imps);
                final ExtDealLine extDealLine = LineItemUtil.extDealLineFrom(receivedBid, correspondingImp, mapper);
                final String lineItemId = extDealLine != null ? extDealLine.getLineItemId() : null;

                final Bid updatedBid = updateBid(
                        receivedBid, bidType, bidder, videoStoredDataResult, auctionContext, eventsContext, lineItemId);
                modifiedBidderBids.add(bidderBid.toBuilder().bid(updatedBid).build());
            }

            final BidderSeatBid modifiedSeatBid = seatBid.with(modifiedBidderBids);
            result.add(bidderResponse.with(modifiedSeatBid));
        }

        return result;
    }

    private Bid updateBid(Bid bid,
                          BidType bidType,
                          String bidder,
                          VideoStoredDataResult videoStoredDataResult,
                          AuctionContext auctionContext,
                          EventsContext eventsContext,
                          String lineItemId) {

        final Account account = auctionContext.getAccount();
        final List<String> debugWarnings = auctionContext.getDebugWarnings();

        final String generatedBidId = bidIdGenerator.getType() != IdGeneratorType.none
                ? bidIdGenerator.generateId()
                : null;
        final String effectiveBidId = ObjectUtils.defaultIfNull(generatedBidId, bid.getId());

        return bid.toBuilder()
                .adm(updateBidAdm(bid,
                        bidType,
                        bidder,
                        account,
                        eventsContext,
                        effectiveBidId,
                        debugWarnings,
                        lineItemId))
                .ext(updateBidExt(
                        bid,
                        bidType,
                        bidder,
                        account,
                        videoStoredDataResult,
                        eventsContext,
                        generatedBidId,
                        effectiveBidId,
                        lineItemId))
                .build();
    }

    private String updateBidAdm(Bid bid,
                                BidType bidType,
                                String bidder,
                                Account account,
                                EventsContext eventsContext,
                                String effectiveBidId,
                                List<String> debugWarnings,
                                String lineItemId) {

        final String bidAdm = bid.getAdm();
        return BidType.video.equals(bidType)
                ? vastModifier.createBidVastXml(
                bidder,
                bidAdm,
                bid.getNurl(),
                effectiveBidId,
                account.getId(),
                eventsContext,
                debugWarnings,
                lineItemId)
                : bidAdm;
    }

    private ObjectNode updateBidExt(Bid bid,
                                    BidType bidType,
                                    String bidder,
                                    Account account,
                                    VideoStoredDataResult videoStoredDataResult,
                                    EventsContext eventsContext,
                                    String generatedBidId,
                                    String effectiveBidId,
                                    String lineItemId) {

        final ExtBidPrebid updatedExtBidPrebid = updateBidExtPrebid(
                bid,
                bidType,
                bidder,
                account,
                videoStoredDataResult,
                eventsContext,
                generatedBidId,
                effectiveBidId,
                lineItemId);
        final ObjectNode existingBidExt = bid.getExt();

        final ObjectNode updatedBidExt = mapper.mapper().createObjectNode();

        if (existingBidExt != null && !existingBidExt.isEmpty()) {
            updatedBidExt.setAll(existingBidExt);
        }

        updatedBidExt.set(PREBID_EXT, mapper.mapper().valueToTree(updatedExtBidPrebid));

        return updatedBidExt;
    }

    private ExtBidPrebid updateBidExtPrebid(Bid bid,
                                            BidType bidType,
                                            String bidder,
                                            Account account,
                                            VideoStoredDataResult videoStoredDataResult,
                                            EventsContext eventsContext,
                                            String generatedBidId,
                                            String effectiveBidId,
                                            String lineItemId) {

        final Video storedVideo = videoStoredDataResult.getImpIdToStoredVideo().get(bid.getImpid());
        final Events events = createEvents(bidder, account, effectiveBidId, eventsContext, lineItemId);
        final ExtBidPrebidVideo extBidPrebidVideo = getExtBidPrebidVideo(bid.getExt()).orElse(null);

        final ExtBidPrebid.ExtBidPrebidBuilder extBidPrebidBuilder = getExtPrebid(bid.getExt(), ExtBidPrebid.class)
                .map(ExtBidPrebid::toBuilder)
                .orElseGet(ExtBidPrebid::builder);

        return extBidPrebidBuilder
                .bidid(generatedBidId)
                .type(bidType)
                .storedRequestAttributes(storedVideo)
                .events(events)
                .video(extBidPrebidVideo)
                .build();
    }

    /**
     * Checks whether bidder responses are empty or contain no bids.
     */
    private static boolean isEmptyBidderResponses(List<BidderResponseInfo> bidderResponseInfos) {
        return bidderResponseInfos.isEmpty() || bidderResponseInfos.stream()
                .map(bidderResponseInfo -> bidderResponseInfo.getSeatBid().getBidsInfos())
                .allMatch(CollectionUtils::isEmpty);
    }

    private List<BidderResponseInfo> toBidderResponseInfos(CategoryMappingResult categoryMappingResult,
                                                           List<Imp> imps) {

        final List<BidderResponseInfo> result = new ArrayList<>();

        final List<BidderResponse> bidderResponses = categoryMappingResult.getBidderResponses();
        for (final BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();

            final List<BidInfo> bidInfos = new ArrayList<>();
            final BidderSeatBid seatBid = bidderResponse.getSeatBid();

            for (final BidderBid bidderBid : seatBid.getBids()) {
                final Bid bid = bidderBid.getBid();
                final BidType type = bidderBid.getType();
                final BidInfo bidInfo = toBidInfo(bid, type, imps, bidder, categoryMappingResult);
                bidInfos.add(bidInfo);
            }

            final BidderSeatBidInfo bidderSeatBidInfo = BidderSeatBidInfo.of(
                    bidInfos,
                    seatBid.getHttpCalls(),
                    seatBid.getErrors(),
                    seatBid.getWarnings(),
                    seatBid.getFledgeAuctionConfigs());

            result.add(BidderResponseInfo.of(bidder, bidderSeatBidInfo, bidderResponse.getResponseTime()));
        }

        return result;
    }

    private BidInfo toBidInfo(Bid bid,
                              BidType type,
                              List<Imp> imps,
                              String bidder,
                              CategoryMappingResult categoryMappingResult) {

        final Imp correspondingImp = correspondingImp(bid, imps);
        final ExtDealLine extDealLine = LineItemUtil.extDealLineFrom(bid, correspondingImp, mapper);
        final String lineItemId = extDealLine != null ? extDealLine.getLineItemId() : null;

        return BidInfo.builder()
                .bid(bid)
                .bidType(type)
                .bidder(bidder)
                .correspondingImp(correspondingImp)
                .lineItemId(lineItemId)
                .category(categoryMappingResult.getCategory(bid))
                .satisfiedPriority(categoryMappingResult.isBidSatisfiesPriority(bid))
                .build();
    }

    private static Imp correspondingImp(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        return correspondingImp(impId, imps)
                // Should never occur. See ResponseBidValidator
                .orElseThrow(
                        () -> new PreBidException("Bid with impId %s doesn't have matched imp".formatted(impId)));
    }

    private static Optional<Imp> correspondingImp(String impId, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> Objects.equals(impId, imp.getId()))
                .findFirst();
    }

    private Future<List<BidderResponse>> invokeProcessedBidderResponseHooks(List<BidderResponse> bidderResponses,
                                                                            AuctionContext auctionContext) {

        return CompositeFuture.join(bidderResponses.stream()
                        .map(bidderResponse -> hookStageExecutor
                                .executeProcessedBidderResponseStage(bidderResponse, auctionContext)
                                .map(stageResult -> rejectBidderResponseOrProceed(stageResult, bidderResponse)))
                        .collect(Collectors.toCollection(ArrayList::new)))
                .map(CompositeFuture::list);
    }

    private Future<List<BidderResponse>> invokeAllProcessedBidResponsesHook(List<BidderResponse> bidderResponses,
                                                                            AuctionContext auctionContext) {

        return hookStageExecutor.executeAllProcessedBidResponsesStage(bidderResponses, auctionContext)
                .map(HookStageExecutionResult::getPayload)
                .map(AllProcessedBidResponsesPayload::bidResponses);
    }

    private static BidderResponse rejectBidderResponseOrProceed(
            HookStageExecutionResult<BidderResponsePayload> stageResult,
            BidderResponse bidderResponse) {

        final List<BidderBid> bids =
                stageResult.isShouldReject() ? Collections.emptyList() : stageResult.getPayload().bids();

        return bidderResponse
                .with(bidderResponse.getSeatBid()
                        .with(bids));
    }

    private Future<CategoryMappingResult> createCategoryMapping(AuctionContext auctionContext,
                                                                List<BidderResponse> bidderResponses) {

        return categoryMappingService.createCategoryMapping(
                        bidderResponses,
                        auctionContext.getBidRequest(),
                        auctionContext.getTimeout())

                .map(categoryMappingResult -> addCategoryMappingErrors(categoryMappingResult, auctionContext));
    }

    private static CategoryMappingResult addCategoryMappingErrors(CategoryMappingResult categoryMappingResult,
                                                                  AuctionContext auctionContext) {

        auctionContext.getPrebidErrors()
                .addAll(CollectionUtils.emptyIfNull(categoryMappingResult.getErrors()));

        return categoryMappingResult;
    }

    private Future<BidResponse> cacheBidsAndCreateResponse(List<BidderResponseInfo> bidderResponses,
                                                           AuctionContext auctionContext,
                                                           BidRequestCacheInfo cacheInfo,
                                                           Map<String, MultiBidConfig> bidderToMultiBids,
                                                           VideoStoredDataResult videoStoredDataResult,
                                                           EventsContext eventsContext) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        if (isEmptyBidderResponses(bidderResponses)) {

            final ExtBidResponse extBidResponse = toExtBidResponse(
                    bidderResponses,
                    auctionContext,
                    CacheServiceResult.empty(),
                    VideoStoredDataResult.empty(),
                    eventsContext.getAuctionTimestamp(),
                    null);
            final CachedDebugLog cachedDebugLog = auctionContext.getCachedDebugLog();
            if (isCachedDebugEnabled(cachedDebugLog)) {
                cachedDebugLog.setExtBidResponse(extBidResponse);
            }

            return Future.succeededFuture(BidResponse.builder()
                    .id(bidRequest.getId())
                    .cur(bidRequest.getCur().get(0))
                    .nbr(0) // signal "Unknown Error"
                    .seatbid(Collections.emptyList())
                    .ext(extBidResponse)
                    .build());
        }

        final ExtRequestTargeting targeting = targeting(bidRequest);
        final TxnLog txnLog = auctionContext.getTxnLog();

        final List<BidderResponseInfo> bidderResponseInfos = toBidderResponseWithTargetingBidInfos(
                bidderResponses, bidderToMultiBids, preferDeals(targeting), txnLog);

        final Set<BidInfo> bidInfos = bidderResponseInfos.stream()
                .map(BidderResponseInfo::getSeatBid)
                .map(BidderSeatBidInfo::getBidsInfos)
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        final Set<BidInfo> winningBidInfos = targeting == null
                ? null
                : bidInfos.stream()
                .filter(bidInfo -> bidInfo.getTargetingInfo().isWinningBid())
                .collect(Collectors.toSet());

        updateSentToClientTxnLog(txnLog, bidInfos);

        final Set<BidInfo> bidsToCache = cacheInfo.isShouldCacheWinningBidsOnly() ? winningBidInfos : bidInfos;

        return cacheBids(bidsToCache, auctionContext, cacheInfo, eventsContext)
                .map(cacheResult -> toBidResponse(
                        bidderResponseInfos,
                        auctionContext,
                        targeting,
                        cacheInfo,
                        cacheResult,
                        videoStoredDataResult,
                        eventsContext));
    }

    private static ExtRequestTargeting targeting(BidRequest bidRequest) {
        final ExtRequest ext = bidRequest.getExt();
        final ExtRequestPrebid prebid = ext != null ? ext.getPrebid() : null;
        return prebid != null ? prebid.getTargeting() : null;
    }

    private static boolean preferDeals(ExtRequestTargeting targeting) {
        return BooleanUtils.toBooleanDefaultIfNull(targeting != null ? targeting.getPreferdeals() : null, false);
    }

    private List<BidderResponseInfo> toBidderResponseWithTargetingBidInfos(
            List<BidderResponseInfo> bidderResponses,
            Map<String, MultiBidConfig> bidderToMultiBids,
            boolean preferDeals,
            TxnLog txnLog) {

        final Map<BidderResponseInfo, List<BidInfo>> bidderResponseToReducedBidInfos = bidderResponses.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        bidderResponse -> toSortedMultiBidInfo(bidderResponse, bidderToMultiBids, preferDeals)));

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

        for (final Map<String, List<BidInfo>> bidderToBidInfos : impIdToBidderToBidInfos.values()) {

            bidderToBidInfos.values().forEach(winningBidsByBidder::addAll);

            bidderToBidInfos.values().stream()
                    .flatMap(Collection::stream)
                    .max(winningBidComparatorFactory.create(preferDeals))
                    .ifPresent(winningBids::add);
        }

        final Map<String, Set<String>> impIdToLineItemIds = impIdToBidderToBidInfos.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        impIdToBidderToBidInfoEntry -> toLineItemIds(impIdToBidderToBidInfoEntry.getValue().values())));

        updateTopMatchAndLostAuctionLineItemsMetric(winningBids, txnLog, impIdToLineItemIds);

        return bidderResponseToReducedBidInfos.entrySet().stream()
                .map(responseToBidInfos -> injectBidInfoWithTargeting(
                        responseToBidInfos.getKey(),
                        responseToBidInfos.getValue(),
                        bidderToMultiBids,
                        winningBids,
                        winningBidsByBidder))
                .toList();
    }

    private List<BidInfo> toSortedMultiBidInfo(BidderResponseInfo bidderResponse,
                                               Map<String, MultiBidConfig> bidderToMultiBids,
                                               boolean preferDeals) {

        final List<BidInfo> bidInfos = bidderResponse.getSeatBid().getBidsInfos();
        final Map<String, List<BidInfo>> impIdToBidInfos = bidInfos.stream()
                .collect(Collectors.groupingBy(bidInfo -> bidInfo.getCorrespondingImp().getId()));

        final MultiBidConfig multiBid = bidderToMultiBids.get(bidderResponse.getBidder());
        final Integer bidLimit = multiBid != null ? multiBid.getMaxBids() : DEFAULT_BID_LIMIT_MIN;

        return impIdToBidInfos.values().stream()
                .map(infos -> sortReducedBidInfo(infos, bidLimit, preferDeals))
                .flatMap(Collection::stream)
                .toList();
    }

    private List<BidInfo> sortReducedBidInfo(List<BidInfo> bidInfos, int limit, boolean preferDeals) {
        return bidInfos.stream()
                .sorted(winningBidComparatorFactory.create(preferDeals).reversed())
                .limit(limit)
                .toList();
    }

    private static Set<String> toLineItemIds(Collection<List<BidInfo>> bidInfos) {
        return bidInfos.stream()
                .flatMap(Collection::stream)
                .map(BidInfo::getLineItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Updates sent to client as top match and auction lost to line item metric.
     */
    private static void updateTopMatchAndLostAuctionLineItemsMetric(Set<BidInfo> winningBidInfos,
                                                                    TxnLog txnLog,
                                                                    Map<String, Set<String>> impToLineItemIds) {
        for (BidInfo winningBidInfo : winningBidInfos) {
            final String winningLineItemId = winningBidInfo.getLineItemId();
            if (winningLineItemId != null) {
                txnLog.lineItemSentToClientAsTopMatch().add(winningLineItemId);

                final String impIdOfWinningBid = winningBidInfo.getBid().getImpid();
                impToLineItemIds.get(impIdOfWinningBid).stream()
                        .filter(lineItemId -> !Objects.equals(lineItemId, winningLineItemId))
                        .forEach(lineItemId -> txnLog.lostAuctionToLineItems().get(lineItemId).add(winningLineItemId));
            }
        }
    }

    private static BidderResponseInfo injectBidInfoWithTargeting(BidderResponseInfo bidderResponseInfo,
                                                                 List<BidInfo> bidderBidInfos,
                                                                 Map<String, MultiBidConfig> bidderToMultiBids,
                                                                 Set<BidInfo> winningBids,
                                                                 Set<BidInfo> winningBidsByBidder) {

        final String bidder = bidderResponseInfo.getBidder();
        final List<BidInfo> bidInfosWithTargeting = toBidInfoWithTargeting(bidderBidInfos, bidder, bidderToMultiBids,
                winningBids, winningBidsByBidder);

        final BidderSeatBidInfo seatBid = bidderResponseInfo.getSeatBid();
        final BidderSeatBidInfo modifiedSeatBid = seatBid.with(bidInfosWithTargeting);
        return bidderResponseInfo.with(modifiedSeatBid);
    }

    private static List<BidInfo> toBidInfoWithTargeting(List<BidInfo> bidderBidInfos,
                                                        String bidder,
                                                        Map<String, MultiBidConfig> bidderToMultiBids,
                                                        Set<BidInfo> winningBids,
                                                        Set<BidInfo> winningBidsByBidder) {

        final Map<String, List<BidInfo>> impIdToBidInfos = bidderBidInfos.stream()
                .collect(Collectors.groupingBy(bidInfo -> bidInfo.getCorrespondingImp().getId()));

        return impIdToBidInfos.values().stream()
                .map(bidInfos -> injectTargeting(bidInfos, bidder, bidderToMultiBids, winningBids, winningBidsByBidder))
                .flatMap(Collection::stream)
                .toList();
    }

    private static List<BidInfo> injectTargeting(List<BidInfo> bidderImpIdBidInfos,
                                                 String bidder,
                                                 Map<String, MultiBidConfig> bidderToMultiBids,
                                                 Set<BidInfo> winningBids,
                                                 Set<BidInfo> winningBidsByBidder) {

        final List<BidInfo> result = new ArrayList<>();

        final MultiBidConfig multiBid = bidderToMultiBids.get(bidder);
        final String bidderCodePrefix = multiBid != null ? multiBid.getTargetBidderCodePrefix() : null;

        final int multiBidSize = bidderImpIdBidInfos.size();
        for (int i = 0; i < multiBidSize; i++) {
            // first bid have the highest value and can't be extra bid
            final boolean isFirstBid = i == 0;
            final String targetingBidderCode = isFirstBid
                    ? bidder
                    : bidderCodePrefix == null ? null : bidderCodePrefix + (i + 1);

            final BidInfo bidInfo = bidderImpIdBidInfos.get(i);
            final TargetingInfo targetingInfo = TargetingInfo.builder()
                    .isTargetingEnabled(targetingBidderCode != null)
                    .isBidderWinningBid(winningBidsByBidder.contains(bidInfo))
                    .isWinningBid(winningBids.contains(bidInfo))
                    .isAddTargetBidderCode(targetingBidderCode != null && multiBidSize > 1)
                    .bidderCode(targetingBidderCode)
                    .build();

            final BidInfo modifiedBidInfo = bidInfo.toBuilder().targetingInfo(targetingInfo).build();
            result.add(modifiedBidInfo);
        }

        return result;
    }

    /**
     * Increments sent to client metrics for each bid with deal.
     */
    private static void updateSentToClientTxnLog(TxnLog txnLog, Set<BidInfo> bidInfos) {
        bidInfos.stream()
                .map(BidInfo::getLineItemId)
                .filter(Objects::nonNull)
                .forEach(lineItemId -> txnLog.lineItemsSentToClient().add(lineItemId));
    }

    /**
     * Returns {@link ExtBidResponse} object, populated with response time, errors and debug info (if requested)
     * from all bidders.
     */
    private ExtBidResponse toExtBidResponse(List<BidderResponseInfo> bidderResponseInfos,
                                            AuctionContext auctionContext,
                                            CacheServiceResult cacheResult,
                                            VideoStoredDataResult videoStoredDataResult,
                                            long auctionTimestamp,
                                            Map<String, List<ExtBidderError>> bidErrors) {

        final DebugContext debugContext = auctionContext.getDebugContext();
        final boolean debugEnabled = debugContext.isDebugEnabled();

        final ExtResponseDebug extResponseDebug = toExtResponseDebug(
                bidderResponseInfos, auctionContext, cacheResult, debugEnabled);
        final Map<String, List<ExtBidderError>> errors = toExtBidderErrors(
                bidderResponseInfos, auctionContext, cacheResult, videoStoredDataResult, bidErrors);
        final Map<String, List<ExtBidderError>> warnings = toExtBidderWarnings(bidderResponseInfos, auctionContext);

        final Map<String, Integer> responseTimeMillis = toResponseTimes(bidderResponseInfos, cacheResult);

        final ExtBidResponseFledge extBidResponseFledge = toExtBidResponseFledge(bidderResponseInfos, auctionContext);
        final ExtBidResponsePrebid prebid = toExtBidResponsePrebid(
                auctionTimestamp, auctionContext.getBidRequest(), extBidResponseFledge);

        return ExtBidResponse.builder()
                .debug(extResponseDebug)
                .errors(errors)
                .warnings(warnings)
                .responsetimemillis(responseTimeMillis)
                .tmaxrequest(auctionContext.getBidRequest().getTmax())
                .prebid(prebid)
                .build();
    }

    private ExtBidResponsePrebid toExtBidResponsePrebid(long auctionTimestamp,
                                                        BidRequest bidRequest,
                                                        ExtBidResponseFledge extBidResponseFledge) {

        final JsonNode passThrough = Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getPassthrough)
                .orElse(null);

        return ExtBidResponsePrebid.builder()
                .auctiontimestamp(auctionTimestamp)
                .passthrough(passThrough)
                .fledge(extBidResponseFledge)
                .build();
    }

    private ExtBidResponseFledge toExtBidResponseFledge(List<BidderResponseInfo> bidderResponseInfos,
                                                        AuctionContext auctionContext) {

        final List<Imp> imps = auctionContext.getBidRequest().getImp();
        final List<FledgeAuctionConfig> fledgeConfigs = bidderResponseInfos.stream()
                .flatMap(bidderResponseInfo -> fledgeConfigsForBidder(bidderResponseInfo, imps))
                .toList();
        return !fledgeConfigs.isEmpty() ? ExtBidResponseFledge.of(fledgeConfigs) : null;
    }

    private Stream<FledgeAuctionConfig> fledgeConfigsForBidder(BidderResponseInfo bidderResponseInfo, List<Imp> imps) {
        return Optional.ofNullable(bidderResponseInfo.getSeatBid().getFledgeAuctionConfigs())
                .stream()
                .flatMap(Collection::stream)
                .filter(fledgeConfig -> validateFledgeConfig(fledgeConfig, imps))
                .map(fledgeConfig -> fledgeConfigWithBidder(fledgeConfig, bidderResponseInfo.getBidder()));
    }

    private boolean validateFledgeConfig(FledgeAuctionConfig fledgeAuctionConfig, List<Imp> imps) {
        final ExtImpAuctionEnvironment fledgeEnabled = correspondingImp(fledgeAuctionConfig.getImpId(), imps)
                .map(Imp::getExt)
                .map(ext -> convertValue(ext, "ae", ExtImpAuctionEnvironment.class))
                .orElse(ExtImpAuctionEnvironment.SERVER_SIDE_AUCTION);

        return fledgeEnabled == ExtImpAuctionEnvironment.ON_DEVICE_IG_AUCTION_FLEDGE;
    }

    private static FledgeAuctionConfig fledgeConfigWithBidder(FledgeAuctionConfig fledgeConfig, String bidderName) {
        return fledgeConfig.toBuilder()
                .bidder(bidderName)
                .adapter(bidderName)
                .build();
    }

    private static ExtResponseDebug toExtResponseDebug(List<BidderResponseInfo> bidderResponseInfos,
                                                       AuctionContext auctionContext,
                                                       CacheServiceResult cacheResult,
                                                       boolean debugEnabled) {

        final DeepDebugLog deepDebugLog = auctionContext.getDeepDebugLog();

        final Map<String, List<ExtHttpCall>> httpCalls = debugEnabled
                ? toExtHttpCalls(bidderResponseInfos, cacheResult, auctionContext.getDebugHttpCalls())
                : null;

        final BidRequest bidRequest = debugEnabled ? auctionContext.getBidRequest() : null;

        final ExtDebugPgmetrics extDebugPgmetrics = debugEnabled ? toExtDebugPgmetrics(
                auctionContext.getTxnLog()) : null;
        final ExtDebugTrace extDebugTrace = deepDebugLog.isDeepDebugEnabled() ? toExtDebugTrace(deepDebugLog) : null;

        return ObjectUtils.anyNotNull(httpCalls, bidRequest, extDebugPgmetrics, extDebugTrace)
                ? ExtResponseDebug.of(httpCalls, bidRequest, extDebugPgmetrics, extDebugTrace)
                : null;
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
                .toList();

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

    private static Map<String, List<ExtHttpCall>> toExtHttpCalls(List<BidderResponseInfo> bidderResponses,
                                                                 CacheServiceResult cacheResult,
                                                                 Map<String, List<DebugHttpCall>> contextHttpCalls) {

        final Map<String, List<ExtHttpCall>> bidderHttpCalls = bidderResponses.stream()
                .filter(bidderResponse -> CollectionUtils.isNotEmpty(bidderResponse.getSeatBid().getHttpCalls()))
                .collect(Collectors.toMap(
                        BidderResponseInfo::getBidder,
                        bidderResponse -> bidderResponse.getSeatBid().getHttpCalls()));

        final DebugHttpCall httpCall = cacheResult.getHttpCall();
        final ExtHttpCall cacheExtHttpCall = httpCall != null ? toExtHttpCall(httpCall) : null;
        final Map<String, List<ExtHttpCall>> cacheHttpCalls = cacheExtHttpCall != null
                ? Collections.singletonMap(CACHE, Collections.singletonList(cacheExtHttpCall))
                : Collections.emptyMap();

        final Map<String, List<ExtHttpCall>> contextExtHttpCalls = contextHttpCalls.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, serviceToHttpCall -> serviceToHttpCall.getValue().stream()
                        .map(BidResponseCreator::toExtHttpCall)
                        .toList()));

        final Map<String, List<ExtHttpCall>> httpCalls = new HashMap<>();
        httpCalls.putAll(bidderHttpCalls);
        httpCalls.putAll(cacheHttpCalls);
        httpCalls.putAll(contextExtHttpCalls);
        return httpCalls.isEmpty() ? null : httpCalls;
    }

    private static ExtHttpCall toExtHttpCall(DebugHttpCall debugHttpCall) {
        return ExtHttpCall.builder()
                .uri(debugHttpCall.getRequestUri())
                .requestbody(debugHttpCall.getRequestBody())
                .status(debugHttpCall.getResponseStatus())
                .responsebody(debugHttpCall.getResponseBody())
                .requestheaders(debugHttpCall.getRequestHeaders())
                .build();
    }

    private static ExtDebugPgmetrics toExtDebugPgmetrics(TxnLog txnLog) {
        final ExtDebugPgmetrics extDebugPgmetrics = ExtDebugPgmetrics.builder()
                .matchedDomainTargeting(nullIfEmpty(txnLog.lineItemsMatchedDomainTargeting()))
                .matchedWholeTargeting(nullIfEmpty(txnLog.lineItemsMatchedWholeTargeting()))
                .matchedTargetingFcapped(nullIfEmpty(txnLog.lineItemsMatchedTargetingFcapped()))
                .matchedTargetingFcapLookupFailed(nullIfEmpty(txnLog.lineItemsMatchedTargetingFcapLookupFailed()))
                .readyToServe(nullIfEmpty(txnLog.lineItemsReadyToServe()))
                .pacingDeferred(nullIfEmpty(txnLog.lineItemsPacingDeferred()))
                .sentToBidder(nullIfEmpty(txnLog.lineItemsSentToBidder()))
                .sentToBidderAsTopMatch(nullIfEmpty(txnLog.lineItemsSentToBidderAsTopMatch()))
                .receivedFromBidder(nullIfEmpty(txnLog.lineItemsReceivedFromBidder()))
                .responseInvalidated(nullIfEmpty(txnLog.lineItemsResponseInvalidated()))
                .sentToClient(nullIfEmpty(txnLog.lineItemsSentToClient()))
                .sentToClientAsTopMatch(nullIfEmpty(txnLog.lineItemSentToClientAsTopMatch()))
                .build();
        return extDebugPgmetrics.equals(ExtDebugPgmetrics.EMPTY) ? null : extDebugPgmetrics;
    }

    private static ExtDebugTrace toExtDebugTrace(DeepDebugLog deepDebugLog) {
        final List<ExtTraceDeal> entries = deepDebugLog.entries();

        final List<ExtTraceDeal> dealsTrace = entries.stream()
                .filter(extTraceDeal -> StringUtils.isEmpty(extTraceDeal.getLineItemId()))
                .toList();
        final Map<String, List<ExtTraceDeal>> lineItemsTrace = entries.stream()
                .filter(extTraceDeal -> StringUtils.isNotEmpty(extTraceDeal.getLineItemId()))
                .collect(Collectors.groupingBy(ExtTraceDeal::getLineItemId, Collectors.toList()));

        return CollectionUtils.isNotEmpty(entries)
                ? ExtDebugTrace.of(CollectionUtils.isEmpty(dealsTrace) ? null : dealsTrace,
                MapUtils.isEmpty(lineItemsTrace) ? null : lineItemsTrace)
                : null;
    }

    private Map<String, List<ExtBidderError>> toExtBidderErrors(List<BidderResponseInfo> bidderResponses,
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
    private static Map<String, List<ExtBidderError>> extractBidderErrors(
            Collection<BidderResponseInfo> bidderResponses) {

        return bidderResponses.stream()
                .filter(bidderResponse -> CollectionUtils.isNotEmpty(bidderResponse.getSeatBid().getErrors()))
                .collect(Collectors.toMap(BidderResponseInfo::getBidder,
                        bidderResponse -> errorsDetails(bidderResponse.getSeatBid().getErrors())));
    }

    /**
     * Returns a map with bidder name as a key and list of {@link ExtBidderError}s as a value.
     */
    private static Map<String, List<ExtBidderError>> extractBidderWarnings(
            Collection<BidderResponseInfo> bidderResponses) {

        return bidderResponses.stream()
                .filter(bidderResponse -> CollectionUtils.isNotEmpty(bidderResponse.getSeatBid().getWarnings()))
                .collect(Collectors.toMap(BidderResponseInfo::getBidder,
                        bidderResponse -> errorsDetails(bidderResponse.getSeatBid().getWarnings())));
    }

    /**
     * Maps a list of {@link BidderError} to a list of {@link ExtBidderError}s.
     */
    private static List<ExtBidderError> errorsDetails(List<BidderError> errors) {
        return errors.stream()
                .map(bidderError -> ExtBidderError.of(
                        bidderError.getType().getCode(),
                        bidderError.getMessage(),
                        nullIfEmpty(bidderError.getImpIds())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns a map with deprecated bidder name as a key and list of {@link ExtBidderError}s as a value.
     */
    private Map<String, List<ExtBidderError>> extractDeprecatedBiddersErrors(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .filter(imp -> imp.getExt() != null)
                .flatMap(imp -> StreamUtil.asStream(imp.getExt().fieldNames()))
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

        final List<ExtBidderError> collectedErrors =
                Stream.concat(contextErrors.stream(), storedErrors.stream()).toList();
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
                    .toList();
        }
        return Collections.emptyList();
    }

    /**
     * Returns a list of {@link ExtBidderError}s of auction context prebid errors.
     */
    private static List<ExtBidderError> extractContextErrors(AuctionContext auctionContext) {
        return auctionContext.getPrebidErrors().stream()
                .map(message -> ExtBidderError.of(BidderError.Type.generic.getCode(), message))
                .toList();
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

    private static Map<String, List<ExtBidderError>> toExtBidderWarnings(List<BidderResponseInfo> bidderResponses,
                                                                         AuctionContext auctionContext) {
        final Map<String, List<ExtBidderError>> warnings = new HashMap<>();

        warnings.putAll(extractContextWarnings(auctionContext));
        warnings.putAll(extractBidderWarnings(bidderResponses));

        return warnings.isEmpty() ? null : warnings;
    }

    private static Map<String, List<ExtBidderError>> extractContextWarnings(AuctionContext auctionContext) {
        final List<ExtBidderError> contextWarnings = auctionContext.getDebugWarnings().stream()
                .map(message -> ExtBidderError.of(BidderError.Type.generic.getCode(), message))
                .toList();

        return contextWarnings.isEmpty()
                ? Collections.emptyMap()
                : Collections.singletonMap(PREBID_EXT, contextWarnings);
    }

    /**
     * Returns a map with response time by bidders and cache.
     */
    private static Map<String, Integer> toResponseTimes(Collection<BidderResponseInfo> bidderResponses,
                                                        CacheServiceResult cacheResult) {

        final Map<String, Integer> responseTimeMillis = bidderResponses.stream()
                .collect(Collectors.toMap(BidderResponseInfo::getBidder, BidderResponseInfo::getResponseTime));

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
    private BidResponse toBidResponse(List<BidderResponseInfo> bidderResponseInfos,
                                      AuctionContext auctionContext,
                                      ExtRequestTargeting targeting,
                                      BidRequestCacheInfo requestCacheInfo,
                                      CacheServiceResult cacheResult,
                                      VideoStoredDataResult videoStoredDataResult,
                                      EventsContext eventsContext) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final Account account = auctionContext.getAccount();

        final Map<String, List<ExtBidderError>> bidErrors = new HashMap<>();
        final List<SeatBid> seatBids = bidderResponseInfos.stream()
                .map(BidderResponseInfo::getSeatBid)
                .map(BidderSeatBidInfo::getBidsInfos)
                .filter(CollectionUtils::isNotEmpty)
                .map(bidInfos -> toSeatBid(
                        bidInfos,
                        targeting,
                        bidRequest,
                        requestCacheInfo,
                        cacheResult.getCacheBids(),
                        account,
                        bidErrors))
                .toList();

        final Long auctionTimestamp = eventsContext.getAuctionTimestamp();
        final ExtBidResponse extBidResponse = toExtBidResponse(
                bidderResponseInfos,
                auctionContext,
                cacheResult,
                videoStoredDataResult,
                auctionTimestamp,
                bidErrors);

        final CachedDebugLog cachedDebugLog = auctionContext.getCachedDebugLog();
        if (isCachedDebugEnabled(cachedDebugLog)) {
            cachedDebugLog.setExtBidResponse(extBidResponse);
        }

        return BidResponse.builder()
                .id(bidRequest.getId())
                .cur(bidRequest.getCur().get(0))
                .seatbid(seatBids)
                .ext(extBidResponse)
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
                throw new InvalidRequestException(
                        "Incorrect Imp extension format for Imp with id " + imp.getId() + ": " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private SeatBid toSeatBid(List<BidInfo> bidInfos,
                              ExtRequestTargeting targeting,
                              BidRequest bidRequest,
                              BidRequestCacheInfo requestCacheInfo,
                              Map<Bid, CacheInfo> bidToCacheInfo,
                              Account account,
                              Map<String, List<ExtBidderError>> bidErrors) {

        final String bidder = bidInfos.stream()
                .map(BidInfo::getBidder)
                .findFirst()
                // Should never occur
                .orElseThrow(() -> new IllegalArgumentException("Bidder was not defined for bidInfo"));

        final List<Bid> bids = bidInfos.stream()
                .map(bidInfo -> injectAdmWithCacheInfo(
                        bidInfo,
                        requestCacheInfo,
                        bidToCacheInfo,
                        bidErrors))
                .filter(Objects::nonNull)
                .map(bidInfo -> toBid(
                        bidInfo,
                        targeting,
                        bidRequest,
                        account))
                .filter(Objects::nonNull)
                .toList();

        return SeatBid.builder()
                .seat(bidder)
                .bid(bids)
                .group(0) // prebid cannot support roadblocking
                .build();
    }

    private BidInfo injectAdmWithCacheInfo(BidInfo bidInfo,
                                           BidRequestCacheInfo requestCacheInfo,
                                           Map<Bid, CacheInfo> bidsWithCacheIds,
                                           Map<String, List<ExtBidderError>> bidErrors) {

        final Bid bid = bidInfo.getBid();
        final BidType bidType = bidInfo.getBidType();
        final String bidder = bidInfo.getBidder();
        final Imp correspondingImp = bidInfo.getCorrespondingImp();

        final CacheInfo cacheInfo = bidsWithCacheIds.get(bid);
        final String cacheId = cacheInfo != null ? cacheInfo.getCacheId() : null;
        final String videoCacheId = cacheInfo != null ? cacheInfo.getVideoCacheId() : null;

        String modifiedBidAdm = bid.getAdm();
        if ((videoCacheId != null && !requestCacheInfo.isReturnCreativeVideoBids())
                || (cacheId != null && !requestCacheInfo.isReturnCreativeBids())) {
            modifiedBidAdm = null;
        }

        if (bidType.equals(BidType.xNative) && modifiedBidAdm != null) {
            try {
                modifiedBidAdm = createNativeMarkup(modifiedBidAdm, correspondingImp);
            } catch (PreBidException e) {
                bidErrors.computeIfAbsent(bidder, ignored -> new ArrayList<>())
                        .add(ExtBidderError.of(BidderError.Type.bad_server_response.getCode(), e.getMessage()));
                return null;
            }
        }

        final Bid modifiedBid = bid.toBuilder().adm(modifiedBidAdm).build();
        return bidInfo.toBuilder()
                .bid(modifiedBid)
                .cacheInfo(cacheInfo)
                .build();
    }

    /**
     * Returns an OpenRTB {@link Bid} with "prebid" and "bidder" extension fields populated.
     */
    private Bid toBid(BidInfo bidInfo, ExtRequestTargeting targeting, BidRequest bidRequest, Account account) {
        final TargetingInfo targetingInfo = bidInfo.getTargetingInfo();
        final BidType bidType = bidInfo.getBidType();
        final Bid bid = bidInfo.getBid();

        final CacheInfo cacheInfo = bidInfo.getCacheInfo();
        final String cacheId = cacheInfo != null ? cacheInfo.getCacheId() : null;
        final String videoCacheId = cacheInfo != null ? cacheInfo.getVideoCacheId() : null;

        final boolean isApp = bidRequest.getApp() != null;

        final Map<String, String> targetingKeywords;
        final String bidderCode = targetingInfo.getBidderCode();
        if (targeting != null && targetingInfo.isTargetingEnabled() && targetingInfo.isBidderWinningBid()) {
            final TargetingKeywordsCreator keywordsCreator = resolveKeywordsCreator(
                    bidType, targeting, isApp, bidRequest, account);

            final boolean isWinningBid = targetingInfo.isWinningBid();
            final String categoryDuration = bidInfo.getCategory();
            targetingKeywords = keywordsCreator != null
                    ? keywordsCreator.makeFor(
                    bid, bidderCode, isWinningBid, cacheId, bidType.getName(), videoCacheId, categoryDuration)
                    : null;
        } else {
            targetingKeywords = null;
        }

        final CacheAsset bids = cacheId != null ? toCacheAsset(cacheId) : null;
        final CacheAsset vastXml = videoCacheId != null ? toCacheAsset(videoCacheId) : null;
        final ExtResponseCache cache = bids != null || vastXml != null ? ExtResponseCache.of(bids, vastXml) : null;

        final ObjectNode originalBidExt = bid.getExt();
        final Boolean dealsTierSatisfied = bidInfo.getSatisfiedPriority();

        final ExtBidPrebid updatedExtBidPrebid =
                getExtPrebid(originalBidExt, ExtBidPrebid.class)
                        .map(ExtBidPrebid::toBuilder)
                        .orElseGet(ExtBidPrebid::builder)
                        .targeting(MapUtils.isNotEmpty(targetingKeywords) ? targetingKeywords : null)
                        .targetBidderCode(targetingInfo.isAddTargetBidderCode() ? bidderCode : null)
                        .dealTierSatisfied(dealsTierSatisfied)
                        .cache(cache)
                        .passThrough(extractPassThrough(bidInfo.getCorrespondingImp()))
                        .build();

        final ObjectNode updatedBidExt =
                originalBidExt != null ? originalBidExt.deepCopy() : mapper.mapper().createObjectNode();
        updatedBidExt.set(PREBID_EXT, mapper.mapper().valueToTree(updatedExtBidPrebid));

        final Integer ttl = cacheInfo != null ? ObjectUtils.max(cacheInfo.getTtl(), cacheInfo.getVideoTtl()) : null;

        return bid.toBuilder()
                .ext(updatedBidExt)
                .exp(ttl)
                .build();
    }

    private JsonNode extractPassThrough(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .flatMap(ext -> getExtPrebid(ext, ExtImpPrebid.class))
                .map(ExtImpPrebid::getPassthrough)
                .orElse(null);
    }

    private String createNativeMarkup(String bidAdm, Imp correspondingImp) {
        final Response nativeMarkup;
        try {
            nativeMarkup = mapper.decodeValue(bidAdm, Response.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }

        final List<Asset> responseAssets = nativeMarkup.getAssets();
        if (CollectionUtils.isNotEmpty(responseAssets)) {
            final Native nativeImp = correspondingImp != null ? correspondingImp.getXNative() : null;
            if (nativeImp == null) {
                throw new PreBidException("Could not find native imp");
            }

            final Request nativeRequest;
            try {
                nativeRequest = mapper.mapper().readValue(nativeImp.getRequest(), Request.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(e.getMessage());
            }

            responseAssets.forEach(asset -> setAssetTypes(asset, nativeRequest.getAssets()));
            return mapper.encodeToString(nativeMarkup);
        }

        return bidAdm;
    }

    private static void setAssetTypes(Asset responseAsset, List<com.iab.openrtb.request.Asset> requestAssets) {
        if (responseAsset.getImg() != null) {
            final ImageObject img = getAssetById(responseAsset.getId(), requestAssets).getImg();
            final Integer type = img != null ? img.getType() : null;
            if (type != null) {
                responseAsset.getImg().setType(type);
            } else {
                final Integer assetId = responseAsset.getId();
                throw new PreBidException(
                        "Response has an Image asset with ID:'%s' present that doesn't exist in the request"
                                .formatted(assetId != null ? assetId : StringUtils.EMPTY));
            }
        }
        if (responseAsset.getData() != null) {
            final DataObject data = getAssetById(responseAsset.getId(), requestAssets).getData();
            final Integer type = data != null ? data.getType() : null;
            if (type != null) {
                responseAsset.getData().setType(type);
            } else {
                throw new PreBidException(
                        "Response has a Data asset with ID:%s present that doesn't exist in the request"
                                .formatted(responseAsset.getId()));
            }
        }
    }

    private static com.iab.openrtb.request.Asset getAssetById(Integer assetId,
                                                              List<com.iab.openrtb.request.Asset> requestAssets) {

        return requestAssets.stream()
                .filter(asset -> Objects.equals(assetId, asset.getId()))
                .findFirst()
                .orElse(com.iab.openrtb.request.Asset.EMPTY);
    }

    private EventsContext createEventsContext(AuctionContext auctionContext) {
        return EventsContext.builder()
                .auctionId(auctionContext.getBidRequest().getId())
                .enabledForAccount(eventsEnabledForAccount(auctionContext))
                .enabledForRequest(eventsEnabledForRequest(auctionContext))
                .auctionTimestamp(auctionTimestamp(auctionContext))
                .integration(integrationFrom(auctionContext))
                .build();
    }

    private static boolean eventsEnabledForAccount(AuctionContext auctionContext) {
        final AccountAuctionConfig accountAuctionConfig = auctionContext.getAccount().getAuction();
        final AccountEventsConfig accountEventsConfig =
                accountAuctionConfig != null ? accountAuctionConfig.getEvents() : null;
        final Boolean accountEventsEnabled = accountEventsConfig != null ? accountEventsConfig.getEnabled() : null;

        return BooleanUtils.isTrue(accountEventsEnabled);
    }

    private static boolean eventsEnabledForRequest(AuctionContext auctionContext) {
        return eventsEnabledForChannel(auctionContext) || eventsAllowedByRequest(auctionContext);
    }

    private static boolean eventsEnabledForChannel(AuctionContext auctionContext) {
        final Map<String, Boolean> channelConfig = Optional.ofNullable(auctionContext.getAccount().getAnalytics())
                .map(AccountAnalyticsConfig::getAuctionEvents)
                .map(AccountAuctionEventConfig::getEvents)
                .orElseGet(AccountAnalyticsConfig::fallbackAuctionEvents);

        final String channelFromRequest = channelFromRequest(auctionContext.getBidRequest());

        return channelConfig.entrySet().stream()
                .filter(entry -> StringUtils.equalsIgnoreCase(channelFromRequest, entry.getKey()))
                .findFirst()
                .map(entry -> BooleanUtils.isTrue(entry.getValue()))
                .orElse(Boolean.FALSE);
    }

    private static String channelFromRequest(BidRequest bidRequest) {
        final ExtRequest ext = bidRequest.getExt();
        final ExtRequestPrebid prebid = ext != null ? ext.getPrebid() : null;
        final ExtRequestPrebidChannel channel = prebid != null ? prebid.getChannel() : null;

        return channel != null ? recogniseChannelName(channel.getName()) : null;
    }

    // TODO: remove alias resolving after transition period
    private static String recogniseChannelName(String channelName) {
        if (StringUtils.equalsIgnoreCase("pbjs", channelName)) {
            return Ortb2ImplicitParametersResolver.WEB_CHANNEL;
        }

        return channelName;
    }

    private static boolean eventsAllowedByRequest(AuctionContext auctionContext) {
        final ExtRequest ext = auctionContext.getBidRequest().getExt();
        final ExtRequestPrebid prebid = ext != null ? ext.getPrebid() : null;

        return prebid != null && prebid.getEvents() != null;
    }

    /**
     * Extracts auction timestamp from {@link ExtRequest} or get it from {@link Clock} if it is null.
     */
    private long auctionTimestamp(AuctionContext auctionContext) {
        final ExtRequest ext = auctionContext.getBidRequest().getExt();
        final ExtRequestPrebid prebid = ext != null ? ext.getPrebid() : null;
        final Long auctionTimestamp = prebid != null ? prebid.getAuctiontimestamp() : null;

        return auctionTimestamp != null ? auctionTimestamp : clock.millis();
    }

    private static String integrationFrom(AuctionContext auctionContext) {
        final ExtRequest ext = auctionContext.getBidRequest().getExt();
        final ExtRequestPrebid prebid = ext != null ? ext.getPrebid() : null;

        return prebid != null ? prebid.getIntegration() : null;
    }

    private Events createEvents(String bidder,
                                Account account,
                                String bidId,
                                EventsContext eventsContext,
                                String lineItemId) {

        if (!eventsContext.isEnabledForAccount()) {
            return null;
        }

        return eventsContext.isEnabledForRequest() || StringUtils.isNotEmpty(lineItemId)
                ? eventsService.createEvent(
                bidId,
                bidder,
                account.getId(),
                lineItemId,
                eventsContext.isEnabledForRequest(),
                eventsContext)
                : null;
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
                BooleanUtils.toBoolean(targeting.getIncludewinners()),
                BooleanUtils.toBoolean(targeting.getIncludebidderkeys()),
                BooleanUtils.toBoolean(targeting.getAlwaysincludedeals()),
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
        final AccountAuctionConfig accountAuctionConfig = account.getAuction();
        final Integer accountTruncateTargetAttr =
                accountAuctionConfig != null ? accountAuctionConfig.getTruncateTargetAttr() : null;

        return ObjectUtils.firstNonNull(
                truncateAttrCharsOrNull(targeting.getTruncateattrchars()),
                truncateAttrCharsOrNull(accountTruncateTargetAttr),
                truncateAttrChars);
    }

    private static Integer truncateAttrCharsOrNull(Integer value) {
        return value != null && value >= 0 && value <= 255 ? value : null;
    }

    private static boolean isCachedDebugEnabled(CachedDebugLog cachedDebugLog) {
        return cachedDebugLog != null && cachedDebugLog.isEnabled();
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
            throw new PreBidException(
                    "Error decoding bidRequest.prebid.targeting.pricegranularity: " + e.getMessage(), e);
        }
    }

    private static BidResponse populateSeatNonBid(AuctionContext auctionContext, BidResponse bidResponse) {
        if (!auctionContext.getDebugContext().isShouldReturnAllBidStatuses()) {
            return bidResponse;
        }

        final List<SeatNonBid> seatNonBids = auctionContext.getBidRejectionTrackers().entrySet().stream()
                .map(entry -> toSeatNonBid(entry.getKey(), entry.getValue()))
                .filter(seatNonBid -> !seatNonBid.getNonBid().isEmpty())
                .toList();

        final ExtBidResponse updatedExtBidResponse = Optional.ofNullable(bidResponse.getExt())
                .map(ExtBidResponse::toBuilder)
                .orElseGet(ExtBidResponse::builder)
                .seatnonbid(seatNonBids)
                .build();

        return bidResponse.toBuilder().ext(updatedExtBidResponse).build();
    }

    private static SeatNonBid toSeatNonBid(String bidder, BidRejectionTracker bidRejectionTracker) {
        final List<NonBid> nonBid = bidRejectionTracker.getRejectionReasons().entrySet().stream()
                .map(entry -> NonBid.of(entry.getKey(), entry.getValue()))
                .toList();

        return SeatNonBid.of(bidder, nonBid);
    }

    /**
     * Creates {@link CacheAsset} for the given cache ID.
     */
    private CacheAsset toCacheAsset(String cacheId) {
        return CacheAsset.of(cacheAssetUrlTemplate.concat(cacheId), cacheId);
    }

    private static <T> Set<T> nullIfEmpty(Set<T> set) {
        if (set.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableSet(set);
    }

    private static <K, V> Map<K, V> nullIfEmpty(Map<K, V> map) {
        if (map.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates {@link ExtBidPrebidVideo} from bid extension.
     */
    private Optional<ExtBidPrebidVideo> getExtBidPrebidVideo(ObjectNode bidExt) {
        return getExtPrebid(bidExt, ExtBidPrebid.class)
                .map(ExtBidPrebid::getVideo);
    }

    private <T> Optional<T> getExtPrebid(ObjectNode extNode, Class<T> extClass) {
        return Optional.ofNullable(extNode)
                .filter(ext -> ext.hasNonNull(PREBID_EXT))
                .map(ext -> convertValue(extNode, PREBID_EXT, extClass));
    }

    private <T> T convertValue(JsonNode jsonNode, String key, Class<T> typeClass) {
        try {
            return mapper.mapper().convertValue(jsonNode.get(key), typeClass);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
