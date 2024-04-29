package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.prebid.server.auction.adjustment.BidAdjustmentFactorResolver;
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
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.PriceFloorAdjuster;
import org.prebid.server.floors.PriceFloorEnforcer;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
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
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsActivity;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsAppliedTo;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceAnalyticsTags;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceGroup;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceInvocationResult;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStage;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTraceStageOutcome;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.StreamUtil;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes an OpenRTB v2.5-2.6 Auction.
 */
public class ExchangeService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final String TID_EXT = "tid";
    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";
    private static final String ALL_BIDDERS_CONFIG = "*";
    private static final Integer DEFAULT_MULTIBID_LIMIT_MIN = 1;
    private static final Integer DEFAULT_MULTIBID_LIMIT_MAX = 9;
    private static final String EID_ALLOWED_FOR_ALL_BIDDERS = "*";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final double logSamplingRate;
    private final BidderCatalog bidderCatalog;
    private final StoredResponseProcessor storedResponseProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final FpdResolver fpdResolver;
    private final SupplyChainResolver supplyChainResolver;
    private final DebugResolver debugResolver;
    private final MediaTypeProcessor mediaTypeProcessor;
    private final UidUpdater uidUpdater;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    private final HttpBidderRequester httpBidderRequester;
    private final ResponseBidValidator responseBidValidator;
    private final CurrencyConversionService currencyService;
    private final BidResponseCreator bidResponseCreator;
    private final BidResponsePostProcessor bidResponsePostProcessor;
    private final HookStageExecutor hookStageExecutor;
    private final HttpInteractionLogger httpInteractionLogger;
    private final PriceFloorAdjuster priceFloorAdjuster;
    private final PriceFloorEnforcer priceFloorEnforcer;
    private final DsaEnforcer dsaEnforcer;
    private final BidAdjustmentFactorResolver bidAdjustmentFactorResolver;
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
                           SupplyChainResolver supplyChainResolver,
                           DebugResolver debugResolver,
                           MediaTypeProcessor mediaTypeProcessor,
                           UidUpdater uidUpdater,
                           TimeoutResolver timeoutResolver,
                           TimeoutFactory timeoutFactory,
                           BidRequestOrtbVersionConversionManager ortbVersionConversionManager,
                           HttpBidderRequester httpBidderRequester,
                           ResponseBidValidator responseBidValidator,
                           CurrencyConversionService currencyService,
                           BidResponseCreator bidResponseCreator,
                           BidResponsePostProcessor bidResponsePostProcessor,
                           HookStageExecutor hookStageExecutor,
                           HttpInteractionLogger httpInteractionLogger,
                           PriceFloorAdjuster priceFloorAdjuster,
                           PriceFloorEnforcer priceFloorEnforcer,
                           DsaEnforcer dsaEnforcer,
                           BidAdjustmentFactorResolver bidAdjustmentFactorResolver,
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
        this.supplyChainResolver = Objects.requireNonNull(supplyChainResolver);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.mediaTypeProcessor = Objects.requireNonNull(mediaTypeProcessor);
        this.uidUpdater = Objects.requireNonNull(uidUpdater);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.ortbVersionConversionManager = Objects.requireNonNull(ortbVersionConversionManager);
        this.httpBidderRequester = Objects.requireNonNull(httpBidderRequester);
        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidResponseCreator = Objects.requireNonNull(bidResponseCreator);
        this.bidResponsePostProcessor = Objects.requireNonNull(bidResponsePostProcessor);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.priceFloorAdjuster = Objects.requireNonNull(priceFloorAdjuster);
        this.priceFloorEnforcer = Objects.requireNonNull(priceFloorEnforcer);
        this.dsaEnforcer = Objects.requireNonNull(dsaEnforcer);
        this.bidAdjustmentFactorResolver = Objects.requireNonNull(bidAdjustmentFactorResolver);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);
        this.enabledStrictAppSiteDoohValidation = enabledStrictAppSiteDoohValidation;
    }

    /**
     * Runs an auction: delegates request to applicable bidders, gathers responses from them and constructs final
     * response containing returned bids and additional information in extensions.
     */
    public Future<AuctionContext> holdAuction(AuctionContext context) {
        return processAuctionRequest(context)
                .compose(this::invokeResponseHooks)
                .map(this::enrichWithHooksDebugInfo)
                .map(this::updateHooksMetrics);
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
        final BidderAliases aliases = aliases(bidRequest);
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(bidRequest);
        final Map<String, MultiBidConfig> bidderToMultiBid = bidderToMultiBids(bidRequest, debugWarnings);
        receivedContext.getBidRejectionTrackers().putAll(makeBidRejectionTrackers(bidRequest, aliases));

        return storedResponseProcessor.getStoredResponseResult(bidRequest.getImp(), timeout)
                .map(storedResponseResult -> populateStoredResponse(storedResponseResult, storedAuctionResponses))
                .compose(storedResponseResult -> extractAuctionParticipations(
                        receivedContext, storedResponseResult, aliases, bidderToMultiBid)
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
                                auctionParticipations, storedAuctionResponses, bidRequest.getImp()))
                        .map(auctionParticipations -> dropZeroNonDealBids(auctionParticipations, debugWarnings))
                        .map(auctionParticipations -> validateAndAdjustBids(auctionParticipations, context, aliases))
                        .map(auctionParticipations -> updateResponsesMetrics(auctionParticipations, account, aliases))
                        .map(context::with))
                // produce response from bidder results
                .compose(context -> bidResponseCreator.create(context, cacheInfo, bidderToMultiBid)
                        .map(bidResponse -> criteriaLogManager.traceResponse(
                                logger,
                                bidResponse,
                                context.getBidRequest(),
                                context.getDebugContext().isDebugEnabled()))
                        .compose(bidResponse -> bidResponsePostProcessor.postProcess(
                                context.getHttpRequest(), uidsCookie, bidRequest, bidResponse, account))
                        .map(context::with));
    }

    private BidderAliases aliases(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final Map<String, String> aliases = prebid != null ? prebid.getAliases() : null;
        final Map<String, Integer> aliasgvlids = prebid != null ? prebid.getAliasgvlids() : null;
        return BidderAliases.of(aliases, aliasgvlids, bidderCatalog);
    }

    private static ExtRequestTargeting targeting(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        return prebid != null ? prebid.getTargeting() : null;
    }

    /**
     * Creates {@link BidRequestCacheInfo} based on {@link BidRequest} model.
     */
    private static BidRequestCacheInfo bidRequestCacheInfo(BidRequest bidRequest) {
        final ExtRequestTargeting targeting = targeting(bidRequest);
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
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

    private static ExtRequestPrebid extRequestPrebid(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        return requestExt != null ? requestExt.getPrebid() : null;
    }

    private static Map<String, MultiBidConfig> bidderToMultiBids(BidRequest bidRequest, List<String> debugWarnings) {
        final ExtRequestPrebid extRequestPrebid = extRequestPrebid(bidRequest);
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
                debugWarnings.add(
                        "Invalid MultiBid: bidder %s and bidders %s specified. Only bidder %s will be used."
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

    /**
     * Populates storedResponse parameter with stored {@link List<SeatBid>} and returns {@link List<Imp>} for which
     * request to bidders should be performed.
     */
    private static StoredResponseResult populateStoredResponse(StoredResponseResult storedResponseResult,
                                                               List<SeatBid> storedResponse) {
        storedResponse.addAll(storedResponseResult.getAuctionStoredResponse());
        return storedResponseResult;
    }

    /**
     * Takes an OpenRTB request and returns the OpenRTB requests sanitized for each bidder.
     * <p>
     * This will copy the {@link BidRequest} into a list of requests, where the bidRequest.imp[].ext field
     * will only consist of the "prebid" field and the field for the appropriate bidder parameters. We will drop all
     * extended fields beyond this context, so this will not be compatible with any other uses of the extension area
     * i.e. the bidders will not see any other extension fields. If Imp extension name is alias, which is also defined
     * in bidRequest.ext.prebid.aliases and valid, separate {@link BidRequest} will be created for this alias and sent
     * to appropriate bidder.
     * For example suppose {@link BidRequest} has two {@link Imp}s. First one with imp.ext.prebid.bidder.rubicon and
     * imp.ext.prebid.bidder.rubiconAlias and second with imp.ext.prebid.bidder.appnexus and
     * imp.ext.prebid.bidder.rubicon. Three {@link BidRequest}s will be created:
     * 1. {@link BidRequest} with one {@link Imp}, where bidder extension points to rubiconAlias extension and will be
     * sent to Rubicon bidder.
     * 2. {@link BidRequest} with two {@link Imp}s, where bidder extension points to appropriate rubicon extension from
     * original {@link BidRequest} and will be sent to Rubicon bidder.
     * 3. {@link BidRequest} with one {@link Imp}, where bidder extension points to appnexus extension and will be sent
     * to Appnexus bidder.
     * <p>
     * Each of the created {@link BidRequest}s will have bidrequest.user.buyerid field populated with the value from
     * bidrequest.user.ext.prebid.buyerids or {@link UidsCookie} corresponding to bidder's family name unless buyerid
     * is already in the original OpenRTB request (in this case it will not be overridden).
     * In case if bidrequest.user.ext.prebid.buyerids contains values after extracting those values it will be cleared
     * in order to avoid leaking of buyerids across bidders.
     * <p>
     * NOTE: the return list will only contain entries for bidders that both have the extension field in at least one
     * {@link Imp}, and are known to {@link BidderCatalog} or aliases from bidRequest.ext.prebid.aliases.
     */
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

    /**
     * Checks if bidder name is valid in case when bidder can also be alias name.
     */
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

    /**
     * Splits the input request into requests which are sanitized for each bidder. Intended behavior is:
     * <p>
     * - bidrequest.imp[].ext will only contain the "prebid" field and a "bidder" field which has the params for
     * the intended Bidder.
     * <p>
     * - bidrequest.user.buyeruid will be set to that Bidder's ID.
     * <p>
     * - bidrequest.ext.prebid.data.bidders will be removed.
     * <p>
     * - bidrequest.ext.prebid.bidders will be staying in corresponding bidder only.
     * <p>
     * - bidrequest.user.ext.data, bidrequest.app.ext.data, bidrequest.dooh.ext.data and bidrequest.site.ext.data
     * will be removed for bidders that don't have first party data allowed.
     */
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

    /**
     * Retrieves user eids from {@link ExtRequestPrebid} and converts them to map, where keys are eids sources
     * and values are allowed bidders
     */
    private Map<String, List<String>> getEidPermissions(ExtRequestPrebid prebid) {
        final ExtRequestPrebidData prebidData = prebid != null ? prebid.getData() : null;
        final List<ExtRequestPrebidDataEidPermissions> eidPermissions = prebidData != null
                ? prebidData.getEidPermissions()
                : null;
        return CollectionUtils.emptyIfNull(eidPermissions).stream()
                .collect(Collectors.toMap(ExtRequestPrebidDataEidPermissions::getSource,
                        ExtRequestPrebidDataEidPermissions::getBidders));
    }

    /**
     * Extracts a list of bidders for which first party data is allowed from {@link ExtRequestPrebidData} model.
     */
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

    /**
     * Returns original {@link User} if user.buyeruid already contains uid value for bidder.
     * Otherwise, returns new {@link User} containing updated {@link ExtUser} and user.buyeruid.
     * <p>
     * Also, removes user.ext.prebid (if present), user.ext.data and user.data (in case bidder does not use first
     * party data).
     */
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
                userBuilder.eids(nullIfEmpty(allowedUserEids));
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

    /**
     * Returns {@link List<Eid>} allowed by {@param eidPermissions} per source per bidder.
     */
    private List<Eid> resolveAllowedEids(List<Eid> userEids, String bidder, Map<String, List<String>> eidPermissions) {
        return CollectionUtils.emptyIfNull(userEids)
                .stream()
                .filter(userEid -> isUserEidAllowed(userEid.getSource(), eidPermissions, bidder))
                .toList();
    }

    /**
     * Returns true if {@param source} allowed by {@param eidPermissions} for particular bidder taking into account
     * ealiases.
     */
    private boolean isUserEidAllowed(String source, Map<String, List<String>> eidPermissions, String bidder) {
        final List<String> allowedBidders = eidPermissions.get(source);
        return CollectionUtils.isEmpty(allowedBidders) || allowedBidders.stream()
                .anyMatch(allowedBidder -> StringUtils.equalsIgnoreCase(allowedBidder, bidder)
                        || EID_ALLOWED_FOR_ALL_BIDDERS.equals(allowedBidder));
    }

    /**
     * Returns shuffled list of {@link AuctionParticipation} with {@link BidRequest}.
     */
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
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
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

    /**
     * Returns {@link AuctionParticipation} for the given bidder.
     */
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
                    .rejectAll(BidRejectionReason.REJECTED_BY_PRIVACY);

            return AuctionParticipation.builder()
                    .bidder(bidder)
                    .requestBlocked(true)
                    .analyticsBlocked(blockedAnalyticsByTcf)
                    .build();
        }

        final OrtbVersion ortbVersion = bidderSupportedOrtbVersion(bidder, bidderAliases);
        // stored bid response supported only for single imp requests
        final String storedBidResponse = impBidderToStoredBidResponse.size() == 1
                ? impBidderToStoredBidResponse.get(imps.get(0).getId()).get(bidder)
                : null;
        final BidRequest preparedBidRequest = prepareBidRequest(
                bidderPrivacyResult,
                imps,
                bidderToMultiBid,
                biddersToConfigs,
                bidderToPrebidBidders,
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
                                         AuctionContext context) {

        final BidRequest bidRequest = context.getBidRequest();
        final String bidder = bidderPrivacyResult.getRequestBidder();
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

        return bidRequest.toBuilder()
                // User was already prepared above
                .user(bidderPrivacyResult.getUser())
                .device(bidderPrivacyResult.getDevice())
                .imp(prepareImps(bidder, imps, bidRequest, transmitTid, useFirstPartyData, context.getAccount()))
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

    /**
     * For each given imp creates a new imp with extension crafted to contain only "prebid", "context" and
     * bidder-specific extension.
     */
    private List<Imp> prepareImps(String bidder,
                                  List<Imp> imps,
                                  BidRequest bidRequest,
                                  boolean transmitTid,
                                  boolean useFirstPartyData,
                                  Account account) {

        return imps.stream()
                .filter(imp -> bidderParamsFromImpExt(imp.getExt()).hasNonNull(bidder))
                .map(imp -> prepareImp(imp, bidder, bidRequest, transmitTid, useFirstPartyData, account))
                .toList();
    }

    private Imp prepareImp(Imp imp,
                           String bidder,
                           BidRequest bidRequest,
                           boolean transmitTid,
                           boolean useFirstPartyData,
                           Account account) {

        final BigDecimal adjustedFloor = resolveBidFloor(imp, bidder, bidRequest, account);
        return imp.toBuilder()
                .bidfloor(adjustedFloor)
                .ext(prepareImpExt(bidder, imp.getExt(), adjustedFloor, transmitTid, useFirstPartyData))
                .build();
    }

    /**
     * @return Bidfloor divided by factor from {@link PriceFloorAdjuster}
     */
    private BigDecimal resolveBidFloor(Imp imp, String bidder, BidRequest bidRequest, Account account) {
        return priceFloorAdjuster.adjustForImp(imp, bidder, bidRequest, account);
    }

    /**
     * Creates a new imp extension for particular bidder having:
     * <ul>
     * <li>"prebid" field populated with an imp.ext.prebid field value, may be null</li>
     * <li>"bidder" field populated with an imp.ext.prebid.bidder.{bidder} field value, not null</li>
     * <li>"context" field populated with an imp.ext.context field value, may be null</li>
     * <li>"data" field populated with an imp.ext.data field value, may be null</li>
     * </ul>
     */
    private ObjectNode prepareImpExt(String bidder,
                                     ObjectNode impExt,
                                     BigDecimal adjustedFloor,
                                     boolean transmitTid,
                                     boolean useFirstPartyData) {

        final ObjectNode modifiedImpExt = impExt.deepCopy();
        final JsonNode impExtPrebid = prepareImpExt(impExt.get(PREBID_EXT), adjustedFloor);
        Optional.ofNullable(impExtPrebid).ifPresentOrElse(
                ext -> modifiedImpExt.set(PREBID_EXT, ext),
                () -> modifiedImpExt.remove(PREBID_EXT));
        modifiedImpExt.set(BIDDER_EXT, bidderParamsFromImpExt(impExt).get(bidder));
        if (!transmitTid) {
            modifiedImpExt.remove(TID_EXT);
        }

        return fpdResolver.resolveImpExt(modifiedImpExt, useFirstPartyData);
    }

    private JsonNode prepareImpExt(JsonNode extImpPrebidNode, BigDecimal adjustedFloor) {
        if (extImpPrebidNode.size() <= 1) {
            return null;
        }

        final ExtImpPrebid extImpPrebid = extImpPrebid(extImpPrebidNode);
        final ExtImpPrebidFloors floors = extImpPrebid.getFloors();
        final ExtImpPrebidFloors updatedFloors = floors != null
                ? ExtImpPrebidFloors.of(floors.getFloorRule(),
                floors.getFloorRuleValue(),
                adjustedFloor,
                floors.getFloorMin(),
                floors.getFloorMinCur())
                : null;

        return mapper.mapper().valueToTree(extImpPrebid(extImpPrebidNode).toBuilder()
                .floors(updatedFloors)
                .bidder(null)
                .build());
    }

    /**
     * Returns {@link ExtImpPrebid} from imp.ext.prebid {@link JsonNode}.
     */
    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return mapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether to pass the app.ext.data and app.content.data depending on request having a first party data
     * allowed for given bidder or not. And merge masked app with fpd config.
     */
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

    /**
     * Checks whether to pass the site.ext.data  and site.content.data depending on request having a first party data
     * allowed for given bidder or not. And merge masked site with fpd config.
     */
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

    /**
     * Checks whether to pass the dooh.ext.data and dooh.content.data depending on request having a first party data
     * allowed for given bidder or not. And merge masked dooh with fpd config.
     */
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

    /**
     * Returns {@link Source} with corresponding request.ext.prebid.schains.
     */
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

    /**
     * Removes all bidders except the given bidder from bidrequest.ext.prebid.bidders to hide list of allowed bidders
     * from initial request.
     * <p>
     * Also masks bidrequest.ext.prebid.schains.
     */
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

    /**
     * Prepares parameters for specified bidder removing parameters for all other bidders.
     * Returns null if there are no parameters for specified bidder.
     */
    private ObjectNode prepareBidderParameters(ExtRequestPrebid prebid, String bidder) {
        final ObjectNode bidderParams = prebid != null ? prebid.getBidderparams() : null;
        final JsonNode params = bidderParams != null ? bidderParams.get(bidder) : null;
        return params != null ? mapper.mapper().createObjectNode().set(bidder, params) : null;
    }

    /**
     * Updates 'account.*.request', 'request' and 'no_cookie_requests' metrics for each {@link AuctionParticipation} .
     */
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
            auctionContext.getBidRejectionTrackers()
                    .get(bidderName)
                    .rejectAll(BidRejectionReason.REJECTED_BY_MEDIA_TYPE);
            final BidderSeatBid bidderSeatBid = BidderSeatBid.builder()
                    .warnings(mediaTypeProcessingErrors)
                    .build();
            return Future.succeededFuture(BidderResponse.of(bidderName, bidderSeatBid, 0));
        }

        return Future.succeededFuture(mediaTypeProcessingResult.getBidRequest())
                .map(bidderRequest::with)
                .compose(modifiedBidderRequest -> invokeHooksAndRequestBids(
                        auctionContext, modifiedBidderRequest, timeout, aliases))
                .map(bidderResponse -> bidderResponse.with(
                        addWarnings(bidderResponse.getSeatBid(), mediaTypeProcessingErrors)));
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
                    .rejectAll(BidRejectionReason.REJECTED_BY_HOOK);

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
        final BidRejectionTracker bidRejectionTracker = auctionContext.getBidRejectionTrackers().get(bidderName);

        final TimeoutContext timeoutContext = auctionContext.getTimeoutContext();
        final long auctionStartTime = timeoutContext.getStartTime();
        final int adjustmentFactor = timeoutContext.getAdjustmentFactor();
        final long bidderRequestStartTime = clock.millis();

        return Future.succeededFuture(bidderRequest.getBidRequest())
                .map(bidRequest -> adjustTmax(bidRequest, auctionStartTime, adjustmentFactor, bidderRequestStartTime))
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
                .map(seatBid -> BidderResponse.of(bidderName, seatBid, responseTime(bidderRequestStartTime)));
    }

    private BidRequest adjustTmax(BidRequest bidRequest, long startTime, int adjustmentFactor, long currentTime) {
        final long tmax = timeoutResolver.limitToMax(bidRequest.getTmax());
        final long adjustedTmax = timeoutResolver.adjustForBidder(tmax, adjustmentFactor, currentTime - startTime);
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
                                                           List<String> debugWarnings) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> dropZeroNonDealBids(auctionParticipation, debugWarnings))
                .toList();
    }

    private AuctionParticipation dropZeroNonDealBids(AuctionParticipation auctionParticipation,
                                                     List<String> debugWarnings) {
        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final List<BidderBid> bidderBids = seatBid.getBids();
        final List<BidderBid> validBids = new ArrayList<>();

        for (BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            if (isZeroNonDealBids(bid.getPrice(), bid.getDealid())) {
                metrics.updateAdapterRequestErrorMetric(bidderResponse.getBidder(), MetricName.unknown_error);
                debugWarnings.add("Dropped bid '%s'. Does not contain a positive (or zero if there is a deal) 'price'"
                        .formatted(bid.getId()));
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

    private List<AuctionParticipation> validateAndAdjustBids(List<AuctionParticipation> auctionParticipations,
                                                             AuctionContext auctionContext,
                                                             BidderAliases aliases) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> validBidderResponse(auctionParticipation, auctionContext, aliases))
                .map(auctionParticipation -> applyBidPriceChanges(auctionParticipation, auctionContext.getBidRequest()))
                .map(auctionParticipation -> priceFloorEnforcer.enforce(
                        auctionContext.getBidRequest(),
                        auctionParticipation,
                        auctionContext.getAccount(),
                        auctionContext.getBidRejectionTrackers().get(auctionParticipation.getBidder())))
                .map(auctionParticipation -> dsaEnforcer.enforce(
                        auctionContext.getBidRequest(),
                        auctionParticipation,
                        auctionContext.getBidRejectionTrackers().get(auctionParticipation.getBidder())))
                .toList();
    }

    /**
     * Validates bid response from exchange.
     * <p>
     * Removes invalid bids from response and adds corresponding error to {@link BidderSeatBid}.
     * <p>
     * Returns input argument as the result if no errors found or creates new {@link BidderResponse} otherwise.
     */
    private AuctionParticipation validBidderResponse(AuctionParticipation auctionParticipation,
                                                     AuctionContext auctionContext,
                                                     BidderAliases aliases) {

        if (auctionParticipation.isRequestBlocked()) {
            return auctionParticipation;
        }

        final BidRequest bidRequest = auctionContext.getBidRequest();
        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final List<BidderError> warnings = new ArrayList<>(seatBid.getWarnings());

        final List<String> requestCurrencies = bidRequest.getCur();
        if (requestCurrencies.size() > 1) {
            errors.add(BidderError.badInput("Cur parameter contains more than one currency. %s will be used"
                    .formatted(requestCurrencies.get(0))));
        }

        final List<BidderBid> bids = seatBid.getBids();
        final List<BidderBid> validBids = new ArrayList<>(bids.size());

        for (final BidderBid bid : bids) {
            final ValidationResult validationResult = responseBidValidator.validate(
                    bid,
                    bidderResponse.getBidder(),
                    auctionContext,
                    aliases);

            if (validationResult.hasWarnings() || validationResult.hasErrors()) {
                errors.add(makeValidationBidderError(bid.getBid(), validationResult));
            }

            if (!validationResult.hasErrors()) {
                validBids.add(bid);
            }
        }

        final BidderResponse resultBidderResponse = errors.size() == seatBid.getErrors().size()
                ? bidderResponse
                : bidderResponse.with(
                seatBid.toBuilder()
                        .bids(validBids)
                        .errors(errors)
                        .warnings(warnings)
                        .build());
        return auctionParticipation.with(resultBidderResponse);
    }

    private BidderError makeValidationBidderError(Bid bid, ValidationResult validationResult) {
        final String validationErrors = Stream.concat(
                        validationResult.getErrors().stream().map(message -> "Error: " + message),
                        validationResult.getWarnings().stream().map(message -> "Warning: " + message))
                .collect(Collectors.joining(". "));

        final String bidId = ObjectUtil.getIfNotNullOrDefault(bid, Bid::getId, () -> "unknown");
        return BidderError.invalidBid("BidId `" + bidId + "` validation messages: " + validationErrors);
    }

    /**
     * Performs changes on {@link Bid}s price depends on different between adServerCurrency and bidCurrency,
     * and adjustment factor. Will drop bid if currency conversion is needed but not possible.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderResponse} to make sure
     * {@link Bid#getPrice()} is not empty.
     */
    private AuctionParticipation applyBidPriceChanges(AuctionParticipation auctionParticipation,
                                                      BidRequest bidRequest) {
        if (auctionParticipation.isRequestBlocked()) {
            return auctionParticipation;
        }

        final BidderResponse bidderResponse = auctionParticipation.getBidderResponse();
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();

        final List<BidderBid> bidderBids = seatBid.getBids();
        if (bidderBids.isEmpty()) {
            return auctionParticipation;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids.size());
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());
        final String adServerCurrency = bidRequest.getCur().get(0);

        for (final BidderBid bidderBid : bidderBids) {
            try {
                final BidderBid updatedBidderBid =
                        updateBidderBidWithBidPriceChanges(bidderBid, bidderResponse, bidRequest, adServerCurrency);
                updatedBidderBids.add(updatedBidderBid);
            } catch (PreBidException e) {
                errors.add(BidderError.generic(e.getMessage()));
            }
        }

        final BidderResponse resultBidderResponse = bidderResponse.with(seatBid.toBuilder()
                .bids(updatedBidderBids)
                .errors(errors)
                .build());
        return auctionParticipation.with(resultBidderResponse);
    }

    private BidderBid updateBidderBidWithBidPriceChanges(BidderBid bidderBid,
                                                         BidderResponse bidderResponse,
                                                         BidRequest bidRequest,
                                                         String adServerCurrency) {
        final Bid bid = bidderBid.getBid();
        final String bidCurrency = bidderBid.getBidCurrency();
        final BigDecimal price = bid.getPrice();

        final BigDecimal priceInAdServerCurrency = currencyService.convertCurrency(
                price, bidRequest, StringUtils.stripToNull(bidCurrency), adServerCurrency);

        final BigDecimal priceAdjustmentFactor =
                bidAdjustmentForBidder(bidderResponse.getBidder(), bidRequest, bidderBid);
        final BigDecimal adjustedPrice = adjustPrice(priceAdjustmentFactor, priceInAdServerCurrency);

        final ObjectNode bidExt = bid.getExt();
        final ObjectNode updatedBidExt = bidExt != null ? bidExt : mapper.mapper().createObjectNode();

        updateExtWithOrigPriceValues(updatedBidExt, price, bidCurrency);

        final Bid.BidBuilder bidBuilder = bid.toBuilder();
        if (adjustedPrice.compareTo(price) != 0) {
            bidBuilder.price(adjustedPrice);
        }

        if (!updatedBidExt.isEmpty()) {
            bidBuilder.ext(updatedBidExt);
        }

        return bidderBid.toBuilder().bid(bidBuilder.build()).build();
    }

    private BigDecimal bidAdjustmentForBidder(String bidder, BidRequest bidRequest, BidderBid bidderBid) {
        final ExtRequestBidAdjustmentFactors adjustmentFactors = extBidAdjustmentFactors(bidRequest);
        if (adjustmentFactors == null) {
            return null;
        }
        final ImpMediaType mediaType = ImpMediaTypeResolver.resolve(
                bidderBid.getBid().getImpid(), bidRequest.getImp(), bidderBid.getType());

        return bidAdjustmentFactorResolver.resolve(mediaType, adjustmentFactors, bidder);
    }

    private static ExtRequestBidAdjustmentFactors extBidAdjustmentFactors(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        return prebid != null ? prebid.getBidadjustmentfactors() : null;
    }

    private static BigDecimal adjustPrice(BigDecimal priceAdjustmentFactor, BigDecimal price) {
        return priceAdjustmentFactor != null && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                ? price.multiply(priceAdjustmentFactor)
                : price;
    }

    private static void updateExtWithOrigPriceValues(ObjectNode updatedBidExt, BigDecimal price, String bidCurrency) {
        addPropertyToNode(updatedBidExt, ORIGINAL_BID_CPM, new DecimalNode(price));
        if (StringUtils.isNotBlank(bidCurrency)) {
            addPropertyToNode(updatedBidExt, ORIGINAL_BID_CURRENCY, new TextNode(bidCurrency));
        }
    }

    private static void addPropertyToNode(ObjectNode node, String propertyName, JsonNode propertyValue) {
        node.set(propertyName, propertyValue);
    }

    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }

    /**
     * Updates 'request_time', 'responseTime', 'timeout_request', 'error_requests', 'no_bid_requests',
     * 'prices' metrics for each {@link AuctionParticipation}.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderResponse} to make sure
     * {@link Bid#getPrice()} is not empty.
     */
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

    /**
     * Resolves {@link MetricName} by {@link BidderError.Type} value.
     */
    private static MetricName bidderErrorTypeToMetric(BidderError.Type errorType) {
        return switch (errorType) {
            case bad_input -> MetricName.badinput;
            case bad_server_response -> MetricName.badserverresponse;
            case failed_to_request_bids -> MetricName.failedtorequestbids;
            case timeout -> MetricName.timeout;
            case invalid_bid -> MetricName.bid_validation;
            case rejected_ipf, generic, invalid_creative -> MetricName.unknown_error;
        };
    }

    private AuctionContext enrichWithHooksDebugInfo(AuctionContext context) {
        final ExtModules extModules = toExtModules(context);

        if (extModules == null) {
            return context;
        }

        final BidResponse bidResponse = context.getBidResponse();
        final Optional<ExtBidResponse> ext = Optional.ofNullable(bidResponse.getExt());
        final Optional<ExtBidResponsePrebid> extPrebid = ext.map(ExtBidResponse::getPrebid);

        final ExtBidResponsePrebid updatedExtPrebid = extPrebid
                .map(ExtBidResponsePrebid::toBuilder)
                .orElse(ExtBidResponsePrebid.builder())
                .modules(extModules)
                .build();

        final ExtBidResponse updatedExt = ext
                .map(ExtBidResponse::toBuilder)
                .orElse(ExtBidResponse.builder())
                .prebid(updatedExtPrebid)
                .build();

        final BidResponse updatedBidResponse = bidResponse.toBuilder().ext(updatedExt).build();
        return context.with(updatedBidResponse);
    }

    private static ExtModules toExtModules(AuctionContext context) {
        final Map<String, Map<String, List<String>>> errors =
                toHookMessages(context, HookExecutionOutcome::getErrors);
        final Map<String, Map<String, List<String>>> warnings =
                toHookMessages(context, HookExecutionOutcome::getWarnings);
        final ExtModulesTrace trace = toHookTrace(context);
        return ObjectUtils.anyNotNull(errors, warnings, trace) ? ExtModules.of(errors, warnings, trace) : null;
    }

    private static Map<String, Map<String, List<String>>> toHookMessages(
            AuctionContext context,
            Function<HookExecutionOutcome, List<String>> messagesGetter) {

        if (!context.getDebugContext().isDebugEnabled()) {
            return null;
        }

        final Map<String, List<HookExecutionOutcome>> hookOutcomesByModule =
                context.getHookExecutionContext().getStageOutcomes().values().stream()
                        .flatMap(Collection::stream)
                        .flatMap(stageOutcome -> stageOutcome.getGroups().stream())
                        .flatMap(groupOutcome -> groupOutcome.getHooks().stream())
                        .filter(hookOutcome -> CollectionUtils.isNotEmpty(messagesGetter.apply(hookOutcome)))
                        .collect(Collectors.groupingBy(
                                hookOutcome -> hookOutcome.getHookId().getModuleCode()));

        final Map<String, Map<String, List<String>>> messagesByModule = hookOutcomesByModule.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        outcomes -> outcomes.getValue().stream()
                                .collect(Collectors.groupingBy(
                                        hookOutcome -> hookOutcome.getHookId().getHookImplCode()))
                                .entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        messagesLists -> messagesLists.getValue().stream()
                                                .map(messagesGetter)
                                                .flatMap(Collection::stream)
                                                .toList()))));

        return !messagesByModule.isEmpty() ? messagesByModule : null;
    }

    private static ExtModulesTrace toHookTrace(AuctionContext context) {
        final TraceLevel traceLevel = context.getDebugContext().getTraceLevel();

        if (traceLevel == null) {
            return null;
        }

        final List<ExtModulesTraceStage> stages = context.getHookExecutionContext().getStageOutcomes()
                .entrySet().stream()
                .map(stageOutcome -> toTraceStage(stageOutcome.getKey(), stageOutcome.getValue(), traceLevel))
                .filter(Objects::nonNull)
                .toList();

        if (stages.isEmpty()) {
            return null;
        }

        final long executionTime = stages.stream().mapToLong(ExtModulesTraceStage::getExecutionTime).sum();
        return ExtModulesTrace.of(executionTime, stages);
    }

    private static ExtModulesTraceStage toTraceStage(Stage stage,
                                                     List<StageExecutionOutcome> stageOutcomes,
                                                     TraceLevel level) {

        final List<ExtModulesTraceStageOutcome> extStageOutcomes = stageOutcomes.stream()
                .map(stageOutcome -> toTraceStageOutcome(stageOutcome, level))
                .filter(Objects::nonNull)
                .toList();

        if (extStageOutcomes.isEmpty()) {
            return null;
        }

        final long executionTime = extStageOutcomes.stream()
                .mapToLong(ExtModulesTraceStageOutcome::getExecutionTime)
                .max()
                .orElse(0L);

        return ExtModulesTraceStage.of(stage, executionTime, extStageOutcomes);
    }

    private static ExtModulesTraceStageOutcome toTraceStageOutcome(
            StageExecutionOutcome stageOutcome, TraceLevel level) {

        final List<ExtModulesTraceGroup> groups = stageOutcome.getGroups().stream()
                .map(group -> toTraceGroup(group, level))
                .toList();

        if (groups.isEmpty()) {
            return null;
        }

        final long executionTime = groups.stream().mapToLong(ExtModulesTraceGroup::getExecutionTime).sum();
        return ExtModulesTraceStageOutcome.of(stageOutcome.getEntity(), executionTime, groups);
    }

    private static ExtModulesTraceGroup toTraceGroup(GroupExecutionOutcome group, TraceLevel level) {
        final List<ExtModulesTraceInvocationResult> invocationResults = group.getHooks().stream()
                .map(hook -> toTraceInvocationResult(hook, level))
                .toList();

        final long executionTime = invocationResults.stream()
                .mapToLong(ExtModulesTraceInvocationResult::getExecutionTime)
                .max()
                .orElse(0L);

        return ExtModulesTraceGroup.of(executionTime, invocationResults);
    }

    private static ExtModulesTraceInvocationResult toTraceInvocationResult(HookExecutionOutcome hook,
                                                                           TraceLevel level) {
        return ExtModulesTraceInvocationResult.builder()
                .hookId(hook.getHookId())
                .executionTime(hook.getExecutionTime())
                .status(hook.getStatus())
                .message(hook.getMessage())
                .action(hook.getAction())
                .debugMessages(level == TraceLevel.verbose ? hook.getDebugMessages() : null)
                .analyticsTags(level == TraceLevel.verbose ? toTraceAnalyticsTags(hook.getAnalyticsTags()) : null)
                .build();
    }

    private static ExtModulesTraceAnalyticsTags toTraceAnalyticsTags(Tags analyticsTags) {
        if (analyticsTags == null) {
            return null;
        }

        return ExtModulesTraceAnalyticsTags.of(CollectionUtils.emptyIfNull(analyticsTags.activities()).stream()
                .filter(Objects::nonNull)
                .map(ExchangeService::toTraceAnalyticsActivity)
                .toList());
    }

    private static ExtModulesTraceAnalyticsActivity toTraceAnalyticsActivity(
            org.prebid.server.hooks.v1.analytics.Activity activity) {

        return ExtModulesTraceAnalyticsActivity.of(
                activity.name(),
                activity.status(),
                CollectionUtils.emptyIfNull(activity.results()).stream()
                        .filter(Objects::nonNull)
                        .map(ExchangeService::toTraceAnalyticsResult)
                        .toList());
    }

    private static ExtModulesTraceAnalyticsResult toTraceAnalyticsResult(Result result) {
        final AppliedTo appliedTo = result.appliedTo();
        final ExtModulesTraceAnalyticsAppliedTo extAppliedTo = appliedTo != null
                ? ExtModulesTraceAnalyticsAppliedTo.builder()
                .impIds(appliedTo.impIds())
                .bidders(appliedTo.bidders())
                .request(appliedTo.request() ? Boolean.TRUE : null)
                .response(appliedTo.response() ? Boolean.TRUE : null)
                .bidIds(appliedTo.bidIds())
                .build()
                : null;

        return ExtModulesTraceAnalyticsResult.of(result.status(), result.values(), extAppliedTo);
    }

    private AuctionContext updateHooksMetrics(AuctionContext context) {
        final EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes =
                context.getHookExecutionContext().getStageOutcomes();

        final Account account = context.getAccount();

        stageOutcomes.forEach((stage, outcomes) -> updateHooksStageMetrics(account, stage, outcomes));

        // account might be null if request is rejected by the entrypoint hook
        if (account != null) {
            stageOutcomes.values().stream()
                    .flatMap(Collection::stream)
                    .map(StageExecutionOutcome::getGroups)
                    .flatMap(Collection::stream)
                    .map(GroupExecutionOutcome::getHooks)
                    .flatMap(Collection::stream)
                    .collect(Collectors.groupingBy(
                            outcome -> outcome.getHookId().getModuleCode(),
                            Collectors.summingLong(HookExecutionOutcome::getExecutionTime)))
                    .forEach((moduleCode, executionTime) ->
                            metrics.updateAccountModuleDurationMetric(account, moduleCode, executionTime));
        }

        return context;
    }

    private void updateHooksStageMetrics(Account account, Stage stage, List<StageExecutionOutcome> stageOutcomes) {
        stageOutcomes.stream()
                .flatMap(stageOutcome -> stageOutcome.getGroups().stream())
                .flatMap(groupOutcome -> groupOutcome.getHooks().stream())
                .forEach(hookOutcome -> updateHookInvocationMetrics(account, stage, hookOutcome));
    }

    private void updateHookInvocationMetrics(Account account, Stage stage, HookExecutionOutcome hookOutcome) {
        final HookId hookId = hookOutcome.getHookId();
        final ExecutionStatus status = hookOutcome.getStatus();
        final ExecutionAction action = hookOutcome.getAction();
        final String moduleCode = hookId.getModuleCode();

        metrics.updateHooksMetrics(
                moduleCode,
                stage,
                hookId.getHookImplCode(),
                status,
                hookOutcome.getExecutionTime(),
                action);

        // account might be null if request is rejected by the entrypoint hook
        if (account != null) {
            metrics.updateAccountHooksMetrics(account, moduleCode, status, action);
        }
    }

    private <T> List<T> nullIfEmpty(List<T> value) {
        return CollectionUtils.isEmpty(value) ? null : value;
    }
}
