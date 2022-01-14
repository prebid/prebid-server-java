package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.MultiBidConfig;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.DealsProcessor;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.deals.model.TxnLog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.GroupExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookExecutionOutcome;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionOutcome;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.hooks.v1.bidder.BidderRequestPayload;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.ExtPrebidBidders;
import org.prebid.server.proto.openrtb.ext.request.BidAdjustmentMediaType;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;
import org.prebid.server.proto.openrtb.ext.request.ExtDeal;
import org.prebid.server.proto.openrtb.ext.request.ExtDealLine;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidadjustmentfactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidMultiBid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.openrtb.ext.response.BidType;
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
import org.prebid.server.util.DealUtil;
import org.prebid.server.util.LineItemUtil;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Executes an OpenRTB v2.5 Auction.
 */
public class ExchangeService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";
    private static final String ORIGINAL_BID_CPM = "origbidcpm";
    private static final String ORIGINAL_BID_CURRENCY = "origbidcur";
    private static final String ALL_BIDDERS_CONFIG = "*";
    private static final Integer DEFAULT_MULTIBID_LIMIT_MIN = 1;
    private static final Integer DEFAULT_MULTIBID_LIMIT_MAX = 9;
    private static final String EID_ALLOWED_FOR_ALL_BIDDERS = "*";

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final long expectedCacheTime;
    private final BidderCatalog bidderCatalog;
    private final StoredResponseProcessor storedResponseProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final FpdResolver fpdResolver;
    private final SchainResolver schainResolver;
    private final DebugResolver debugResolver;
    private final HttpBidderRequester httpBidderRequester;
    private final ResponseBidValidator responseBidValidator;
    private final CurrencyConversionService currencyService;
    private final BidResponseCreator bidResponseCreator;
    private final ApplicationEventService applicationEventService;
    private final BidResponsePostProcessor bidResponsePostProcessor;
    private final HookStageExecutor hookStageExecutor;
    private final HttpInteractionLogger httpInteractionLogger;
    private final Metrics metrics;
    private final Clock clock;
    private final JacksonMapper mapper;
    private final CriteriaLogManager criteriaLogManager;

    public ExchangeService(long expectedCacheTime,
                           BidderCatalog bidderCatalog,
                           StoredResponseProcessor storedResponseProcessor,
                           PrivacyEnforcementService privacyEnforcementService,
                           FpdResolver fpdResolver,
                           SchainResolver schainResolver,
                           DebugResolver debugResolver,
                           HttpBidderRequester httpBidderRequester,
                           ResponseBidValidator responseBidValidator,
                           CurrencyConversionService currencyService,
                           BidResponseCreator bidResponseCreator,
                           BidResponsePostProcessor bidResponsePostProcessor,
                           HookStageExecutor hookStageExecutor,
                           ApplicationEventService applicationEventService,
                           HttpInteractionLogger httpInteractionLogger,
                           Metrics metrics,
                           Clock clock,
                           JacksonMapper mapper,
                           CriteriaLogManager criteriaLogManager) {

        if (expectedCacheTime < 0) {
            throw new IllegalArgumentException("Expected cache time should be positive");
        }
        this.expectedCacheTime = expectedCacheTime;
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.storedResponseProcessor = Objects.requireNonNull(storedResponseProcessor);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.fpdResolver = Objects.requireNonNull(fpdResolver);
        this.schainResolver = Objects.requireNonNull(schainResolver);
        this.debugResolver = Objects.requireNonNull(debugResolver);
        this.httpBidderRequester = Objects.requireNonNull(httpBidderRequester);
        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidResponseCreator = Objects.requireNonNull(bidResponseCreator);
        this.bidResponsePostProcessor = Objects.requireNonNull(bidResponsePostProcessor);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.applicationEventService = applicationEventService;
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
        this.criteriaLogManager = Objects.requireNonNull(criteriaLogManager);
    }

    /**
     * Runs an auction: delegates request to applicable bidders, gathers responses from them and constructs final
     * response containing returned bids and additional information in extensions.
     */
    public Future<AuctionContext> holdAuction(AuctionContext context) {
        return processAuctionRequest(context)
                .map(this::enrichWithHooksDebugInfo)
                .map(this::updateHooksMetrics);
    }

    private Future<AuctionContext> processAuctionRequest(AuctionContext context) {
        return context.isRequestRejected()
                ? Future.succeededFuture(context.with(emptyResponse()))
                : runAuction(context);
    }

    private static BidResponse emptyResponse() {
        return BidResponse.builder()
                .seatbid(Collections.emptyList())
                .build();
    }

    private Future<AuctionContext> runAuction(AuctionContext receivedContext) {
        final UidsCookie uidsCookie = receivedContext.getUidsCookie();
        final BidRequest bidRequest = receivedContext.getBidRequest();
        final Timeout timeout = receivedContext.getTimeout();
        final Account account = receivedContext.getAccount();
        final List<String> debugWarnings = receivedContext.getDebugWarnings();
        final MetricName requestTypeMetric = receivedContext.getRequestTypeMetric();

        final List<SeatBid> storedAuctionResponses = new ArrayList<>();
        final BidderAliases aliases = aliases(bidRequest);
        final String publisherId = account.getId();
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(bidRequest);
        final Map<String, MultiBidConfig> bidderToMultiBid = bidderToMultiBids(bidRequest, debugWarnings);

        return storedResponseProcessor.getStoredResponseResult(bidRequest.getImp(), timeout)
                .map(storedResponseResult -> populateStoredResponse(storedResponseResult, storedAuctionResponses))
                .compose(storedResponseResult -> extractAuctionParticipation(
                        receivedContext, storedResponseResult, aliases, bidderToMultiBid))

                .map(auctionParticipation -> updateRequestMetric(
                        auctionParticipation, uidsCookie, aliases, publisherId, requestTypeMetric))
                .map(bidderRequests -> maybeLogBidderInteraction(receivedContext, bidderRequests))
                .compose(auctionParticipations -> CompositeFuture.join(
                        auctionParticipations.stream()
                                .map(auctionParticipation -> invokeHooksAndRequestBids(
                                        receivedContext,
                                        auctionParticipation.getBidderRequest(),
                                        auctionTimeout(timeout, cacheInfo.isDoCaching()),
                                        aliases)
                                        .map(auctionParticipation::with))
                                .collect(Collectors.toList())))
                // send all the requests to the bidders and gathers results
                .map(CompositeFuture::<AuctionParticipation>list)

                .map(auctionParticipations -> storedResponseProcessor.mergeWithBidderResponses(
                        auctionParticipations, storedAuctionResponses, bidRequest.getImp()))
                .map(auctionParticipations -> dropZeroNonDealBids(auctionParticipations, debugWarnings))
                .map(auctionParticipations -> validateAndAdjustBids(auctionParticipations, receivedContext, aliases))
                .map(auctionParticipations -> updateMetricsFromResponses(auctionParticipations, publisherId, aliases))

                .map(receivedContext::with)

                // produce response from bidder results
                .compose(context -> bidResponseCreator.create(
                                context.getAuctionParticipations(),
                                context,
                                cacheInfo,
                                bidderToMultiBid)
                        .map(bidResponse -> publishAuctionEvent(bidResponse, receivedContext))
                        .map(bidResponse -> criteriaLogManager.traceResponse(logger, bidResponse,
                                receivedContext.getBidRequest(), receivedContext.getDebugContext().isDebugEnabled()))
                        .compose(bidResponse -> bidResponsePostProcessor.postProcess(
                                receivedContext.getHttpRequest(), uidsCookie, bidRequest, bidResponse, account))

                        .map(context::with))

                .compose(this::invokeResponseHooks);
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
                        ? ObjectUtils.defaultIfNull(cache.getVastxml()
                        .getReturnCreative(), true)
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

        final Map<String, MultiBidConfig> bidderToMultiBid = new HashMap<>();
        for (ExtRequestPrebidMultiBid prebidMultiBid : multiBids) {
            final String bidder = prebidMultiBid.getBidder();
            final List<String> bidders = prebidMultiBid.getBidders();
            final Integer maxBids = prebidMultiBid.getMaxBids();
            final String codePrefix = prebidMultiBid.getTargetBidderCodePrefix();

            if (bidder != null && CollectionUtils.isNotEmpty(bidders)) {
                debugWarnings.add(String.format("Invalid MultiBid: bidder %s and bidders %s specified. "
                        + "Only bidder %s will be used.", bidder, bidders, bidder));

                tryAddBidderWithMultiBid(bidder, maxBids, codePrefix, bidderToMultiBid, debugWarnings);
                continue;
            }

            if (bidder != null) {
                tryAddBidderWithMultiBid(bidder, maxBids, codePrefix, bidderToMultiBid, debugWarnings);
            } else if (CollectionUtils.isNotEmpty(bidders)) {
                if (codePrefix != null) {
                    debugWarnings.add(String.format("Invalid MultiBid: CodePrefix %s that was specified for bidders %s "
                            + "will be skipped.", codePrefix, bidders));
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
            debugWarnings.add(String.format("Invalid MultiBid: Bidder %s specified multiple times.", bidder));
            return;
        }

        if (maxBids == null) {
            debugWarnings.add(String.format("Invalid MultiBid: MaxBids for bidder %s is not specified and "
                    + "will be skipped.", bidder));
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
    private Future<List<AuctionParticipation>> extractAuctionParticipation(
            AuctionContext context,
            StoredResponseResult storedResponseResult,
            BidderAliases aliases,
            Map<String, MultiBidConfig> bidderToMultiBid) {

        final List<Imp> imps = storedResponseResult.getRequiredRequestImps().stream()
                .filter(imp -> bidderParamsFromImpExt(imp.getExt()) != null)
                .map(imp -> DealsProcessor.removeDealsOnlyBiddersWithoutDeals(imp, context))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        // identify valid bidders and aliases out of imps
        final List<String> bidders = imps.stream()
                .flatMap(imp -> StreamUtil.asStream(bidderParamsFromImpExt(imp.getExt()).fieldNames())
                        .filter(bidder -> isValidBidder(bidder, aliases)))
                .distinct()
                .collect(Collectors.toList());

        final Map<String, Map<String, String>> impBidderToStoredBidResponse =
                storedResponseResult.getImpBidderToStoredBidResponse();

        return makeAuctionParticipation(bidders, context, aliases, impBidderToStoredBidResponse,
                imps, bidderToMultiBid);
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
     * - bidrequest.user.ext.data, bidrequest.app.ext.data and bidrequest.site.ext.data will be removed for bidders
     * that don't have first party data allowed.
     */
    private Future<List<AuctionParticipation>> makeAuctionParticipation(
            List<String> bidders,
            AuctionContext context,
            BidderAliases aliases,
            Map<String, Map<String, String>> impBidderToStoredResponse,
            List<Imp> imps,
            Map<String, MultiBidConfig> bidderToMultiBid) {

        final BidRequest bidRequest = context.getBidRequest();
        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final Map<String, String> uidsBody = uidsFromBody(extUser);

        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final Map<String, ExtBidderConfigOrtb> biddersToConfigs = getBiddersToConfigs(prebid);
        final Map<String, List<String>> eidPermissions = getEidPermissions(prebid);
        final Map<String, User> bidderToUser =
                prepareUsers(bidders, context, aliases, bidRequest, extUser, uidsBody, biddersToConfigs,
                        eidPermissions);

        return privacyEnforcementService
                .mask(context, bidderToUser, bidders, aliases)
                .map(bidderToPrivacyResult ->
                        getAuctionParticipation(bidderToPrivacyResult, bidRequest, impBidderToStoredResponse, imps,
                                bidderToMultiBid, biddersToConfigs, aliases, context.getDebugWarnings()));
    }

    private Map<String, ExtBidderConfigOrtb> getBiddersToConfigs(ExtRequestPrebid prebid) {
        final List<ExtRequestPrebidBidderConfig> bidderConfigs = prebid == null ? null : prebid.getBidderconfig();

        if (CollectionUtils.isEmpty(bidderConfigs)) {
            return Collections.emptyMap();
        }

        final Map<String, ExtBidderConfigOrtb> bidderToConfig = new HashMap<>();

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
     * Returns UIDs from request.user.ext or empty map if not defined.
     */
    private static Map<String, String> uidsFromBody(ExtUser extUser) {
        return extUser != null && extUser.getPrebid() != null
                // as long as ext.prebid exists we are guaranteed that user.ext.prebid.buyeruids also exists
                ? extUser.getPrebid().getBuyeruids()
                : Collections.emptyMap();
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
                                           BidRequest bidRequest,
                                           ExtUser extUser,
                                           Map<String, String> uidsBody,
                                           Map<String, ExtBidderConfigOrtb> biddersToConfigs,
                                           Map<String, List<String>> eidPermissions) {

        final List<String> firstPartyDataBidders = firstPartyDataBidders(bidRequest.getExt());

        final Map<String, User> bidderToUser = new HashMap<>();
        for (String bidder : bidders) {
            final ExtBidderConfigOrtb fpdConfig = ObjectUtils.defaultIfNull(biddersToConfigs.get(bidder),
                    biddersToConfigs.get(ALL_BIDDERS_CONFIG));

            final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.contains(bidder);
            final User preparedUser = prepareUser(bidRequest.getUser(), extUser, bidder, aliases, uidsBody,
                    context.getUidsCookie(), useFirstPartyData, fpdConfig, eidPermissions);
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
    private User prepareUser(User user,
                             ExtUser extUser,
                             String bidder,
                             BidderAliases aliases,
                             Map<String, String> uidsBody,
                             UidsCookie uidsCookie,
                             boolean useFirstPartyData,
                             ExtBidderConfigOrtb fpdConfig,
                             Map<String, List<String>> eidPermissions) {

        final String updatedBuyerUid = updateUserBuyerUid(user, bidder, aliases, uidsBody, uidsCookie);
        final List<ExtUserEid> userEids = extractUserEids(user);
        final List<ExtUserEid> allowedUserEids = resolveAllowedEids(userEids, bidder, eidPermissions);
        final boolean shouldUpdateUserEids = extUser != null
                && allowedUserEids.size() != CollectionUtils.emptyIfNull(userEids).size();
        final boolean shouldCleanExtPrebid = extUser != null && extUser.getPrebid() != null;
        final boolean shouldCleanExtData = extUser != null && extUser.getData() != null && !useFirstPartyData;
        final boolean shouldUpdateUserExt = shouldCleanExtData || shouldCleanExtPrebid || shouldUpdateUserEids;
        final boolean shouldCleanData = user != null && user.getData() != null && !useFirstPartyData;

        User maskedUser = user;
        if (updatedBuyerUid != null || shouldUpdateUserExt || shouldCleanData) {
            final User.UserBuilder userBuilder = user == null ? User.builder() : user.toBuilder();
            if (updatedBuyerUid != null) {
                userBuilder.buyeruid(updatedBuyerUid);
            }

            if (shouldUpdateUserExt) {
                final ExtUser updatedExtUser = extUser.toBuilder()
                        .prebid(shouldCleanExtPrebid ? null : extUser.getPrebid())
                        .data(shouldCleanExtData ? null : extUser.getData())
                        .eids(shouldUpdateUserEids ? nullIfEmpty(allowedUserEids) : userEids)
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

    /**
     * Returns updated buyerUid or null if it doesn't need to be updated.
     */
    private String updateUserBuyerUid(User user, String bidder, BidderAliases aliases,
                                      Map<String, String> uidsBody, UidsCookie uidsCookie) {
        final String buyerUidFromBodyOrCookie = extractUid(uidsBody, uidsCookie, aliases.resolveBidder(bidder));
        final String buyerUidFromUser = user != null ? user.getBuyeruid() : null;

        return StringUtils.isBlank(buyerUidFromUser) && StringUtils.isNotBlank(buyerUidFromBodyOrCookie)
                ? buyerUidFromBodyOrCookie
                : null;
    }

    /**
     * Extracts {@link List<ExtUserEid>} from {@link User}.
     * Returns null if user or its extension is null.
     */
    private List<ExtUserEid> extractUserEids(User user) {
        final ExtUser extUser = user != null ? user.getExt() : null;
        return extUser != null ? extUser.getEids() : null;
    }

    /**
     * Returns {@link List<ExtUserEid>} allowed by {@param eidPermissions} per source per bidder.
     */
    private List<ExtUserEid> resolveAllowedEids(List<ExtUserEid> userEids, String bidder,
                                                Map<String, List<String>> eidPermissions) {
        return CollectionUtils.emptyIfNull(userEids)
                .stream()
                .filter(extUserEid -> isUserEidAllowed(extUserEid.getSource(), eidPermissions, bidder))
                .collect(Collectors.toList());
    }

    /**
     * Returns true if {@param source} allowed by {@param eidPermissions} for particular bidder taking into account
     * ealiases.
     */
    private boolean isUserEidAllowed(String source, Map<String, List<String>> eidPermissions, String bidder) {
        final List<String> allowedBidders = eidPermissions.get(source);
        return CollectionUtils.isEmpty(allowedBidders)
                || allowedBidders.contains(EID_ALLOWED_FOR_ALL_BIDDERS)
                || allowedBidders.contains(bidder);
    }

    /**
     * Extracts UID from uids from body or {@link UidsCookie}.
     */
    private String extractUid(Map<String, String> uidsBody, UidsCookie uidsCookie, String bidder) {
        final String uid = uidsBody.get(bidder);
        return StringUtils.isNotBlank(uid) ? uid : uidsCookie.uidFrom(resolveCookieFamilyName(bidder));
    }

    /**
     * Extract cookie family name from bidder's {@link Usersyncer} if it is enabled. If not - return null.
     */
    private String resolveCookieFamilyName(String bidder) {
        return bidderCatalog.isActive(bidder) ? bidderCatalog.usersyncerByName(bidder).getCookieFamilyName() : null;
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
            List<String> debugWarnings) {

        final Map<String, JsonNode> bidderToPrebidBidders = bidderToPrebidBidders(bidRequest);

        final List<AuctionParticipation> bidderRequests = bidderPrivacyResults.stream()
                // for each bidder create a new request that is a copy of original request except buyerid, imp
                // extensions, ext.prebid.data.bidders and ext.prebid.bidders.
                // Also, check whether to pass user.ext.data, app.ext.data and site.ext.data or not.
                .map(bidderPrivacyResult -> createAuctionParticipation(
                        bidderPrivacyResult,
                        bidRequest,
                        impBidderToStoredBidResponse,
                        imps,
                        bidderToMultiBid,
                        biddersToConfigs,
                        bidderToPrebidBidders,
                        aliases,
                        debugWarnings))
                // Can't be removed after we prepare workflow to filter blocked
                .filter(auctionParticipation -> !auctionParticipation.isRequestBlocked())
                .collect(Collectors.toList());

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
            BidRequest bidRequest,
            Map<String, Map<String, String>> impBidderToStoredBidResponse, List<Imp> imps,
            Map<String, MultiBidConfig> bidderToMultiBid,
            Map<String, ExtBidderConfigOrtb> biddersToConfigs,
            Map<String, JsonNode> bidderToPrebidBidders,
            BidderAliases bidderAliases,
            List<String> debugWarnings) {

        final boolean blockedRequestByTcf = bidderPrivacyResult.isBlockedRequestByTcf();
        final boolean blockedAnalyticsByTcf = bidderPrivacyResult.isBlockedAnalyticsByTcf();
        final String bidder = bidderPrivacyResult.getRequestBidder();
        if (blockedRequestByTcf) {
            return AuctionParticipation.builder()
                    .bidder(bidder)
                    .requestBlocked(true)
                    .analyticsBlocked(blockedAnalyticsByTcf)
                    .build();
        }

        final List<String> firstPartyDataBidders = firstPartyDataBidders(bidRequest.getExt());
        final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.contains(bidder);

        final ExtBidderConfigOrtb fpdConfig = ObjectUtils.defaultIfNull(biddersToConfigs.get(bidder),
                biddersToConfigs.get(ALL_BIDDERS_CONFIG));

        final App app = bidRequest.getApp();
        final Site site = bidRequest.getSite();
        if (app != null && site != null) {
            debugWarnings.add("BidRequest contains app and site. Removed site object");
        }
        final Site resolvedSite = app == null ? site : null;

        final ObjectNode fpdSite = fpdConfig != null ? fpdConfig.getSite() : null;
        final ObjectNode fpdApp = fpdConfig != null ? fpdConfig.getApp() : null;

        // stored bid response supported only for single imp requests
        final String storedBidResponse = impBidderToStoredBidResponse.size() == 1
                ? impBidderToStoredBidResponse.get(imps.get(0).getId()).get(bidder)
                : null;

        final BidderRequest bidderRequest = BidderRequest.of(bidder, storedBidResponse, bidRequest.toBuilder()
                // User was already prepared above
                .user(bidderPrivacyResult.getUser())
                .device(bidderPrivacyResult.getDevice())
                .imp(prepareImps(bidder, imps, useFirstPartyData, bidderAliases))
                .app(prepareApp(app, fpdApp, useFirstPartyData))
                .site(prepareSite(resolvedSite, fpdSite, useFirstPartyData))
                .source(prepareSource(bidder, bidRequest))
                .ext(prepareExt(bidder, bidderToPrebidBidders, bidderToMultiBid, bidRequest.getExt()))
                .build());

        return AuctionParticipation.builder()
                .bidder(bidder)
                .bidderRequest(bidderRequest)
                .requestBlocked(false)
                .analyticsBlocked(blockedAnalyticsByTcf)
                .build();
    }

    /**
     * For each given imp creates a new imp with extension crafted to contain only "prebid", "context" and
     * bidder-specific extension.
     */
    private List<Imp> prepareImps(String bidder, List<Imp> imps, boolean useFirstPartyData, BidderAliases aliases) {
        return imps.stream()
                .filter(imp -> bidderParamsFromImpExt(imp.getExt()).hasNonNull(bidder))
                .map(imp -> imp.toBuilder()
                        .pmp(preparePmp(bidder, imp.getPmp(), aliases))
                        .ext(prepareImpExt(bidder, imp.getExt(), useFirstPartyData))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Removes deal from {@link Pmp} if bidder's deals doesn't contain it.
     */
    private Pmp preparePmp(String bidder, Pmp pmp, BidderAliases aliases) {
        final List<Deal> originalDeals = pmp != null ? pmp.getDeals() : null;
        if (CollectionUtils.isEmpty(originalDeals)) {
            return pmp;
        }

        final List<Deal> updatedDeals = originalDeals.stream()
                .map(deal -> Tuple2.of(deal, toExtDeal(deal.getExt())))
                .filter((Tuple2<Deal, ExtDeal> tuple) -> DealUtil.isBidderHasDeal(bidder, tuple.getRight(), aliases))
                .map((Tuple2<Deal, ExtDeal> tuple) -> prepareDeal(tuple.getLeft(), tuple.getRight()))
                .collect(Collectors.toList());

        return pmp.toBuilder().deals(updatedDeals).build();
    }

    /**
     * Returns {@link ExtDeal} from the given {@link ObjectNode}.
     */
    private ExtDeal toExtDeal(ObjectNode ext) {
        if (ext == null) {
            return null;
        }
        try {
            return mapper.mapper().treeToValue(ext, ExtDeal.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(
                    String.format("Error decoding bidRequest.imp.pmp.deal.ext: %s", e.getMessage()), e);
        }
    }

    /**
     * Removes bidder from imp[].pmp.deal[].ext.line object if presents.
     */
    private Deal prepareDeal(Deal deal, ExtDeal extDeal) {
        final ExtDealLine line = extDeal != null ? extDeal.getLine() : null;
        final ExtDealLine updatedLine = line != null
                ? ExtDealLine.of(line.getLineItemId(),
                line.getExtLineItemId(),
                line.getSizes(),
                null)
                : null;

        return updatedLine != null
                ? deal.toBuilder().ext(mapper.mapper().valueToTree(ExtDeal.of(updatedLine))).build()
                : deal;
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
    private ObjectNode prepareImpExt(String bidder, ObjectNode impExt, boolean useFirstPartyData) {
        final ObjectNode modifiedImpExt = impExt.deepCopy();

        final JsonNode impExtPrebid = cleanBidderParamsFromImpExtPrebid(impExt.get(PREBID_EXT));
        if (impExtPrebid == null) {
            modifiedImpExt.remove(PREBID_EXT);
        } else {
            modifiedImpExt.set(PREBID_EXT, impExtPrebid);
        }

        modifiedImpExt.set(BIDDER_EXT, bidderParamsFromImpExt(impExt).get(bidder));

        return fpdResolver.resolveImpExt(modifiedImpExt, useFirstPartyData);
    }

    private JsonNode cleanBidderParamsFromImpExtPrebid(JsonNode extImpPrebidNode) {
        if (extImpPrebidNode.size() > 1) {
            return mapper.mapper().valueToTree(
                    extImpPrebid(extImpPrebidNode).toBuilder()
                            .bidder(null)
                            .build());
        }

        return null;
    }

    /**
     * Returns {@link ExtImpPrebid} from imp.ext.prebid {@link JsonNode}.
     */
    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return mapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding imp.ext.prebid: %s", e.getMessage()), e);
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

        return useFirstPartyData
                ? fpdResolver.resolveApp(maskedApp, fpdApp)
                : maskedApp;
    }

    private ExtApp maskExtApp(ExtApp appExt) {
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

        return useFirstPartyData
                ? fpdResolver.resolveSite(maskedSite, fpdSite)
                : maskedSite;
    }

    private Content prepareContent(Content content) {
        final Content updatedContent = content.toBuilder()
                .data(null)
                .build();

        return updatedContent.isEmpty() ? null : updatedContent;
    }

    private ExtSite maskExtSite(ExtSite siteExt) {
        final ExtSite maskedExtSite = ExtSite.of(siteExt.getAmp(), null);
        return maskedExtSite.isEmpty() ? null : maskedExtSite;
    }

    /**
     * Returns {@link Source} with corresponding request.ext.prebid.schains.
     */
    private Source prepareSource(String bidder, BidRequest bidRequest) {
        final Source receivedSource = bidRequest.getSource();

        final ExtRequestPrebidSchainSchain bidderSchain = schainResolver.resolveForBidder(bidder, bidRequest);

        if (bidderSchain == null) {
            return receivedSource;
        }

        final ExtSource extSource = ExtSource.of(bidderSchain);

        return receivedSource == null
                ? Source.builder().ext(extSource).build()
                : receivedSource.toBuilder().ext(extSource).build();
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

        if (bidderToPrebidBidders.isEmpty()
                && bidderToMultiBid.isEmpty()
                && !suppressSchains
                && !suppressBidderConfig
                && !suppressPrebidData
                && !suppressBidderParameters) {
            return requestExt;
        }

        final JsonNode prebidParameters = bidderToPrebidBidders.get(bidder);
        final ObjectNode bidders = prebidParameters != null
                ? mapper.mapper().valueToTree(ExtPrebidBidders.of(prebidParameters))
                : null;

        final ExtRequestPrebid.ExtRequestPrebidBuilder extPrebidBuilder = extPrebid != null
                ? extPrebid.toBuilder()
                : ExtRequestPrebid.builder();

        return ExtRequest.of(
                extPrebidBuilder
                        .multibid(resolveExtRequestMultiBids(bidderToMultiBid.get(bidder), bidder))
                        .bidders(bidders)
                        .bidderparams(prepareBidderParameters(extPrebid, bidder))
                        .schains(null)
                        .data(null)
                        .bidderconfig(null)
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
    private List<AuctionParticipation> updateRequestMetric(List<AuctionParticipation> auctionParticipations,
                                                           UidsCookie uidsCookie,
                                                           BidderAliases aliases,
                                                           String publisherId,
                                                           MetricName requestTypeMetric) {
        auctionParticipations = auctionParticipations.stream()
                .filter(auctionParticipation -> !auctionParticipation.isRequestBlocked())
                .collect(Collectors.toList());

        metrics.updateRequestBidderCardinalityMetric(auctionParticipations.size());
        metrics.updateAccountRequestMetrics(publisherId, requestTypeMetric);

        for (AuctionParticipation auctionParticipation : auctionParticipations) {
            if (auctionParticipation.isRequestBlocked()) {
                continue;
            }

            final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
            final String bidder = aliases.resolveBidder(bidderRequest.getBidder());
            final boolean isApp = bidderRequest.getBidRequest().getApp() != null;
            final boolean noBuyerId = !bidderCatalog.isActive(bidder) || StringUtils.isBlank(
                    uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).getCookieFamilyName()));

            metrics.updateAdapterRequestTypeAndNoCookieMetrics(bidder, requestTypeMetric, !isApp && noBuyerId);
        }

        return auctionParticipations;
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

        if (hookStageResult.isShouldReject()) {
            return Future.succeededFuture(BidderResponse.of(bidderRequest.getBidder(), BidderSeatBid.empty(), 0));
        }

        final BidderRequest enrichedBidderRequest = bidderRequest.with(hookStageResult.getPayload().bidRequest());
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

        final boolean debugEnabledForBidder = debugResolver.resolveDebugForBidder(auctionContext, resolvedBidderName);

        final long startTime = clock.millis();

        return httpBidderRequester.requestBids(bidder, bidderRequest, timeout, requestHeaders, debugEnabledForBidder)
                .map(seatBid -> BidderResponse.of(bidderName, seatBid, responseTime(startTime)));
    }

    private BidderResponse rejectBidderResponseOrProceed(HookStageExecutionResult<BidderResponsePayload> stageResult,
                                                         BidderResponse bidderResponse) {

        final List<BidderBid> bids = stageResult.isShouldReject()
                ? Collections.emptyList()
                : stageResult.getPayload().bids();

        return bidderResponse
                .with(bidderResponse.getSeatBid().with(bids));
    }

    private List<AuctionParticipation> dropZeroNonDealBids(List<AuctionParticipation> auctionParticipations,
                                                           List<String> debugWarnings) {

        return auctionParticipations.stream()
                .map(auctionParticipation -> dropZeroNonDealBids(auctionParticipation, debugWarnings))
                .collect(Collectors.toList());
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
                debugWarnings.add(String.format(
                        "Dropped bid '%s'. Does not contain a positive (or zero if there is a deal) 'price'",
                        bid.getId()));
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
                .collect(Collectors.toList());
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

        final List<String> requestCurrencies = bidRequest.getCur();
        if (requestCurrencies.size() > 1) {
            errors.add(BidderError.badInput(
                    String.format("Cur parameter contains more than one currency. %s will be used",
                            requestCurrencies.get(0))));
        }

        final List<BidderBid> bids = seatBid.getBids();
        final List<BidderBid> validBids = new ArrayList<>(bids.size());

        final TxnLog txnLog = auctionContext.getTxnLog();
        final String bidder = bidderResponse.getBidder();

        for (final BidderBid bid : bids) {
            final String lineItemId = LineItemUtil.lineItemIdFrom(bid.getBid(), bidRequest.getImp(), mapper);
            maybeRecordInTxnLog(lineItemId, () -> txnLog.lineItemsReceivedFromBidder().get(bidder));

            final ValidationResult validationResult =
                    responseBidValidator.validate(bid, bidderResponse.getBidder(), auctionContext, aliases);

            if (validationResult.hasWarnings()) {
                addAsBidderErrors(validationResult.getWarnings(), errors);
            }

            if (validationResult.hasErrors()) {
                addAsBidderErrors(validationResult.getErrors(), errors);
                maybeRecordInTxnLog(lineItemId, txnLog::lineItemsResponseInvalidated);
                continue;
            }

            validBids.add(bid);
        }

        final BidderResponse resultBidderResponse = errors.isEmpty()
                ? bidderResponse
                : bidderResponse.with(BidderSeatBid.of(validBids, seatBid.getHttpCalls(), errors));
        return auctionParticipation.with(resultBidderResponse);
    }

    private void addAsBidderErrors(List<String> messages, List<BidderError> errors) {
        messages.stream().map(BidderError::generic).forEach(errors::add);
    }

    private static void maybeRecordInTxnLog(String lineItemId, Supplier<Set<String>> metricSupplier) {
        if (lineItemId != null) {
            metricSupplier.get().add(lineItemId);
        }
    }

    private BidResponse publishAuctionEvent(BidResponse bidResponse, AuctionContext auctionContext) {
        if (applicationEventService != null) {
            applicationEventService.publishAuctionEvent(auctionContext);
        }
        return bidResponse;
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

        final BidderResponse resultBidderResponse =
                bidderResponse.with(BidderSeatBid.of(updatedBidderBids, seatBid.getHttpCalls(), errors));
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
                price, bidRequest, adServerCurrency, StringUtils.stripToNull(bidCurrency));

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

        return bidderBid.with(bidBuilder.build());
    }

    private static BidAdjustmentMediaType resolveBidAdjustmentMediaType(String bidImpId,
                                                                        List<Imp> imps,
                                                                        BidType bidType) {

        switch (bidType) {
            case banner:
                return BidAdjustmentMediaType.banner;
            case xNative:
                return BidAdjustmentMediaType.xNative;
            case audio:
                return BidAdjustmentMediaType.audio;
            case video:
                return resolveBidAdjustmentVideoMediaType(bidImpId, imps);
            default:
                throw new PreBidException("BidType not present for bidderBid");
        }
    }

    private static BidAdjustmentMediaType resolveBidAdjustmentVideoMediaType(String bidImpId, List<Imp> imps) {
        final Video bidImpVideo = imps.stream()
                .filter(imp -> imp.getId().equals(bidImpId))
                .map(Imp::getVideo)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (bidImpVideo == null) {
            return null;
        }

        final Integer placement = bidImpVideo.getPlacement();
        return placement == null || Objects.equals(placement, 1)
                ? BidAdjustmentMediaType.video
                : BidAdjustmentMediaType.video_outstream;
    }

    private static BigDecimal bidAdjustmentForBidder(String bidder,
                                                     BidRequest bidRequest,
                                                     BidderBid bidderBid) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final ExtRequestBidadjustmentfactors extBidadjustmentfactors = prebid != null
                ? prebid.getBidadjustmentfactors()
                : null;
        if (extBidadjustmentfactors == null) {
            return null;
        }
        final BidAdjustmentMediaType mediaType =
                resolveBidAdjustmentMediaType(bidderBid.getBid().getImpid(), bidRequest.getImp(), bidderBid.getType());

        return resolveBidAdjustmentFactor(extBidadjustmentfactors, mediaType, bidder);
    }

    private static BigDecimal resolveBidAdjustmentFactor(ExtRequestBidadjustmentfactors extBidadjustmentfactors,
                                                         BidAdjustmentMediaType mediaType,
                                                         String bidder) {
        final Map<BidAdjustmentMediaType, Map<String, BigDecimal>> mediatypes =
                extBidadjustmentfactors.getMediatypes();
        final Map<String, BigDecimal> adjustmentsByMediatypes = mediatypes != null ? mediatypes.get(mediaType) : null;
        final BigDecimal adjustmentFactorByMediaType =
                adjustmentsByMediatypes != null ? adjustmentsByMediatypes.get(bidder) : null;
        if (adjustmentFactorByMediaType != null) {
            return adjustmentFactorByMediaType;
        }
        return extBidadjustmentfactors.getAdjustments().get(bidder);
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
     * If we need to cache bids, then it will take some time to call prebid cache.
     * We should reduce the amount of time the bidders have, to compensate.
     */
    private Timeout auctionTimeout(Timeout timeout, boolean shouldCacheBids) {
        // A static timeout here is not ideal. This is a hack because we have some aggressive timelines for OpenRTB
        // support.
        // In reality, the cache response time will probably fluctuate with the traffic over time. Someday, this
        // should be replaced by code which tracks the response time of recent cache calls and adjusts the time
        // dynamically.
        return shouldCacheBids ? timeout.minus(expectedCacheTime) : timeout;
    }

    private List<AuctionParticipation> maybeLogBidderInteraction(AuctionContext context,
                                                                 List<AuctionParticipation> auctionParticipations) {
        auctionParticipations.forEach(auctionParticipation ->
                httpInteractionLogger.maybeLogBidderRequest(context, auctionParticipation.getBidderRequest()));

        return auctionParticipations;
    }

    /**
     * Updates 'request_time', 'responseTime', 'timeout_request', 'error_requests', 'no_bid_requests',
     * 'prices' metrics for each {@link AuctionParticipation}.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderResponse} to make sure
     * {@link Bid#getPrice()} is not empty.
     */
    private List<AuctionParticipation> updateMetricsFromResponses(List<AuctionParticipation> auctionParticipations,
                                                                  String publisherId,
                                                                  BidderAliases aliases) {
        final List<BidderResponse> bidderResponses = auctionParticipations.stream()
                .filter(auctionParticipation -> !auctionParticipation.isRequestBlocked())
                .map(AuctionParticipation::getBidderResponse)
                .collect(Collectors.toList());

        for (BidderResponse bidderResponse : bidderResponses) {
            final String bidder = aliases.resolveBidder(bidderResponse.getBidder());

            metrics.updateAdapterResponseTime(bidder, publisherId, bidderResponse.getResponseTime());

            final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
            if (CollectionUtils.isEmpty(bidderBids)) {
                metrics.updateAdapterRequestNobidMetrics(bidder, publisherId);
            } else {
                metrics.updateAdapterRequestGotbidsMetrics(bidder, publisherId);

                for (final BidderBid bidderBid : bidderBids) {
                    final Bid bid = bidderBid.getBid();

                    final long cpm = bid.getPrice().multiply(THOUSAND).longValue();
                    metrics.updateAdapterBidMetrics(bidder, publisherId, cpm, bid.getAdm() != null,
                            bidderBid.getType().toString());
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
        final MetricName errorMetric;
        switch (errorType) {
            case bad_input:
                errorMetric = MetricName.badinput;
                break;
            case bad_server_response:
                errorMetric = MetricName.badserverresponse;
                break;
            case failed_to_request_bids:
                errorMetric = MetricName.failedtorequestbids;
                break;
            case timeout:
                errorMetric = MetricName.timeout;
                break;
            case generic:
            default:
                errorMetric = MetricName.unknown_error;
        }
        return errorMetric;
    }

    private AuctionContext enrichWithHooksDebugInfo(AuctionContext context) {
        final ExtModules extModules = toExtModules(context);

        if (extModules == null) {
            return context;
        }

        final BidResponse bidResponse = context.getBidResponse();
        final ExtBidResponse ext = bidResponse.getExt();
        final ExtBidResponsePrebid extPrebid = ext != null ? ext.getPrebid() : null;

        final ExtBidResponsePrebid updatedExtPrebid = ExtBidResponsePrebid.of(
                extPrebid != null ? extPrebid.getAuctiontimestamp() : null,
                extModules);
        final ExtBidResponse updatedExt = (ext != null ? ext.toBuilder() : ExtBidResponse.builder())
                .prebid(updatedExtPrebid)
                .build();

        final BidResponse updatedBidResponse = bidResponse.toBuilder()
                .ext(updatedExt)
                .build();
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
                                                .collect(Collectors.toList())))));

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
                .collect(Collectors.toList());

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
                .collect(Collectors.toList());

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
                .collect(Collectors.toList());

        if (groups.isEmpty()) {
            return null;
        }

        final long executionTime = groups.stream().mapToLong(ExtModulesTraceGroup::getExecutionTime).sum();

        return ExtModulesTraceStageOutcome.of(stageOutcome.getEntity(), executionTime, groups);
    }

    private static ExtModulesTraceGroup toTraceGroup(GroupExecutionOutcome group, TraceLevel level) {
        final List<ExtModulesTraceInvocationResult> invocationResults = group.getHooks().stream()
                .map(hook -> toTraceInvocationResult(hook, level))
                .collect(Collectors.toList());

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
                .collect(Collectors.toList()));
    }

    private static ExtModulesTraceAnalyticsActivity toTraceAnalyticsActivity(Activity activity) {
        return ExtModulesTraceAnalyticsActivity.of(
                activity.name(),
                activity.status(),
                CollectionUtils.emptyIfNull(activity.results()).stream()
                        .filter(Objects::nonNull)
                        .map(ExchangeService::toTraceAnalyticsResult)
                        .collect(Collectors.toList()));
    }

    private static ExtModulesTraceAnalyticsResult toTraceAnalyticsResult(Result result) {
        final AppliedTo appliedTo = result.appliedTo();
        final ExtModulesTraceAnalyticsAppliedTo extAppliedTo = appliedTo != null
                ? ExtModulesTraceAnalyticsAppliedTo.builder()
                .impIds(appliedTo.impIds())
                .bidders(appliedTo.bidders())
                .request(appliedTo.request()
                        ? Boolean.TRUE
                        : null)
                .response(appliedTo.response()
                        ? Boolean.TRUE
                        : null)
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
            final String accountId = account.getId();

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
                            metrics.updateAccountModuleDurationMetric(accountId, moduleCode, executionTime));
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
            metrics.updateAccountHooksMetrics(account.getId(), moduleCode, status, action);
        }
    }

    private <T> List<T> nullIfEmpty(List<T> value) {
        return CollectionUtils.isEmpty(value) ? null : value;
    }
}
