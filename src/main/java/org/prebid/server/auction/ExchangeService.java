package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
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
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.ExtPrebidBidders;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.StreamUtil;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Executes an OpenRTB v2.5 Auction.
 */
public class ExchangeService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeService.class);

    private static final String PREBID_EXT = "prebid";
    private static final String CONTEXT_EXT = "context";
    private static final String DATA = "data";
    private static final String ALL_BIDDERS_CONFIG = "*";
    private static final String GENERIC_SCHAIN_KEY = "*";

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final long expectedCacheTime;
    private final BidderCatalog bidderCatalog;
    private final StoredResponseProcessor storedResponseProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final FpdResolver fpdResolver;
    private final HttpBidderRequester httpBidderRequester;
    private final ResponseBidValidator responseBidValidator;
    private final CurrencyConversionService currencyService;
    private final BidResponseCreator bidResponseCreator;
    private final BidResponsePostProcessor bidResponsePostProcessor;
    private final Metrics metrics;
    private final Clock clock;
    private final JacksonMapper mapper;

    public ExchangeService(long expectedCacheTime,
                           BidderCatalog bidderCatalog,
                           StoredResponseProcessor storedResponseProcessor,
                           PrivacyEnforcementService privacyEnforcementService,
                           FpdResolver fpdResolver,
                           HttpBidderRequester httpBidderRequester,
                           ResponseBidValidator responseBidValidator,
                           CurrencyConversionService currencyService,
                           BidResponseCreator bidResponseCreator,
                           BidResponsePostProcessor bidResponsePostProcessor,
                           Metrics metrics,
                           Clock clock,
                           JacksonMapper mapper) {

        if (expectedCacheTime < 0) {
            throw new IllegalArgumentException("Expected cache time should be positive");
        }
        this.expectedCacheTime = expectedCacheTime;
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.storedResponseProcessor = Objects.requireNonNull(storedResponseProcessor);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.fpdResolver = Objects.requireNonNull(fpdResolver);
        this.httpBidderRequester = Objects.requireNonNull(httpBidderRequester);
        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidResponseCreator = Objects.requireNonNull(bidResponseCreator);
        this.bidResponsePostProcessor = Objects.requireNonNull(bidResponsePostProcessor);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Runs an auction: delegates request to applicable bidders, gathers responses from them and constructs final
     * response containing returned bids and additional information in extensions.
     */
    public Future<BidResponse> holdAuction(AuctionContext context) {
        final UidsCookie uidsCookie = context.getUidsCookie();
        final BidRequest bidRequest = context.getBidRequest();
        final Timeout timeout = context.getTimeout();
        final Account account = context.getAccount();

        final List<SeatBid> storedResponse = new ArrayList<>();
        final BidderAliases aliases = aliases(bidRequest);
        final String publisherId = account.getId();
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(bidRequest);
        final boolean debugEnabled = isDebugEnabled(bidRequest);

        return storedResponseProcessor.getStoredResponseResult(bidRequest.getImp(), aliases, timeout)
                .map(storedResponseResult -> populateStoredResponse(storedResponseResult, storedResponse))
                .compose(impsRequiredRequest -> extractBidderRequests(context, impsRequiredRequest, aliases))
                .map(bidderRequests -> updateRequestMetric(
                        bidderRequests, uidsCookie, aliases, publisherId, context.getRequestTypeMetric()))
                .compose(bidderRequests -> CompositeFuture.join(
                        bidderRequests.stream()
                                .map(bidderRequest -> requestBids(
                                        bidderRequest,
                                        auctionTimeout(timeout, cacheInfo.isDoCaching()),
                                        debugEnabled,
                                        aliases))
                                .collect(Collectors.toList())))
                // send all the requests to the bidders and gathers results
                .map(CompositeFuture::<BidderResponse>list)
                .map(bidderResponses -> storedResponseProcessor.mergeWithBidderResponses(
                        bidderResponses, storedResponse, bidRequest.getImp()))
                .map(bidderResponses -> validateAndAdjustBids(bidderResponses, context, aliases))
                .map(bidderResponses -> updateMetricsFromResponses(bidderResponses, publisherId, aliases))
                // produce response from bidder results
                .compose(bidderResponses -> bidResponseCreator.create(
                        bidderResponses,
                        context,
                        cacheInfo,
                        debugEnabled))
                .compose(bidResponse -> bidResponsePostProcessor.postProcess(
                        context.getRoutingContext(), uidsCookie, bidRequest, bidResponse, account));
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

    private static Map<String, Map<String, BigDecimal>> currencyRates(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final ExtRequestCurrency currency = prebid != null ? prebid.getCurrency() : null;
        return currency != null ? currency.getRates() : null;
    }

    private static Boolean usepbsrates(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final ExtRequestCurrency currency = prebid != null ? prebid.getCurrency() : null;
        return currency != null ? currency.getUsepbsrates() : null;
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
            final boolean shouldCacheWinningBidsOnly = targeting.getIncludebidderkeys()
                    ? false // ext.prebid.targeting.includebidderkeys takes precedence
                    : ObjectUtils.defaultIfNull(cache.getWinningonly(), false);

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

    /**
     * Determines debug flag from {@link BidRequest} or {@link ExtRequest}.
     */
    private static boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }
        final ExtRequestPrebid extRequestPrebid = extRequestPrebid(bidRequest);
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    private static ExtRequestPrebid extRequestPrebid(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        return requestExt != null ? requestExt.getPrebid() : null;
    }

    /**
     * Populates storedResponse parameter with stored {@link List<SeatBid>} and returns {@link List<Imp>} for which
     * request to bidders should be performed.
     */
    private static List<Imp> populateStoredResponse(StoredResponseResult storedResponseResult,
                                                    List<SeatBid> storedResponse) {
        storedResponse.addAll(storedResponseResult.getStoredResponse());
        return storedResponseResult.getRequiredRequestImps();
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
     * For example suppose {@link BidRequest} has two {@link Imp}s. First one with imp.ext[].rubicon and
     * imp.ext[].rubiconAlias and second with imp.ext[].appnexus and imp.ext[].rubicon. Three {@link BidRequest}s will
     * be created:
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
    private Future<List<BidderRequest>> extractBidderRequests(AuctionContext context,
                                                              List<Imp> requestedImps,
                                                              BidderAliases aliases) {
        // sanity check: discard imps without extension
        final List<Imp> imps = requestedImps.stream()
                .filter(imp -> imp.getExt() != null)
                .collect(Collectors.toList());

        // identify valid bidders and aliases out of imps
        final List<String> bidders = imps.stream()
                .flatMap(imp -> StreamUtil.asStream(imp.getExt().fieldNames())
                        .filter(bidder -> !Objects.equals(bidder, PREBID_EXT) && !Objects.equals(bidder, CONTEXT_EXT))
                        .filter(bidder -> isValidBidder(bidder, aliases)))
                .distinct()
                .collect(Collectors.toList());

        return makeBidderRequests(bidders, context, aliases, imps);
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
    private Future<List<BidderRequest>> makeBidderRequests(List<String> bidders,
                                                           AuctionContext context,
                                                           BidderAliases aliases,
                                                           List<Imp> imps) {

        final BidRequest bidRequest = context.getBidRequest();
        final User user = bidRequest.getUser();
        final ExtUser extUser = user != null ? user.getExt() : null;
        final Map<String, String> uidsBody = uidsFromBody(extUser);

        final ExtRequest requestExt = bidRequest.getExt();
        final Map<String, ExtBidderConfigFpd> biddersToConfigs = getBiddersToConfigs(requestExt);

        final Map<String, User> bidderToUser =
                prepareUsers(bidders, context, aliases, bidRequest, extUser, uidsBody, biddersToConfigs);

        return privacyEnforcementService
                .mask(context, bidderToUser, bidders, aliases)
                .map(bidderToPrivacyResult ->
                        getBidderRequests(bidderToPrivacyResult, bidRequest, imps, biddersToConfigs));
    }

    private Map<String, ExtBidderConfigFpd> getBiddersToConfigs(ExtRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final List<ExtRequestPrebidBidderConfig> bidderConfigs = prebid == null ? null : prebid.getBidderconfig();

        if (CollectionUtils.isEmpty(bidderConfigs)) {
            return Collections.emptyMap();
        }

        final Map<String, ExtBidderConfigFpd> bidderToConfig = new HashMap<>();

        bidderConfigs.stream()
                .filter(prebidBidderConfig -> prebidBidderConfig.getBidders().contains(ALL_BIDDERS_CONFIG))
                .map(prebidBidderConfig -> prebidBidderConfig.getConfig().getFpd())
                .findFirst()
                .ifPresent(extBidderConfigFpd -> bidderToConfig.put(ALL_BIDDERS_CONFIG, extBidderConfigFpd));

        for (ExtRequestPrebidBidderConfig config : bidderConfigs) {
            for (String bidder : config.getBidders()) {
                final ExtBidderConfigFpd concreteFpd = config.getConfig().getFpd();
                bidderToConfig.putIfAbsent(bidder, concreteFpd);
            }
        }
        return bidderToConfig;
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
                                           Map<String, ExtBidderConfigFpd> biddersToConfigs) {

        final List<String> firstPartyDataBidders = firstPartyDataBidders(bidRequest.getExt());

        final Map<String, User> bidderToUser = new HashMap<>();
        for (String bidder : bidders) {
            final ExtBidderConfigFpd fpdConfig = ObjectUtils.firstNonNull(biddersToConfigs.get(bidder),
                    biddersToConfigs.get(ALL_BIDDERS_CONFIG));

            final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.contains(bidder);
            final User preparedUser = prepareUser(bidRequest.getUser(), extUser, bidder, aliases, uidsBody,
                    context.getUidsCookie(), useFirstPartyData, fpdConfig);
            bidderToUser.put(bidder, preparedUser);
        }
        return bidderToUser;
    }

    /**
     * Returns original {@link User} if user.buyeruid already contains uid value for bidder.
     * Otherwise, returns new {@link User} containing updated {@link ExtUser} and user.buyeruid.
     * <p>
     * Also, removes user.ext.prebid (if present) and user.ext.data (in case bidder does not use first party data).
     */
    private User prepareUser(User user,
                             ExtUser extUser,
                             String bidder,
                             BidderAliases aliases,
                             Map<String, String> uidsBody,
                             UidsCookie uidsCookie,
                             boolean useFirstPartyData,
                             ExtBidderConfigFpd fpdConfig) {

        final String updatedBuyerUid = updateUserBuyerUid(user, bidder, aliases, uidsBody, uidsCookie);
        final boolean shouldCleanPrebid = extUser != null && extUser.getPrebid() != null;
        final boolean shouldCleanData = extUser != null && extUser.getData() != null && !useFirstPartyData;
        final boolean shouldUpdateUserExt = shouldCleanData || shouldCleanPrebid;

        User maskedUser = user;
        if (updatedBuyerUid != null || shouldUpdateUserExt) {
            final User.UserBuilder userBuilder = user == null ? User.builder() : user.toBuilder();
            if (updatedBuyerUid != null) {
                userBuilder.buyeruid(updatedBuyerUid);
            }

            if (shouldUpdateUserExt) {
                final ExtUser updatedExtUser = extUser.toBuilder()
                        .prebid(shouldCleanPrebid ? null : extUser.getPrebid())
                        .data(shouldCleanData ? null : extUser.getData())
                        .build();
                userBuilder.ext(updatedExtUser.isEmpty() ? null : updatedExtUser);
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
     * Returns shuffled list of {@link BidderRequest}.
     */
    private List<BidderRequest> getBidderRequests(List<BidderPrivacyResult> bidderPrivacyResults,
                                                  BidRequest bidRequest,
                                                  List<Imp> imps,
                                                  Map<String, ExtBidderConfigFpd> biddersToConfigs) {

        final ExtRequest requestExt = bidRequest.getExt();
        final Map<String, JsonNode> bidderToPrebidBidders = bidderToPrebidBidders(requestExt);
        final Map<String, ExtRequestPrebidSchainSchain> bidderToPrebidSchains = bidderToPrebidSchains(requestExt);
        final List<BidderRequest> bidderRequests = bidderPrivacyResults.stream()
                // for each bidder create a new request that is a copy of original request except buyerid, imp
                // extensions, ext.prebid.data.bidders and ext.prebid.bidders.
                // Also, check whether to pass user.ext.data, app.ext.data and site.ext.data or not.
                .map(bidderPrivacyResult -> createBidderRequest(bidderPrivacyResult, bidRequest, imps,
                        biddersToConfigs, bidderToPrebidBidders, bidderToPrebidSchains))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Collections.shuffle(bidderRequests);

        return bidderRequests;
    }

    /**
     * Extracts a map of bidders to their arguments from {@link ObjectNode} prebid.bidders.
     */
    private static Map<String, JsonNode> bidderToPrebidBidders(ExtRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
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
     * Extracts a map of bidders to their arguments from {@link ObjectNode} prebid.schains.
     */
    private static Map<String, ExtRequestPrebidSchainSchain> bidderToPrebidSchains(ExtRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final List<ExtRequestPrebidSchain> schains = prebid == null ? null : prebid.getSchains();

        if (schains == null || schains.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, ExtRequestPrebidSchainSchain> bidderToPrebidSchains = new HashMap<>();
        for (ExtRequestPrebidSchain schain : schains) {
            final List<String> bidders = schain.getBidders();
            if (CollectionUtils.isNotEmpty(bidders)) {
                for (String bidder : bidders) {
                    if (bidderToPrebidSchains.containsKey(bidder)) {
                        bidderToPrebidSchains.remove(bidder);
                        logger.debug("Schain bidder {0} is rejected since it was defined more than once", bidder);
                        continue;
                    }
                    bidderToPrebidSchains.put(bidder, schain.getSchain());
                }
            }
        }
        return bidderToPrebidSchains;
    }

    /**
     * Returns {@link BidderRequest} for the given bidder.
     */
    private BidderRequest createBidderRequest(BidderPrivacyResult bidderPrivacyResult,
                                              BidRequest bidRequest,
                                              List<Imp> imps,
                                              Map<String, ExtBidderConfigFpd> biddersToConfigs,
                                              Map<String, JsonNode> bidderToPrebidBidders,
                                              Map<String, ExtRequestPrebidSchainSchain> bidderToPrebidSchains) {

        final String bidder = bidderPrivacyResult.getRequestBidder();
        if (bidderPrivacyResult.isBlockedRequestByTcf()) {
            return null;
        }

        final List<String> firstPartyDataBidders = firstPartyDataBidders(bidRequest.getExt());
        final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.contains(bidder);

        final ExtBidderConfigFpd fpdConfig = ObjectUtils.firstNonNull(biddersToConfigs.get(bidder),
                biddersToConfigs.get(ALL_BIDDERS_CONFIG));

        final Site bidRequestSite = bidRequest.getSite();
        final App bidRequestApp = bidRequest.getApp();
        final ObjectNode fpdSite = fpdConfig != null ? fpdConfig.getSite() : null;
        final ObjectNode fpdApp = fpdConfig != null ? fpdConfig.getApp() : null;

        if (bidRequestSite != null && fpdApp != null || bidRequestApp != null && fpdSite != null) {
            logger.info("Request to bidder {0} rejected as both bidRequest.site and bidRequest.app are present"
                    + " after fpd data have been merged", bidder);
            return null;
        }

        return BidderRequest.of(bidder, bidRequest.toBuilder()
                // User was already prepared above
                .user(bidderPrivacyResult.getUser())
                .device(bidderPrivacyResult.getDevice())
                .imp(prepareImps(bidder, imps, useFirstPartyData))
                .app(prepareApp(bidRequestApp, fpdApp, useFirstPartyData))
                .site(prepareSite(bidRequestSite, fpdSite, useFirstPartyData))
                .source(prepareSource(bidder, bidderToPrebidSchains, bidRequest.getSource()))
                .ext(prepareExt(bidder, bidderToPrebidBidders, bidRequest.getExt()))
                .build());
    }

    /**
     * For each given imp creates a new imp with extension crafted to contain only "prebid", "context" and
     * bidder-specific extension.
     */
    private List<Imp> prepareImps(String bidder, List<Imp> imps, boolean useFirstPartyData) {
        return imps.stream()
                .filter(imp -> imp.getExt().hasNonNull(bidder))
                .map(imp -> imp.toBuilder()
                        .ext(prepareImpExt(bidder, imp.getExt(), useFirstPartyData))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Creates a new imp extension for particular bidder having:
     * <ul>
     * <li>"prebid" field populated with an imp.ext.prebid field value, may be null</li>
     * <li>"context" field populated with an imp.ext.context field value, may be null</li>
     * <li>"bidder" field populated with an imp.ext.{bidder} field value, not null</li>
     * </ul>
     */
    private ObjectNode prepareImpExt(String bidder, ObjectNode impExt, boolean useFirstPartyData) {
        final JsonNode impExtPrebid = prepareImpExtPrebid(bidder, impExt.get(PREBID_EXT));
        final ObjectNode result = mapper.mapper().valueToTree(ExtPrebid.of(impExtPrebid, impExt.get(bidder)));

        final JsonNode contextNode = impExt.get(CONTEXT_EXT);
        final boolean isContextNodePresent = contextNode != null && !contextNode.isNull();
        if (isContextNodePresent) {
            final JsonNode contextNodeCopy = contextNode.deepCopy();
            if (!useFirstPartyData && contextNodeCopy.isObject()) {
                ((ObjectNode) contextNodeCopy).remove(DATA);
            }
            result.set(CONTEXT_EXT, contextNodeCopy);
        }
        return result;
    }

    private JsonNode prepareImpExtPrebid(String bidder, JsonNode extImpPrebidNode) {
        if (extImpPrebidNode != null && extImpPrebidNode.hasNonNull(bidder)) {
            final ExtImpPrebid extImpPrebid = extImpPrebid(extImpPrebidNode).toBuilder()
                    .bidder((ObjectNode) extImpPrebidNode.get(bidder)) // leave appropriate bidder related data
                    .build();
            return mapper.mapper().valueToTree(extImpPrebid);
        }
        return extImpPrebidNode;
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
     * Checks whether to pass the app.ext.data depending on request having a first party data
     * allowed for given bidder or not. And merge masked app with fpd config.
     */
    private App prepareApp(App app, ObjectNode fpdApp, boolean useFirstPartyData) {
        final ExtApp appExt = app != null ? app.getExt() : null;

        final App maskedApp = appExt != null && appExt.getData() != null && !useFirstPartyData
                ? app.toBuilder().ext(maskExtApp(appExt)).build()
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
     * Checks whether to pass the site.ext.data depending on request having a first party data
     * allowed for given bidder or not. And merge masked site with fpd config.
     */
    private Site prepareSite(Site site, ObjectNode fpdSite, boolean useFirstPartyData) {
        final ExtSite siteExt = site != null ? site.getExt() : null;

        final Site maskedSite = siteExt != null && siteExt.getData() != null && !useFirstPartyData
                ? site.toBuilder().ext(maskExtSite(siteExt)).build()
                : site;

        return useFirstPartyData
                ? fpdResolver.resolveSite(maskedSite, fpdSite)
                : maskedSite;
    }

    private ExtSite maskExtSite(ExtSite siteExt) {
        final ExtSite maskedExtSite = ExtSite.of(siteExt.getAmp(), null);
        return maskedExtSite.isEmpty() ? null : maskedExtSite;
    }

    /**
     * Returns {@link Source} with corresponding request.ext.prebid.schains.
     */
    private Source prepareSource(String bidder, Map<String, ExtRequestPrebidSchainSchain> bidderToSchain,
                                 Source receivedSource) {
        final ExtRequestPrebidSchainSchain defaultSchain = bidderToSchain.get(GENERIC_SCHAIN_KEY);
        final ExtRequestPrebidSchainSchain bidderSchain = bidderToSchain.getOrDefault(bidder, defaultSchain);

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
                                  ExtRequest requestExt) {

        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final List<ExtRequestPrebidSchain> extPrebidSchains = extPrebid != null ? extPrebid.getSchains() : null;
        final ExtRequestPrebidData extPrebidData = extPrebid != null ? extPrebid.getData() : null;
        final List<ExtRequestPrebidBidderConfig> extPrebidBidderconfig =
                extPrebid != null ? extPrebid.getBidderconfig() : null;

        final boolean suppressSchains = extPrebidSchains != null;
        final boolean suppressBidderConfig = extPrebidBidderconfig != null;
        final boolean suppressPrebidData = extPrebidData != null;

        if (bidderToPrebidBidders.isEmpty() && !suppressSchains && !suppressBidderConfig && !suppressPrebidData) {
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
                        .bidders(bidders)
                        .schains(null)
                        .data(null)
                        .bidderconfig(null)
                        .build());
    }

    /**
     * Updates 'account.*.request', 'request' and 'no_cookie_requests' metrics for each {@link BidderRequest}.
     */
    private List<BidderRequest> updateRequestMetric(List<BidderRequest> bidderRequests,
                                                    UidsCookie uidsCookie,
                                                    BidderAliases aliases,
                                                    String publisherId,
                                                    MetricName requestTypeMetric) {

        metrics.updateRequestBidderCardinalityMetric(bidderRequests.size());
        metrics.updateAccountRequestMetrics(publisherId, requestTypeMetric);

        for (BidderRequest bidderRequest : bidderRequests) {
            final String bidder = aliases.resolveBidder(bidderRequest.getBidder());
            final boolean isApp = bidderRequest.getBidRequest().getApp() != null;
            final boolean noBuyerId = !bidderCatalog.isActive(bidder) || StringUtils.isBlank(
                    uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).getCookieFamilyName()));

            metrics.updateAdapterRequestTypeAndNoCookieMetrics(bidder, requestTypeMetric, !isApp && noBuyerId);
        }

        return bidderRequests;
    }

    private static BigDecimal bidAdjustmentForBidder(BidRequest bidRequest, String bidder) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final Map<String, BigDecimal> bidAdjustmentFactors = prebid != null ? prebid.getBidadjustmentfactors() : null;
        return bidAdjustmentFactors != null ? bidAdjustmentFactors.get(bidder) : null;
    }

    /**
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest,
                                               Timeout timeout,
                                               boolean debugEnabled,
                                               BidderAliases aliases) {

        final String bidderName = bidderRequest.getBidder();
        final Bidder<?> bidder = bidderCatalog.bidderByName(aliases.resolveBidder(bidderName));
        final long startTime = clock.millis();

        return httpBidderRequester.requestBids(bidder, bidderRequest.getBidRequest(), timeout, debugEnabled)
                .map(seatBid -> BidderResponse.of(bidderName, seatBid, responseTime(startTime)));
    }

    private List<BidderResponse> validateAndAdjustBids(
            List<BidderResponse> bidderResponses, AuctionContext auctionContext, BidderAliases aliases) {

        return bidderResponses.stream()
                .map(bidderResponse -> validBidderResponse(bidderResponse, auctionContext, aliases))
                .map(bidderResponse -> applyBidPriceChanges(bidderResponse, auctionContext.getBidRequest()))
                .collect(Collectors.toList());
    }

    /**
     * Validates bid response from exchange.
     * <p>
     * Removes invalid bids from response and adds corresponding error to {@link BidderSeatBid}.
     * <p>
     * Returns input argument as the result if no errors found or creates new {@link BidderResponse} otherwise.
     */
    private BidderResponse validBidderResponse(
            BidderResponse bidderResponse, AuctionContext auctionContext, BidderAliases aliases) {

        final BidRequest bidRequest = auctionContext.getBidRequest();
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

        for (final BidderBid bid : bids) {
            final ValidationResult validationResult =
                    responseBidValidator.validate(bid, bidderResponse.getBidder(), auctionContext, aliases);

            if (validationResult.hasWarnings()) {
                addAsBidderErrors(validationResult.getWarnings(), errors);
            }

            if (validationResult.hasErrors()) {
                addAsBidderErrors(validationResult.getErrors(), errors);
                continue;
            }

            validBids.add(bid);
        }

        return errors.isEmpty()
                ? bidderResponse
                : bidderResponse.with(BidderSeatBid.of(validBids, seatBid.getHttpCalls(), errors));
    }

    private void addAsBidderErrors(List<String> messages, List<BidderError> errors) {
        messages.stream().map(BidderError::generic).forEach(errors::add);
    }

    /**
     * Performs changes on {@link Bid}s price depends on different between adServerCurrency and bidCurrency,
     * and adjustment factor. Will drop bid if currency conversion is needed but not possible.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderResponse} to make sure
     * {@link Bid#getPrice()} is not empty.
     */
    private BidderResponse applyBidPriceChanges(BidderResponse bidderResponse, BidRequest bidRequest) {
        final BidderSeatBid seatBid = bidderResponse.getSeatBid();

        final List<BidderBid> bidderBids = seatBid.getBids();
        if (bidderBids.isEmpty()) {
            return bidderResponse;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids.size());
        final List<BidderError> errors = new ArrayList<>(seatBid.getErrors());

        final String adServerCurrency = bidRequest.getCur().get(0);
        final BigDecimal priceAdjustmentFactor = bidAdjustmentForBidder(bidRequest, bidderResponse.getBidder());
        final Boolean usepbsrates = usepbsrates(bidRequest);

        for (final BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            final String bidCurrency = bidderBid.getBidCurrency();
            final BigDecimal price = bid.getPrice();
            try {
                final BigDecimal priceInAdServerCurrency = currencyService.convertCurrency(
                        price, currencyRates(bidRequest), adServerCurrency,
                        StringUtils.stripToNull(bidCurrency), usepbsrates);

                final BigDecimal adjustedPrice = adjustPrice(priceAdjustmentFactor, priceInAdServerCurrency);

                if (adjustedPrice.compareTo(price) != 0) {
                    bid.setPrice(adjustedPrice);
                }
                updatedBidderBids.add(bidderBid);
            } catch (PreBidException e) {
                errors.add(BidderError.generic(e.getMessage()));
            }
        }

        return bidderResponse.with(BidderSeatBid.of(updatedBidderBids, seatBid.getHttpCalls(), errors));
    }

    private static BigDecimal adjustPrice(BigDecimal priceAdjustmentFactor, BigDecimal price) {
        return priceAdjustmentFactor != null && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                ? price.multiply(priceAdjustmentFactor)
                : price;
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

    /**
     * Updates 'request_time', 'responseTime', 'timeout_request', 'error_requests', 'no_bid_requests',
     * 'prices' metrics for each {@link BidderResponse}.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderResponse} to make sure
     * {@link Bid#getPrice()} is not empty.
     */
    private List<BidderResponse> updateMetricsFromResponses(
            List<BidderResponse> bidderResponses, String publisherId, BidderAliases aliases) {

        for (final BidderResponse bidderResponse : bidderResponses) {
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

        return bidderResponses;
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
}
