package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.auction.mediatypeprocessor.MediaTypeProcessingResult;
import org.prebid.server.auction.mediatypeprocessor.MediaTypeProcessor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.privacy.enforcement.PrivacyEnforcementService;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.floors.PriceFloorAdjuster;
import org.prebid.server.floors.PriceFloorProcessor;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebidBidders;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;
import org.prebid.server.proto.openrtb.ext.request.ExtDooh;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ListUtil;
import org.prebid.server.util.PbsUtil;
import org.prebid.server.util.StreamUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExchangeService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String PREBID_EXT = "prebid";
    private static final String PREBID_META_EXT = "meta";
    private static final String BIDDER_EXT = "bidder";
    private static final String TID_EXT = "tid";
    private static final String ALL_BIDDERS_CONFIG = "*";
    private static final Integer DEFAULT_MULTIBID_LIMIT_MIN = 1;
    private static final Integer DEFAULT_MULTIBID_LIMIT_MAX = 9;
    private static final String EID_ALLOWED_FOR_ALL_BIDDERS = "*";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final Set<String> BIDDER_FIELDS_EXCEPTION_LIST = Set.of(
            "adunitcode", "storedrequest", "options", "is_rewarded_inventory");

    private final double logSamplingRate;
    private final BidderCatalog bidderCatalog;
    private final StoredResponseProcessor storedResponseProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final FpdResolver fpdResolver;
    private final ImpAdjuster impAdjuster;
    private final SupplyChainResolver supplyChainResolver;
    private final DebugResolver debugResolver;
    private final MediaTypeProcessor mediaTypeProcessor;
    private final UidUpdater uidUpdater;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    private final HttpBidderRequester httpBidderRequester;
    private final BidResponseCreator bidResponseCreator;
    private final BidResponsePostProcessor bidResponsePostProcessor;
    private final HookStageExecutor hookStageExecutor;
    private final HttpInteractionLogger httpInteractionLogger;
    private final PriceFloorAdjuster priceFloorAdjuster;
    private final PriceFloorProcessor priceFloorProcessor;
    private final BidsAdjuster bidsAdjuster;
    private final Metrics metrics;
    private final Clock clock;
    private final JacksonMapper mapper;
    private final CriteriaLogManager criteriaLogManager;
    private final boolean enabledStrictAppSiteDoohValidation;

    public ExchangeService(double logSamplingRate,
                           BidderCatalog bidderCatalog,
                           StoredResponseProcessor storedResponseProcessor,
                           PrivacyEnforcementService privacyEnforcementService,
                           FpdResolver fpdResolver,
                           ImpAdjuster impAdjuster,
                           SupplyChainResolver supplyChainResolver,
                           DebugResolver debugResolver,
                           MediaTypeProcessor mediaTypeProcessor,
                           UidUpdater uidUpdater,
                           TimeoutResolver timeoutResolver,
                           TimeoutFactory timeoutFactory,
                           BidRequestOrtbVersionConversionManager ortbVersionConversionManager,
                           HttpBidderRequester httpBidderRequester,
                           BidResponseCreator bidResponseCreator,
                           BidResponsePostProcessor bidResponsePostProcessor,
                           HookStageExecutor hookStageExecutor,
                           HttpInteractionLogger httpInteractionLogger,
                           PriceFloorAdjuster priceFloorAdjuster,
                           PriceFloorProcessor priceFloorProcessor,
                           BidsAdjuster bidsAdjuster,
                           Metrics metrics,
                           Clock clock,
                           JacksonMapper mapper,
                           CriteriaLogManager criteriaLogManager,
                           boolean enabledStrictAppSiteDoohValidation) {

        this.logSamplingRate = logSamplingRate;
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.storedResponseProcessor = Objects.requireNonNull(storedResponseProcessor);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.fpdResolver = Objects.requireNonNull(fpdResolver);
        this.impAdjuster = Objects.requireNonNull(impAdjuster);
        this.supplyChainResolver = Objects.requireNonNull(supplyChainResolver);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.mediaTypeProcessor = Objects.requireNonNull(mediaTypeProcessor);
        this.uidUpdater = Objects.requireNonNull(uidUpdater);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.ortbVersionConversionManager = Objects.requireNonNull(ortbVersionConversionManager);
        this.httpBidderRequester = Objects.requireNonNull(httpBidderRequester);
        this.bidResponseCreator = Objects.requireNonNull(bidResponseCreator);
        this.bidResponsePostProcessor = Objects.requireNonNull(bidResponsePostProcessor);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.priceFloorAdjuster = Objects.requireNonNull(priceFloorAdjuster);
        this.priceFloorProcessor = Objects.requireNonNull(priceFloorProcessor);
        this.bidsAdjuster = Objects.requireNonNull(bidsAdjuster);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);
        this.enabledStrictAppSiteDoohValidation = enabledStrictAppSiteDoohValidation;
    }

    public Future<AuctionContext> holdAuction(AuctionContext context) {
        return processAuctionRequest(context)
                .compose(this::invokeResponseHooks)
                .map(AnalyticsTagsEnricher::enrichWithAnalyticsTags)
                .map(HookDebugInfoEnricher::enrichWithHooksDebugInfo);
    }

    private Future<AuctionContext> processAuctionRequest(AuctionContext context) {
        return context.isRequestRejected()
                ? Future.succeededFuture(context.with(emptyResponse()))
                : runAuction(context);
    }

    private static BidResponse emptyResponse() {
        return BidResponse.builder().seatbid(Collections.emptyList()).build();
    }

    private Future<AuctionContext> runAuction(AuctionContext receivedContext) {
        final UidsCookie uidsCookie = receivedContext.getUidsCookie();
        final BidRequest bidRequest = receivedContext.getBidRequest();
        final Timeout timeout = receivedContext.getTimeoutContext().getTimeout();
        final Account account = receivedContext.getAccount();
        final List<String> debugWarnings = receivedContext.getDebugWarnings();
        final MetricName requestTypeMetric = receivedContext.getRequestTypeMetric();

        final List<SeatBid> storedAuctionResponses = new ArrayList<>();
        final BidderAliases aliases = aliases(bidRequest, account);
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(bidRequest);
        final Map<String, MultiBidConfig> bidderToMultiBid = bidderToMultiBids(bidRequest, debugWarnings);
        receivedContext.getBidRejectionTrackers().putAll(makeBidRejectionTrackers(bidRequest, aliases));

        final boolean debugEnabled = receivedContext.getDebugContext().isDebugEnabled();
        metrics.updateDebugRequestMetrics(debugEnabled);
        metrics.updateAccountDebugRequestMetrics(account, debugEnabled);

        return storedResponseProcessor.getStoredResponseResult(bidRequest.getImp(), timeout)
                .map(storedResponseResult -> populateStoredResponse(storedResponseResult, storedAuctionResponses))
                .compose(storedResponseResult ->
                        extractAuctionParticipations(receivedContext, storedResponseResult, aliases, bidderToMultiBid)
                                .map(receivedContext::with))

                .map(context -> updateRequestMetric(context, uidsCookie, aliases, account, requestTypeMetric))
                .compose(context -> CompositeFuture.join(
                                context.getAuctionParticipations().stream()
                                        .map(auctionParticipation -> processAndRequestBids(
                                                context,
                                                auctionParticipation.getBidderRequest(),
                                                timeout,
                                                aliases)
                                                .map(auctionParticipation::with))
                                        .collect(Collectors.toCollection(ArrayList::new)))
                        // send all the requests to the bidders and gathers results
                        .map(CompositeFuture::<AuctionParticipation>list)
                        .map(storedResponseProcessor::updateStoredBidResponse)
                        .map(auctionParticipations -> storedResponseProcessor.mergeWithBidderResponses(
                                auctionParticipations,
                                storedAuctionResponses,
                                bidRequest.getImp(),
                                context.getBidRejectionTrackers()))
                        .map(auctionParticipations -> dropZeroNonDealBids(
                                auctionParticipations, debugWarnings, debugEnabled))
                        .map(auctionParticipations -> bidsAdjuster.validateAndAdjustBids(
                                auctionParticipations,
                                context,
                                aliases))
                        .map(auctionParticipations -> updateResponsesMetrics(auctionParticipations, account, aliases))
                        .map(context::with))
                // produce response from bidder results
                .compose(context -> bidResponseCreator.create(context, cacheInfo, aliases, bidderToMultiBid)
                        .map(bidResponse -> criteriaLogManager.traceResponse(
                                logger,
                                bidResponse,
                                context.getBidRequest(),
                                debugEnabled))
                        .compose(bidResponse -> bidResponsePostProcessor.postProcess(
                                context.getHttpRequest(), uidsCookie, bidRequest, bidResponse, account))
                        .map(context::with));
    }

    private BidderAliases aliases(BidRequest bidRequest, Account account) {
        final ExtRequestPrebid prebid = PbsUtil.extRequestPrebid(bidRequest);
        final Map<String, String> aliases = prebid != null ? prebid.getAliases() : null;
        final Map<String, Integer> aliasgvlids = prebid != null ? prebid.getAliasgvlids() : null;
        final ExtRequestPrebidAlternateBidderCodes alternateBidderCodes = prebid != null
                ? prebid.getAlternateBidderCodes()
                : null;

        return alternateBidderCodes != null
                ? BidderAliases.of(aliases, aliasgvlids, bidderCatalog, alternateBidderCodes)
                : BidderAliases.of(aliases, aliasgvlids, bidderCatalog, account.getAlternateBidderCodes());
    }

    private static ExtRequestTargeting targeting(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = PbsUtil.extRequestPrebid(bidRequest);
        return prebid != null ? prebid.getTargeting() : null;
    }

    private static BidRequestCacheInfo bidRequestCacheInfo(BidRequest bidRequest) {
        final ExtRequestTargeting targeting = targeting(bidRequest);
        final ExtRequestPrebid prebid = PbsUtil.extRequestPrebid(bidRequest);
        final ExtRequestPrebidCache cache = prebid != null ? prebid.getCache() : null;

        if (targeting != null && cache != null) {
            final boolean shouldCacheBids = cache.getBids() != null;
            final boolean shouldCacheVideoBids = cache.getVastxml() != null;
            final boolean shouldCacheWinningBidsOnly = !targeting.getIncludebidderkeys()
                    // ext.prebid.targeting.includebidderkeys takes precedence
                    && ObjectUtils.defaultIfNull(cache.getWinningonly(), false);

            if (shouldCacheBids || shouldCacheVideoBids || shouldCacheWinningBidsOnly) {
                final Integer cacheBidsTtl = shouldCacheBids ? cache.getBids().getTtlseconds() : null;
                final Integer cacheVideoBidsTtl = shouldCacheVideoBids ? cache.getVastxml().getTtlseconds() : null;
                final boolean returnCreativeBid = shouldCacheBids
                        ? ObjectUtils.defaultIfNull(cache.getBids().getReturnCreative(), true)
                        : false;
                final boolean returnCreativeVideoBid = shouldCacheVideoBids
                        ? ObjectUtils.defaultIfNull(cache.getVastxml().getReturnCreative(), true)
                        : false;

                return BidRequestCacheInfo.builder()
                        .doCaching(true)
                        .shouldCacheBids(shouldCacheBids)
                        .cacheBidsTtl(cacheBidsTtl)
                        .shouldCacheVideoBids(shouldCacheVideoBids)
                        .cacheVideoBidsTtl(cacheVideoBidsTtl)
                        .returnCreativeBids(returnCreativeBid)
                        .returnCreativeVideoBids(returnCreativeVideoBid)
                        .shouldCacheWinningBidsOnly(shouldCacheWinningBidsOnly)
                        .build();
            }
        }
        return BidRequestCacheInfo.noCache();
    }

    private static Map<String, MultiBidConfig> bidderToMultiBids(BidRequest bidRequest, List<String> debugWarnings) {
        final ExtRequestPrebid extRequestPrebid = PbsUtil.extRequestPrebid(bidRequest);
        final Collection<ExtRequestPrebidMultiBid> multiBids = extRequestPrebid != null
                ? CollectionUtils.emptyIfNull(extRequestPrebid.getMultibid())
                : Collections.emptyList();

        final Map<String, MultiBidConfig> bidderToMultiBid = new CaseInsensitiveMap<>();
        for (ExtRequestPrebidMultiBid prebidMultiBid : multiBids) {
            final String bidder = prebidMultiBid.getBidder();
            final List<String> bidders = prebidMultiBid.getBidders();
            final Integer maxBids = prebidMultiBid.getMaxBids();
            final String codePrefix = prebidMultiBid.getTargetBidderCodePrefix();

            if (bidder != null && CollectionUtils.isNotEmpty(bidders)) {
                debugWarnings.add("Invalid MultiBid: bidder %s and bidders %s specified. Only bidder %s will be used."
                        .formatted(bidder, bidders, bidder));
                tryAddBidderWithMultiBid(bidder, maxBids, codePrefix, bidderToMultiBid, debugWarnings);
                continue;
            }

            if (bidder != null) {
                tryAddBidderWithMultiBid(bidder, maxBids, codePrefix, bidderToMultiBid, debugWarnings);
            } else if (CollectionUtils.isNotEmpty(bidders)) {
                if (codePrefix != null) {
                    debugWarnings.add(
                            "Invalid MultiBid: CodePrefix %s that was specified for bidders %s will be skipped."
                                    .formatted(codePrefix, bidders));
                }
                bidders.forEach(currentBidder ->
                        tryAddBidderWithMultiBid(currentBidder, maxBids, null, bidderToMultiBid, debugWarnings));
            } else {
                debugWarnings.add("Invalid MultiBid: Bidder and bidders was not specified.");
            }
        }

        return bidderToMultiBid;
    }

    private static void tryAddBidderWithMultiBid(String bidder,
                                                 Integer maxBids,
                                                 String codePrefix,
                                                 Map<String, MultiBidConfig> bidderToMultiBid,
                                                 List<String> debugWarnings) {
        if (bidderToMultiBid.containsKey(bidder)) {
            debugWarnings.add("Invalid MultiBid: Bidder %s specified multiple times.".formatted(bidder));
            return;
        }

        if (maxBids == null) {
            debugWarnings.add("Invalid MultiBid: MaxBids for bidder %s is not specified and will be skipped."
                    .formatted(bidder));
            return;
        }

        bidderToMultiBid.put(bidder, toMultiBid(bidder, maxBids, codePrefix));
    }

    private static MultiBidConfig toMultiBid(String bidder, Integer maxBids, String codePrefix) {
        final int bidLimit = maxBids < DEFAULT_MULTIBID_LIMIT_MIN
                ? DEFAULT_MULTIBID_LIMIT_MIN
                : maxBids > DEFAULT_MULTIBID_LIMIT_MAX ? DEFAULT_MULTIBID_LIMIT_MAX : maxBids;

        return MultiBidConfig.of(bidder, bidLimit, codePrefix);
    }

    private Map<String, BidRejectionTracker> makeBidRejectionTrackers(BidRequest bidRequest, BidderAliases aliases) {
        final Map<String, Set<String>> impIdToBidders = bidRequest.getImp().stream()
                .filter(Objects::nonNull)
                .filter(imp -> StringUtils.isNotEmpty(imp.getId()))
                .collect(Collectors.toMap(Imp::getId, imp -> bidderNamesFromImpExt(imp, aliases)));

        final Map<String, Set<String>> bidderToImpIds = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : impIdToBidders.entrySet()) {
            final String impId = entry.getKey();
            final Set<String> bidderNames = entry.getValue();
            bidderNames.forEach(bidder ->
                    bidderToImpIds.computeIfAbsent(bidder, bidderName -> new HashSet<>()).add(impId));
        }

        return bidderToImpIds.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new BidRejectionTracker(entry.getKey(), entry.getValue(), logSamplingRate)));
    }

    private static StoredResponseResult populateStoredResponse(StoredResponseResult storedResponseResult,
                                                               List<SeatBid> storedResponse) {
        storedResponse.addAll(storedResponseResult.getAuctionStoredResponse());
        return storedResponseResult;
    }

    private Future<List<AuctionParticipation>> extractAuctionParticipations(
            AuctionContext context,
            StoredResponseResult storedResponseResult,
            BidderAliases aliases,
            Map<String, MultiBidConfig> bidderToMultiBid) {

        final List<Imp> imps = storedResponseResult.getRequiredRequestImps().stream()
                .filter(imp -> bidderParamsFromImpExt(imp.getExt()) != null)
                .toList();
        // identify valid bidders and aliases out of imps
        final List<String> bidders = imps.stream()
                .map(imp -> bidderNamesFromImpExt(imp, aliases))
                .flatMap(Collection::stream)
                .filter(bidder -> isBidderCallActivityAllowed(bidder, context))
                .distinct()
                .toList();
        final Map<String, Map<String, String>> impBidderToStoredBidResponse =
                storedResponseResult.getImpBidderToStoredBidResponse();
        return makeAuctionParticipation(
                bidders,
                context,
                aliases,
                impBidderToStoredBidResponse,
                imps,
                bidderToMultiBid);
    }

    private Set<String> bidderNamesFromImpExt(Imp imp, BidderAliases aliases) {
        return Optional.ofNullable(bidderParamsFromImpExt(imp.getExt())).stream()
                .flatMap(paramsNode -> StreamUtil.asStream(paramsNode.fieldNames()))
                .filter(bidder -> isValidBidder(bidder, aliases))
                .collect(Collectors.toSet());
    }

    private static JsonNode bidderParamsFromImpExt(ObjectNode ext) {
        return ext.get(PREBID_EXT).get(BIDDER_EXT);
    }

    private boolean isValidBidder(String bidder, BidderAliases aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.isAliasDefined(bidder);
    }

    private static boolean isBidderCallActivityAllowed(String bidder, AuctionContext auctionContext) {
        final ActivityInfrastructure activityInfrastructure = auctionContext.getActivityInfrastructure();
        final ActivityInvocationPayload activityInvocationPayload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, bidder),
                auctionContext.getBidRequest());

        return activityInfrastructure.isAllowed(
                Activity.CALL_BIDDER,
                activityInvocationPayload);
    }

    private Future<List<AuctionParticipation>> makeAuctionParticipation(
            List<String> bidders,
            AuctionContext context,
            BidderAliases aliases,
            Map<String, Map<String, String>> impBidderToStoredResponse,
            List<Imp> imps,
            Map<String, MultiBidConfig> bidderToMultiBid) {

        final BidRequest bidRequest = context.getBidRequest();
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final Map<String, ExtBidderConfigOrtb> biddersToConfigs = getBiddersToConfigs(prebid);
        final Map<String, List<String>> eidPermissions = getEidPermissions(prebid);
        final Map<String, User> bidderToUser =
                prepareUsers(bidders, context, aliases, biddersToConfigs, eidPermissions);

        return privacyEnforcementService.mask(context, bidderToUser, aliases)
                .map(bidderToPrivacyResult -> getAuctionParticipation(
                        bidderToPrivacyResult,
                        bidRequest,
                        impBidderToStoredResponse,
                        imps,
                        bidderToMultiBid,
                        biddersToConfigs,
                        aliases,
                        context));
    }

    private Map<String, ExtBidderConfigOrtb> getBiddersToConfigs(ExtRequestPrebid prebid) {
        final List<ExtRequestPrebidBidderConfig> bidderConfigs = prebid == null ? null : prebid.getBidderconfig();

        if (CollectionUtils.isEmpty(bidderConfigs)) {
            return Collections.emptyMap();
        }

        final Map<String, ExtBidderConfigOrtb> bidderToConfig = new CaseInsensitiveMap<>();

        bidderConfigs.stream()
                .filter(prebidBidderConfig -> prebidBidderConfig.getBidders().contains(ALL_BIDDERS_CONFIG))
                .map(prebidBidderConfig -> prebidBidderConfig.getConfig().getOrtb2())
                .findFirst()
                .ifPresent(extBidderConfigFpd -> bidderToConfig.put(ALL_BIDDERS_CONFIG, extBidderConfigFpd));

        for (ExtRequestPrebidBidderConfig config : bidderConfigs) {
            for (String bidder : config.getBidders()) {
                final ExtBidderConfigOrtb concreteFpd = config.getConfig().getOrtb2();
                bidderToConfig.putIfAbsent(bidder, concreteFpd);
            }
        }
        return bidderToConfig;
    }

    private Map<String, List<String>> getEidPermissions(ExtRequestPrebid prebid) {
        final ExtRequestPrebidData prebidData = prebid != null ? prebid.getData() : null;
        final List<ExtRequestPrebidDataEidPermissions> eidPermissions = prebidData != null
                ? prebidData.getEidPermissions()
                : null;
        return CollectionUtils.emptyIfNull(eidPermissions).stream()
                .collect(Collectors.toMap(ExtRequestPrebidDataEidPermissions::getSource,
                        ExtRequestPrebidDataEidPermissions::getBidders));
    }

    private static List<String> firstPartyDataBidders(ExtRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final ExtRequestPrebidData data = prebid == null ? null : prebid.getData();
        return data == null ? null : data.getBidders();
    }

    private Map<String, User> prepareUsers(List<String> bidders,
                                           AuctionContext context,
                                           BidderAliases aliases,
                                           Map<String, ExtBidderConfigOrtb> biddersToConfigs,
                                           Map<String, List<String>> eidPermissions) {

        final BidRequest bidRequest = context.getBidRequest();
        final List<String> firstPartyDataBidders = firstPartyDataBidders(bidRequest.getExt());

        final Map<String, User> bidderToUser = new HashMap<>();
        for (String bidder : bidders) {
            final ExtBidderConfigOrtb fpdConfig = ObjectUtils.defaultIfNull(biddersToConfigs.get(bidder),
                    biddersToConfigs.get(ALL_BIDDERS_CONFIG));
            final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.stream()
                    .anyMatch(fpdBidder -> StringUtils.equalsIgnoreCase(fpdBidder, bidder));
            final User preparedUser = prepareUser(
                    bidder, context, aliases, useFirstPartyData, fpdConfig, eidPermissions);
            bidderToUser.put(bidder, preparedUser);
        }
        return bidderToUser;
    }

    private User prepareUser(String bidder,
                             AuctionContext context,
                             BidderAliases aliases,
                             boolean useFirstPartyData,
                             ExtBidderConfigOrtb fpdConfig,
                             Map<String, List<String>> eidPermissions) {

        final User user = context.getBidRequest().getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final UpdateResult<String> buyerUidUpdateResult = uidUpdater.updateUid(bidder, context, aliases);
        final List<Eid> userEids = extractUserEids(user);
        final List<Eid> allowedUserEids = resolveAllowedEids(userEids, bidder, eidPermissions);
        final boolean shouldUpdateUserEids = allowedUserEids.size() != CollectionUtils.emptyIfNull(userEids).size();
        final boolean shouldCleanExtPrebid = extUser != null && extUser.getPrebid() != null;
        final boolean shouldCleanExtData = extUser != null && extUser.getData() != null && !useFirstPartyData;
        final boolean shouldUpdateUserExt = shouldCleanExtData || shouldCleanExtPrebid;
        final boolean shouldCleanData = user != null && user.getData() != null && !useFirstPartyData;

        User maskedUser = user;
        if (buyerUidUpdateResult.isUpdated() || shouldUpdateUserEids || shouldUpdateUserExt || shouldCleanData) {
            final User.UserBuilder userBuilder = user == null ? User.builder() : user.toBuilder();
            userBuilder.buyeruid(buyerUidUpdateResult.getValue());

            if (shouldUpdateUserEids) {
                userBuilder.eids(ListUtil.nullIfEmpty(allowedUserEids));
            }

            if (shouldUpdateUserExt) {
                final ExtUser updatedExtUser = extUser.toBuilder()
                        .prebid(null)
                        .data(shouldCleanExtData ? null : extUser.getData())
                        .build();
                userBuilder.ext(updatedExtUser.isEmpty() ? null : updatedExtUser);
            }

            if (shouldCleanData) {
                userBuilder.data(null);
            }

            maskedUser = userBuilder.build();
        }

        return useFirstPartyData
                ? fpdResolver.resolveUser(maskedUser, fpdConfig == null ? null : fpdConfig.getUser())
                : maskedUser;
    }

    private List<Eid> extractUserEids(User user) {
        return user != null ? user.getEids() : null;
    }

    private List<Eid> resolveAllowedEids(List<Eid> userEids, String bidder, Map<String, List<String>> eidPermissions) {
        return CollectionUtils.emptyIfNull(userEids)
                .stream()
                .filter(userEid -> isUserEidAllowed(userEid.getSource(), eidPermissions, bidder))
                .toList();
    }

    private boolean isUserEidAllowed(String source, Map<String, List<String>> eidPermissions, String bidder) {
        final List<String> allowedBidders = eidPermissions.get(source);
        return CollectionUtils.isEmpty(allowedBidders) || allowedBidders.stream()
                .anyMatch(allowedBidder -> StringUtils.equalsIgnoreCase(allowedBidder, bidder)
                        || EID_ALLOWED_FOR_ALL_BIDDERS.equals(allowedBidder));
    }

    private List<AuctionParticipation> getAuctionParticipation(
            List<BidderPrivacyResult> bidderPrivacyResults,
            BidRequest bidRequest,
            Map<String, Map<String, String>> impBidderToStoredBidResponse,
            List<Imp> imps,
            Map<String, MultiBidConfig> bidderToMultiBid,
            Map<String, ExtBidderConfigOrtb> biddersToConfigs,
            BidderAliases aliases,
            AuctionContext context) {

        final Map<String, JsonNode> bidderToPrebidBidders = bidderToPrebidBidders(bidRequest);
        final List<AuctionParticipation> bidderRequests = bidderPrivacyResults.stream()
                // for each bidder create a new request that is a copy of original request except buyerid, imp
                // extensions, ext.prebid.data.bidders and ext.prebid.bidders.
                // Also, check whether to pass user.ext.data, app.ext.data, dooh.ext.data and site.ext.data or not.
                .map(bidderPrivacyResult -> createAuctionParticipation(
                        bidderPrivacyResult,
                        impBidderToStoredBidResponse,
                        imps,
                        bidderToMultiBid,
                        biddersToConfigs,
                        bidderToPrebidBidders,
                        aliases,
                        context))
                // Can't be removed after we prepare workflow to filter blocked
                .filter(auctionParticipation -> !auctionParticipation.isRequestBlocked())
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(bidderRequests);
        return bidderRequests;
    }

    /**
     * Extracts a map of bidders to their arguments from {@link ObjectNode} prebid.bidders.
     */
    private static Map<String, JsonNode> bidderToPrebidBidders(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = PbsUtil.extRequestPrebid(bidRequest);
        final ObjectNode bidders = prebid == null ? null : prebid.getBidders();

        if (bidders == null || bidders.isNull()) {
            return Collections.emptyMap();
        }

        final Map<String, JsonNode> bidderToPrebidParameters = new HashMap<>();
        final Iterator<Map.Entry<String, JsonNode>> biddersToParams = bidders.fields();
        while (biddersToParams.hasNext()) {
            final Map.Entry<String, JsonNode> bidderToParam = biddersToParams.next();
            bidderToPrebidParameters.put(bidderToParam.getKey(), bidderToParam.getValue());
        }
        return bidderToPrebidParameters;
    }

    private AuctionParticipation createAuctionParticipation(
            BidderPrivacyResult bidderPrivacyResult,
            Map<String, Map<String, String>> impBidderToStoredBidResponse,
            List<Imp> imps,
            Map<String, MultiBidConfig> bidderToMultiBid,
            Map<String, ExtBidderConfigOrtb> biddersToConfigs,
            Map<String, JsonNode> bidderToPrebidBidders,
            BidderAliases bidderAliases,
            AuctionContext context) {

        final boolean blockedRequestByTcf = bidderPrivacyResult.isBlockedRequestByTcf();
        final boolean blockedAnalyticsByTcf = bidderPrivacyResult.isBlockedAnalyticsByTcf();
        final String bidder = bidderPrivacyResult.getRequestBidder();
        if (blockedRequestByTcf) {
            context.getBidRejectionTrackers()
                    .get(bidder)
                    .rejectAllImps(BidRejectionReason.REQUEST_BLOCKED_PRIVACY);

            return AuctionParticipation.builder()
                    .bidder(bidder)
                    .requestBlocked(true)
                    .analyticsBlocked(blockedAnalyticsByTcf)
                    .build();
        }

        final OrtbVersion ortbVersion = bidderSupportedOrtbVersion(bidder, bidderAliases);
        // stored bid response supported only for single imp requests
        final String storedBidResponse = impBidderToStoredBidResponse.size() == 1
                ? impBidderToStoredBidResponse.get(imps.getFirst().getId()).get(bidder)
                : null;
        final BidRequest preparedBidRequest = prepareBidRequest(
                bidderPrivacyResult,
                imps,
                bidderToMultiBid,
                biddersToConfigs,
                bidderToPrebidBidders,
                bidderAliases,
                context);

        final BidderRequest bidderRequest = BidderRequest.builder()
                .bidder(bidder)
                .ortbVersion(ortbVersion)
                .storedResponse(storedBidResponse)
                .bidRequest(preparedBidRequest)
                .build();

        return AuctionParticipation.builder()
                .bidder(bidder)
                .bidderRequest(bidderRequest)
                .requestBlocked(false)
                .analyticsBlocked(blockedAnalyticsByTcf)
                .build();
    }

    private OrtbVersion bidderSupportedOrtbVersion(String bidder, BidderAliases aliases) {
        return bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidder)).getOrtbVersion();
    }

    private BidRequest prepareBidRequest(BidderPrivacyResult bidderPrivacyResult,
                                         List<Imp> imps,
                                         Map<String, MultiBidConfig> bidderToMultiBid,
                                         Map<String, ExtBidderConfigOrtb> biddersToConfigs,
                                         Map<String, JsonNode> bidderToPrebidBidders,
                                         BidderAliases bidderAliases,
                                         AuctionContext context) {

        final String bidder = bidderPrivacyResult.getRequestBidder();
        final BidRequest bidRequest = priceFloorProcessor.enrichWithPriceFloors(
                context.getBidRequest().toBuilder().imp(imps).build(),
                context.getAccount(),
                bidder,
                context.getPrebidErrors(),
                context.getDebugWarnings());
        final boolean transmitTid = transmitTransactionId(bidder, context);
        final List<String> firstPartyDataBidders = firstPartyDataBidders(bidRequest.getExt());
        final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.stream()
                .anyMatch(fpdBidder -> StringUtils.equalsIgnoreCase(fpdBidder, bidder));

        final ExtBidderConfigOrtb fpdConfig = ObjectUtils.defaultIfNull(
                biddersToConfigs.get(bidder),
                biddersToConfigs.get(ALL_BIDDERS_CONFIG));

        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        final Dooh dooh = bidRequest.getDooh();
        final ObjectNode fpdSite = fpdConfig != null ? fpdConfig.getSite() : null;
        final ObjectNode fpdApp = fpdConfig != null ? fpdConfig.getApp() : null;
        final ObjectNode fpdDooh = fpdConfig != null ? fpdConfig.getDooh() : null;
        final App preparedApp = prepareApp(app, fpdApp, useFirstPartyData);
        final Site preparedSite = prepareSite(site, fpdSite, useFirstPartyData);
        final Dooh preparedDooh = prepareDooh(dooh, fpdDooh, useFirstPartyData);

        final List<String> distributionChannels = new ArrayList<>();
        Optional.ofNullable(preparedApp).ifPresent(ignored -> distributionChannels.add("app"));
        Optional.ofNullable(preparedDooh).ifPresent(ignored -> distributionChannels.add("dooh"));
        Optional.ofNullable(preparedSite).ifPresent(ignored -> distributionChannels.add("site"));

        if (distributionChannels.size() > 1) {
            metrics.updateAlertsMetrics(MetricName.general);
            if (enabledStrictAppSiteDoohValidation) {
                conditionalLogger.error("More than one distribution channel is present", logSamplingRate);
                throw new InvalidRequestException(
                        String.join(" and ", distributionChannels) + " are present, "
                                + "but no more than one of site or app or dooh can be defined");
            }

            context.getDebugWarnings().add("BidRequest contains " + String.join(" and ", distributionChannels)
                    + ". Only the first one is applicable, the others are ignored");
            final String logMessage = String.join(" and ", distributionChannels) + " are present. "
                    + "Referer: " + context.getHttpRequest().getHeaders().get(HttpUtil.REFERER_HEADER) + ". "
                    + "Account: " + context.getAccount().getId();
            conditionalLogger.warn(logMessage, logSamplingRate);
        }

        final boolean isApp = preparedApp != null;
        final boolean isDooh = !isApp && preparedDooh != null;
        final boolean isSite = !isApp && !isDooh && preparedSite != null;

        final List<Imp> preparedImps = prepareImps(
                bidder,
                bidRequest,
                transmitTid,
                useFirstPartyData,
                context.getAccount(),
                bidderAliases,
                context.getDebugWarnings());

        return bidRequest.toBuilder()
                // User was already prepared above
                .user(bidderPrivacyResult.getUser())
                .device(bidderPrivacyResult.getDevice())
                .imp(preparedImps)
                .app(isApp ? preparedApp : null)
                .dooh(isDooh ? preparedDooh : null)
                .site(isSite ? preparedSite : null)
                .source(prepareSource(bidder, bidRequest, transmitTid))
                .ext(prepareExt(bidder, bidderToPrebidBidders, bidderToMultiBid, bidRequest.getExt()))
                .build();
    }

    private static boolean transmitTransactionId(String bidder, AuctionContext context) {
        final BidRequest bidRequest = context.getBidRequest();
        final Boolean createTids = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getCreateTids)
                .orElse(null);

        if (createTids == null) {
            final ActivityInvocationPayload payload = BidRequestActivityInvocationPayload.of(
                    ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, bidder),
                    bidRequest);
            return context.getActivityInfrastructure().isAllowed(Activity.TRANSMIT_TID, payload);
        }

        return createTids;
    }

    private List<Imp> prepareImps(String bidder,
                                  BidRequest bidRequest,
                                  boolean transmitTid,
                                  boolean useFirstPartyData,
                                  Account account,
                                  BidderAliases bidderAliases,
                                  List<String> debugWarnings) {

        return bidRequest.getImp().stream()
                .filter(imp -> bidderParamsFromImpExt(imp.getExt()).hasNonNull(bidder))
                .map(imp -> imp.toBuilder().ext(imp.getExt().deepCopy()).build())
                .map(imp -> impAdjuster.adjust(imp, bidder, bidderAliases, debugWarnings))
                .map(imp -> prepareImp(imp, bidder, bidRequest, transmitTid, useFirstPartyData, account, debugWarnings))
                .toList();
    }

    private Imp prepareImp(Imp imp,
                           String bidder,
                           BidRequest bidRequest,
                           boolean transmitTid,
                           boolean useFirstPartyData,
                           Account account,
                           List<String> debugWarnings) {

        final Price adjustedPrice = resolveBidPrice(imp, bidder, bidRequest, account, debugWarnings);
        return imp.toBuilder()
                .bidfloor(adjustedPrice.getValue())
                .bidfloorcur(adjustedPrice.getCurrency())
                .ext(prepareImpExt(bidder, imp.getExt(), transmitTid, useFirstPartyData))
                .build();
    }

    private Price resolveBidPrice(Imp imp,
                                  String bidder,
                                  BidRequest bidRequest,
                                  Account account,
                                  List<String> debugWarnings) {

        return priceFloorAdjuster.adjustForImp(imp, bidder, bidRequest, account, debugWarnings);
    }

    private ObjectNode prepareImpExt(String bidder,
                                     ObjectNode impExt,
                                     boolean transmitTid,
                                     boolean useFirstPartyData) {
        final JsonNode bidderNode = bidderParamsFromImpExt(impExt).get(bidder);
        final JsonNode impExtPrebid = cleanUpImpExtPrebid(impExt.get(PREBID_EXT));
        Optional.ofNullable(impExtPrebid).ifPresentOrElse(
                ext -> impExt.set(PREBID_EXT, ext),
                () -> impExt.remove(PREBID_EXT));
        impExt.set(BIDDER_EXT, bidderNode);
        if (!transmitTid) {
            impExt.remove(TID_EXT);
        }

        return fpdResolver.resolveImpExt(impExt, useFirstPartyData);
    }

    private JsonNode cleanUpImpExtPrebid(JsonNode extImpPrebid) {
        if (extImpPrebid.size() <= 1) {
            return null;
        }

        final Iterator<String> fieldsIterator = extImpPrebid.fieldNames();
        final ObjectNode modifiedExtImpPrebid = extImpPrebid.deepCopy();

        while (fieldsIterator.hasNext()) {
            final String fieldName = fieldsIterator.next();
            if (!BIDDER_FIELDS_EXCEPTION_LIST.contains(fieldName)) {
                modifiedExtImpPrebid.remove(fieldName);
            }
        }

        return modifiedExtImpPrebid;
    }

    private App prepareApp(App app, ObjectNode fpdApp, boolean useFirstPartyData) {
        final ExtApp appExt = app != null ? app.getExt() : null;
        final Content content = app != null ? app.getContent() : null;
        final boolean shouldCleanExtData = appExt != null && appExt.getData() != null && !useFirstPartyData;
        final boolean shouldCleanContentData = content != null && content.getData() != null && !useFirstPartyData;

        final App maskedApp = shouldCleanExtData || shouldCleanContentData
                ? app.toBuilder()
                .ext(shouldCleanExtData ? maskExtApp(appExt) : appExt)
                .content(shouldCleanContentData ? prepareContent(content) : content)
                .build()
                : app;

        return useFirstPartyData ? fpdResolver.resolveApp(maskedApp, fpdApp) : maskedApp;
    }

    private static ExtApp maskExtApp(ExtApp appExt) {
        final ExtApp maskedExtApp = ExtApp.of(appExt.getPrebid(), null);
        return maskedExtApp.isEmpty() ? null : maskedExtApp;
    }

    private Site prepareSite(Site site, ObjectNode fpdSite, boolean useFirstPartyData) {
        final ExtSite siteExt = site != null ? site.getExt() : null;
        final Content content = site != null ? site.getContent() : null;
        final boolean shouldCleanExtData = siteExt != null && siteExt.getData() != null && !useFirstPartyData;
        final boolean shouldCleanContentData = content != null && content.getData() != null && !useFirstPartyData;

        final Site maskedSite = shouldCleanExtData || shouldCleanContentData
                ? site.toBuilder()
                .ext(shouldCleanExtData ? maskExtSite(siteExt) : siteExt)
                .content(shouldCleanContentData ? prepareContent(content) : content)
                .build()
                : site;

        return useFirstPartyData ? fpdResolver.resolveSite(maskedSite, fpdSite) : maskedSite;
    }

    private static ExtSite maskExtSite(ExtSite siteExt) {
        final ExtSite maskedExtSite = ExtSite.of(siteExt.getAmp(), null);
        return maskedExtSite.isEmpty() ? null : maskedExtSite;
    }

    private Dooh prepareDooh(Dooh dooh, ObjectNode fpdDooh, boolean useFirstPartyData) {
        final ExtDooh doohExt = dooh != null ? dooh.getExt() : null;
        final Content content = dooh != null ? dooh.getContent() : null;
        final boolean shouldCleanExtData = doohExt != null && doohExt.getData() != null && !useFirstPartyData;
        final boolean shouldCleanContentData = content != null && content.getData() != null && !useFirstPartyData;

        final Dooh maskedDooh = shouldCleanExtData || shouldCleanContentData
                ? dooh.toBuilder()
                .ext(shouldCleanExtData ? null : doohExt)
                .content(shouldCleanContentData ? prepareContent(content) : content)
                .build()
                : dooh;

        return useFirstPartyData ? fpdResolver.resolveDooh(maskedDooh, fpdDooh) : maskedDooh;
    }

    private static Content prepareContent(Content content) {
        final Content updatedContent = content.toBuilder().data(null).build();
        return updatedContent.isEmpty() ? null : updatedContent;
    }

    private Source prepareSource(String bidder, BidRequest bidRequest, boolean transmitTid) {
        final Source receivedSource = bidRequest.getSource();

        final SupplyChain bidderSchain = supplyChainResolver.resolveForBidder(bidder, bidRequest);

        if (bidderSchain == null && (transmitTid || receivedSource == null)) {
            return receivedSource;
        }

        return receivedSource == null
                ? Source.builder().schain(bidderSchain).build()
                : receivedSource.toBuilder()
                .schain(bidderSchain != null ? bidderSchain : receivedSource.getSchain())
                .tid(transmitTid ? receivedSource.getTid() : null)
                .build();
    }

    private ExtRequest prepareExt(String bidder,
                                  Map<String, JsonNode> bidderToPrebidBidders,
                                  Map<String, MultiBidConfig> bidderToMultiBid,
                                  ExtRequest requestExt) {

        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final List<ExtRequestPrebidSchain> extPrebidSchains = extPrebid != null ? extPrebid.getSchains() : null;
        final ExtRequestPrebidData extPrebidData = extPrebid != null ? extPrebid.getData() : null;
        final List<ExtRequestPrebidBidderConfig> extPrebidBidderconfig =
                extPrebid != null ? extPrebid.getBidderconfig() : null;

        final boolean suppressSchains = extPrebidSchains != null;
        final boolean suppressBidderConfig = extPrebidBidderconfig != null;
        final boolean suppressPrebidData = extPrebidData != null;
        final boolean suppressBidderParameters = extPrebid != null && extPrebid.getBidderparams() != null;
        final boolean suppressAliases = extPrebid != null && extPrebid.getAliases() != null;

        if (bidderToPrebidBidders.isEmpty()
                && bidderToMultiBid.isEmpty()
                && !suppressSchains
                && !suppressBidderConfig
                && !suppressPrebidData
                && !suppressBidderParameters
                && !suppressAliases) {

            return requestExt;
        }

        final JsonNode prebidParameters = bidderToPrebidBidders.get(bidder);
        final ObjectNode bidders = prebidParameters != null
                ? mapper.mapper().valueToTree(ExtPrebidBidders.of(prebidParameters))
                : null;
        final ExtRequestPrebid.ExtRequestPrebidBuilder extPrebidBuilder = Optional.ofNullable(extPrebid)
                .map(ExtRequestPrebid::toBuilder)
                .orElse(ExtRequestPrebid.builder());

        return ExtRequest.of(extPrebidBuilder
                .multibid(resolveExtRequestMultiBids(bidderToMultiBid.get(bidder), bidder))
                .bidders(bidders)
                .bidderparams(prepareBidderParameters(extPrebid, bidder))
                .schains(null)
                .data(null)
                .bidderconfig(null)
                .aliases(null)
                .build());
    }

    private List<ExtRequestPrebidMultiBid> resolveExtRequestMultiBids(MultiBidConfig multiBidConfig,
                                                                      String bidder) {
        return multiBidConfig != null
                ? Collections.singletonList(ExtRequestPrebidMultiBid.of(
                bidder, null, multiBidConfig.getMaxBids(), multiBidConfig.getTargetBidderCodePrefix()))
                : null;
    }

    private ObjectNode prepareBidderParameters(ExtRequestPrebid prebid, String bidder) {
        final ObjectNode bidderParams = prebid != null ? prebid.getBidderparams() : null;
        final JsonNode params = bidderParams != null ? bidderParams.get(bidder) : null;
        return params != null ? mapper.mapper().createObjectNode().set(bidder, params) : null;
    }

    private AuctionContext updateRequestMetric(AuctionContext context,
                                               UidsCookie uidsCookie,
                                               BidderAliases aliases,
                                               Account account,
                                               MetricName requestTypeMetric) {

        final List<AuctionParticipation> auctionParticipations = context.getAuctionParticipations();

        metrics.updateRequestBidderCardinalityMetric(auctionParticipations.size());
        metrics.updateAccountRequestMetrics(account, requestTypeMetric);

        for (AuctionParticipation auctionParticipation : auctionParticipations) {
            if (auctionParticipation.isRequestBlocked()) {
                continue;
            }

            final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
            final String bidder = aliases.resolveBidder(bidderRequest.getBidder());
            final boolean isApp = bidderRequest.getBidRequest().getApp() != null;
            final boolean noBuyerId = bidderCatalog.usersyncerByName(bidder)
                    .map(Usersyncer::getCookieFamilyName)
                    .map(cookieFamily -> StringUtils.isBlank(uidsCookie.uidFrom(cookieFamily)))
                    .orElse(false);

            metrics.updateAdapterRequestTypeAndNoCookieMetrics(bidder, requestTypeMetric, !isApp && noBuyerId);
        }

        return context;
    }

    private Future<BidderResponse> processAndRequestBids(AuctionContext auctionContext,
                                                         BidderRequest bidderRequest,
                                                         Timeout timeout,
                                                         BidderAliases aliases) {

        final String bidderName = bidderRequest.getBidder();
        final MediaTypeProcessingResult mediaTypeProcessingResult = mediaTypeProcessor.process(
                bidderRequest.getBidRequest(), bidderName, aliases, auctionContext.getAccount());
        final List<BidderError> mediaTypeProcessingErrors = mediaTypeProcessingResult.getErrors();
        if (mediaTypeProcessingResult.isRejected()) {
            return processReject(
                    auctionContext,
                    BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE,
                    mediaTypeProcessingErrors,
                    bidderName);
        }
        if (isUnacceptableCurrency(auctionContext, aliases.resolveBidder(bidderName))) {
            return processReject(
                    auctionContext,
                    BidRejectionReason.REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY,
                    List.of(BidderError.generic("No match between the configured currencies and bidRequest.cur")),
                    bidderName);
        }

        return Future.succeededFuture(mediaTypeProcessingResult.getBidRequest())
                .map(bidderRequest::with)
                .compose(modifiedBidderRequest -> invokeHooksAndRequestBids(
                        auctionContext, modifiedBidderRequest, timeout, aliases))
                .map(bidderResponse -> bidderResponse.with(
                        addWarnings(bidderResponse.getSeatBid(), mediaTypeProcessingErrors)));
    }

    private boolean isUnacceptableCurrency(AuctionContext auctionContext, String originalBidderName) {
        final List<String> requestCurrencies = auctionContext.getBidRequest().getCur();
        final List<String> bidAcceptableCurrencies =
                Optional.ofNullable(bidderCatalog.bidderInfoByName(originalBidderName))
                        .map(BidderInfo::getCurrencyAccepted)
                        .orElse(null);

        if (CollectionUtils.isEmpty(requestCurrencies) || CollectionUtils.isEmpty(bidAcceptableCurrencies)) {
            return false;
        }

        return !CollectionUtils.containsAny(requestCurrencies, bidAcceptableCurrencies);
    }

    private static Future<BidderResponse> processReject(AuctionContext auctionContext,
                                                        BidRejectionReason bidRejectionReason,
                                                        List<BidderError> warnings,
                                                        String bidderName) {

        auctionContext.getBidRejectionTrackers()
                .get(bidderName)
                .rejectAllImps(bidRejectionReason);
        final BidderSeatBid bidderSeatBid = BidderSeatBid.builder()
                .warnings(warnings)
                .build();
        return Future.succeededFuture(BidderResponse.of(bidderName, bidderSeatBid, 0));
    }

    private static BidderSeatBid addWarnings(BidderSeatBid seatBid, List<BidderError> warnings) {
        return CollectionUtils.isNotEmpty(warnings)
                ? seatBid.toBuilder()
                .warnings(ListUtils.union(warnings, seatBid.getWarnings()))
                .build()
                : seatBid;
    }

    private Future<BidderResponse> invokeHooksAndRequestBids(AuctionContext auctionContext,
                                                             BidderRequest bidderRequest,
                                                             Timeout timeout,
                                                             BidderAliases aliases) {

        return hookStageExecutor.executeBidderRequestStage(bidderRequest, auctionContext)
                .compose(stageResult -> requestBidsOrRejectBidder(
                        stageResult, bidderRequest, auctionContext, timeout, aliases))
                .compose(bidderResponse -> hookStageExecutor.executeRawBidderResponseStage(
                                bidderResponse, auctionContext)
                        .map(stageResult -> rejectBidderResponseOrProceed(stageResult, bidderResponse)));
    }

    private Future<BidderResponse> requestBidsOrRejectBidder(
            HookStageExecutionResult<BidderRequestPayload> hookStageResult,
            BidderRequest bidderRequest,
            AuctionContext auctionContext,
            Timeout timeout,
            BidderAliases aliases) {

        httpInteractionLogger.maybeLogBidderRequest(auctionContext, bidderRequest);
        if (hookStageResult.isShouldReject()) {
            auctionContext.getBidRejectionTrackers()
                    .get(bidderRequest.getBidder())
                    .rejectAllImps(BidRejectionReason.REQUEST_BLOCKED_GENERAL);

            return Future.succeededFuture(BidderResponse.of(bidderRequest.getBidder(), BidderSeatBid.empty(), 0));
        }

        final BidderRequest enrichedBidderRequest = bidderRequest.toBuilder()
                .bidRequest(hookStageResult.getPayload().bidRequest())
                .build();
        return requestBids(enrichedBidderRequest, auctionContext, timeout, aliases);
    }

    /**
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest,
                                               AuctionContext auctionContext,
                                               Timeout timeout,
                                               BidderAliases aliases) {

        final CaseInsensitiveMultiMap requestHeaders = auctionContext.getHttpRequest().getHeaders();
        final String bidderName = bidderRequest.getBidder();
        final String resolvedBidderName = aliases.resolveBidder(bidderName);
        final Bidder<?> bidder = bidderCatalog.bidderByName(resolvedBidderName);
        final long bidderTmaxDeductionMs = bidderCatalog.bidderInfoByName(resolvedBidderName).getTmaxDeductionMs();
        final BidRejectionTracker bidRejectionTracker = auctionContext.getBidRejectionTrackers().get(bidderName);

        final TimeoutContext timeoutContext = auctionContext.getTimeoutContext();
        final long auctionStartTime = timeoutContext.getStartTime();
        final int adjustmentFactor = timeoutContext.getAdjustmentFactor();
        final long bidderRequestStartTime = clock.millis();

        return Future.succeededFuture(bidderRequest.getBidRequest())
                .map(bidRequest -> adjustTmax(
                        bidRequest, auctionStartTime, adjustmentFactor, bidderRequestStartTime, bidderTmaxDeductionMs))
                .map(bidRequest -> ortbVersionConversionManager.convertFromAuctionSupportedVersion(
                        bidRequest, bidderRequest.getOrtbVersion()))
                .map(bidderRequest::with)
                .compose(convertedBidderRequest -> httpBidderRequester.requestBids(
                        bidder,
                        convertedBidderRequest,
                        bidRejectionTracker,
                        adjustTimeout(timeout, auctionStartTime, bidderRequestStartTime),
                        requestHeaders,
                        aliases,
                        debugResolver.resolveDebugForBidder(auctionContext, resolvedBidderName)))
                .map(seatBid -> populateBidderCode(seatBid, bidderName, resolvedBidderName))
                .map(seatBid -> BidderResponse.of(bidderName, seatBid, responseTime(bidderRequestStartTime)));
    }

    private BidderSeatBid populateBidderCode(BidderSeatBid seatBid, String bidderName, String resolvedBidderName) {
        return seatBid.with(seatBid.getBids().stream()
                .map(bidderBid -> bidderBid.toBuilder()
                        .seat(ObjectUtils.defaultIfNull(bidderBid.getSeat(), bidderName))
                        .bid(bidderBid.getBid().toBuilder()
                                .ext(prepareBidExt(bidderBid.getBid(), resolvedBidderName))
                                .build())
                        .build())
                .toList());
    }

    private ObjectNode prepareBidExt(Bid bid, String bidderName) {
        final ObjectNode bidExt = bid.getExt();
        final ObjectNode extPrebid = bidExt != null ? (ObjectNode) bidExt.get(PREBID_EXT) : null;
        final ExtBidPrebidMeta meta = extPrebid != null
                ? getExtPrebidMeta(extPrebid.get(PREBID_META_EXT))
                : null;

        final ExtBidPrebidMeta updatedMeta = Optional.ofNullable(meta)
                .map(ExtBidPrebidMeta::toBuilder)
                .orElseGet(ExtBidPrebidMeta::builder)
                .adapterCode(bidderName)
                .build();

        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();
        final ObjectNode updatedBidExtPrebid = extPrebid != null ? extPrebid : mapper.mapper().createObjectNode();
        updatedBidExtPrebid.set(PREBID_META_EXT, mapper.mapper().valueToTree(updatedMeta));
        updatedBidExt.set(PREBID_EXT, mapper.mapper().valueToTree(updatedBidExtPrebid));

        return updatedBidExt;
    }

    private ExtBidPrebidMeta getExtPrebidMeta(JsonNode bidExtPrebidMeta) {
        try {
            return bidExtPrebidMeta != null
                    ? mapper.mapper().convertValue(bidExtPrebidMeta, ExtBidPrebidMeta.class)
                    : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BidRequest adjustTmax(BidRequest bidRequest,
                                  long startTime,
                                  int adjustmentFactor,
                                  long currentTime,
                                  long bidderTmaxDeductionMs) {

        final long tmax = timeoutResolver.limitToMax(bidRequest.getTmax());
        final long adjustedTmax = timeoutResolver.adjustForBidder(
                tmax, adjustmentFactor, currentTime - startTime, bidderTmaxDeductionMs);

        return tmax != adjustedTmax
                ? bidRequest.toBuilder().tmax(adjustedTmax).build()
                : bidRequest;
    }

    private Timeout adjustTimeout(Timeout timeout, long startTime, long currentTime) {
        final long adjustedTmax = timeoutResolver.adjustForRequest(
                timeout.getDeadline() - startTime, currentTime - startTime);
        return timeoutFactory.create(currentTime, adjustedTmax);
    }

    private BidderResponse rejectBidderResponseOrProceed(HookStageExecutionResult<BidderResponsePayload> stageResult,
                                                         BidderResponse bidderResponse) {

        final List<BidderBid> bids = stageResult.isShouldReject()
                ? Collections.emptyList()
                : stageResult.getPayload().bids();

        return bidderResponse.with(bidderResponse.getSeatBid().with(bids));
    }

    private List<AuctionParticipation> dropZeroNonDealBids(List<AuctionParticipation> auctionParticipations,
                                                           List<String> debugWarnings,
                                                           boolean isDebugEnabled) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> dropZeroNonDealBids(auctionParticipation, debugWarnings, isDebugEnabled))
                .toList();
    }

    private AuctionParticipation dropZeroNonDealBids(AuctionParticipation auctionParticipation,
                                                     List<String> debugWarnings,
                                                     boolean isDebugEnabled) {

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final List<BidderBid> bidderBids = seatBid.getBids();
        final List<BidderBid> validBids = new ArrayList<>();

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            if (isZeroNonDealBids(bid.getPrice(), bid.getDealid())) {
                metrics.updateAdapterRequestErrorMetric(bidderResponse.getBidder(), MetricName.unknown_error);
                if (isDebugEnabled) {
                    debugWarnings.add(
                            "Dropped bid '%s'. Does not contain a positive (or zero if there is a deal) 'price'"
                            .formatted(bid.getId()));
                }
            } else {
                validBids.add(bidderBid);
            }
        }

        return validBids.size() != bidderBids.size()
                ? auctionParticipation.with(bidderResponse.with(seatBid.with(validBids)))
                : auctionParticipation;
    }

    private boolean isZeroNonDealBids(BigDecimal price, String dealId) {
        return price == null
                || price.compareTo(BigDecimal.ZERO) < 0
                || (price.compareTo(BigDecimal.ZERO) == 0 && StringUtils.isBlank(dealId));
    }

    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }

    private List<AuctionParticipation> updateResponsesMetrics(List<AuctionParticipation> auctionParticipations,
                                                              Account account,
                                                              BidderAliases aliases) {

        final List<BidderResponse> bidderResponses = auctionParticipations.stream()
                .filter(auctionParticipation -> !auctionParticipation.isRequestBlocked())
                .map(AuctionParticipation::getBidderResponse)
                .toList();

        for (BidderResponse bidderResponse : bidderResponses) {
            final String bidder = aliases.resolveBidder(bidderResponse.getBidder());

            metrics.updateAdapterResponseTime(bidder, account, bidderResponse.getResponseTime());

            final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
            if (CollectionUtils.isEmpty(bidderBids)) {
                metrics.updateAdapterRequestNobidMetrics(bidder, account);
            } else {
                metrics.updateAdapterRequestGotbidsMetrics(bidder, account);

                for (final BidderBid bidderBid : bidderBids) {
                    final Bid bid = bidderBid.getBid();
                    final long cpm = bid.getPrice().multiply(THOUSAND).longValue();
                    final String bidType = bidderBid.getType().toString();
                    metrics.updateAdapterBidMetrics(bidder, account, cpm, bid.getAdm() != null, bidType);
                }
            }

            final List<BidderError> errors = bidderResponse.getSeatBid().getErrors();
            if (CollectionUtils.isNotEmpty(errors)) {
                errors.stream()
                        .map(BidderError::getType)
                        .distinct()
                        .map(ExchangeService::bidderErrorTypeToMetric)
                        .forEach(errorMetric -> metrics.updateAdapterRequestErrorMetric(bidder, errorMetric));
            }
        }

        return auctionParticipations;
    }

    private Future<AuctionContext> invokeResponseHooks(AuctionContext auctionContext) {
        final BidResponse bidResponse = auctionContext.getBidResponse();
        return hookStageExecutor.executeAuctionResponseStage(bidResponse, auctionContext)
                .map(stageResult -> stageResult.getPayload().bidResponse())
                .map(auctionContext::with);
    }

    private static MetricName bidderErrorTypeToMetric(BidderError.Type errorType) {
        return switch (errorType) {
            case bad_input -> MetricName.badinput;
            case bad_server_response -> MetricName.badserverresponse;
            case failed_to_request_bids -> MetricName.failedtorequestbids;
            case timeout -> MetricName.timeout;
            case invalid_bid -> MetricName.bid_validation;
            case rejected_ipf, generic -> MetricName.unknown_error;
        };
    }
}
