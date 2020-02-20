package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.PrivacyEnforcementResult;
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
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Executes an OpenRTB v2.5 Auction.
 */
public class ExchangeService {

    private static final String PREBID_EXT = "prebid";
    private static final String CONTEXT_EXT = "context";
    private static final String ALL_BIDDERS_CONFIG = "*";

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final String GENERIC_SCHAIN_KEY = "*";

    private final long expectedCacheTime;
    private final BidderCatalog bidderCatalog;
    private final StoredResponseProcessor storedResponseProcessor;
    private final PrivacyEnforcementService privacyEnforcementService;
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
        final RoutingContext routingContext = context.getRoutingContext();
        final UidsCookie uidsCookie = context.getUidsCookie();
        final BidRequest bidRequest = context.getBidRequest();
        final Timeout timeout = context.getTimeout();
        final MetricName requestTypeMetric = context.getRequestTypeMetric();
        final Account account = context.getAccount();

        final ExtBidRequest requestExt;
        try {
            requestExt = requestExt(bidRequest);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final List<Imp> imps = bidRequest.getImp();
        final List<SeatBid> storedResponse = new ArrayList<>();
        final Map<String, String> aliases = aliases(requestExt);
        final String publisherId = account.getId();
        final ExtRequestTargeting targeting = targeting(requestExt);
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(targeting, requestExt);
        final Boolean isGdprEnforced = account.getEnforceGdpr();
        final boolean debugEnabled = isDebugEnabled(bidRequest, requestExt);

        return storedResponseProcessor.getStoredResponseResult(imps, aliases, timeout)
                .map(storedResponseResult -> populateStoredResponse(storedResponseResult, storedResponse))
                .compose(impsRequiredRequest -> extractBidderRequests(context, impsRequiredRequest, requestExt,
                        aliases, isGdprEnforced))
                .map(bidderRequests ->
                        updateRequestMetric(bidderRequests, uidsCookie, aliases, publisherId,
                                requestTypeMetric))
                .compose(bidderRequests -> CompositeFuture.join(bidderRequests.stream()
                        .map(bidderRequest -> requestBids(bidderRequest,
                                auctionTimeout(timeout, cacheInfo.isDoCaching()), debugEnabled, aliases,
                                bidAdjustments(requestExt), currencyRates(targeting)))
                        .collect(Collectors.toList())))
                // send all the requests to the bidders and gathers results
                .map(CompositeFuture::<BidderResponse>list)
                // produce response from bidder results
                .map(bidderResponses -> updateMetricsFromResponses(bidderResponses, publisherId))
                .map(bidderResponses ->
                        storedResponseProcessor.mergeWithBidderResponses(bidderResponses, storedResponse, imps))
                .compose(bidderResponses ->
                        bidResponseCreator.create(bidderResponses, bidRequest, targeting, cacheInfo, account, timeout,
                                debugEnabled))
                .compose(bidResponse ->
                        bidResponsePostProcessor.postProcess(routingContext, uidsCookie, bidRequest, bidResponse,
                                account));
    }

    /**
     * Populates storedResponse parameter with stored {@link List<SeatBid>} and returns {@link List<Imp>} for which
     * request to bidders should be performed.
     */
    private List<Imp> populateStoredResponse(StoredResponseResult storedResponseResult, List<SeatBid> storedResponse) {
        storedResponse.addAll(storedResponseResult.getStoredResponse());
        return storedResponseResult.getRequiredRequestImps();
    }

    /**
     * Extracts {@link ExtBidRequest} from {@link BidRequest}.
     */
    private ExtBidRequest requestExt(BidRequest bidRequest) {
        try {
            return bidRequest.getExt() != null
                    ? mapper.mapper().treeToValue(bidRequest.getExt(), ExtBidRequest.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()), e);
        }
    }

    /**
     * Extracts aliases from {@link ExtBidRequest}.
     */
    private static Map<String, String> aliases(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final Map<String, String> aliases = prebid != null ? prebid.getAliases() : null;
        return aliases != null ? aliases : Collections.emptyMap();
    }

    /**
     * Determines debug flag from {@link BidRequest} or {@link ExtBidRequest}.
     */
    private static boolean isDebugEnabled(BidRequest bidRequest, ExtBidRequest extBidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
    }

    /**
     * Extracts bidAdjustments from {@link ExtBidRequest}.
     */
    private static Map<String, BigDecimal> bidAdjustments(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final Map<String, BigDecimal> bidAdjustmentFactors = prebid != null ? prebid.getBidadjustmentfactors() : null;
        return bidAdjustmentFactors != null ? bidAdjustmentFactors : Collections.emptyMap();
    }

    /**
     * Extracts currency rates from {@link ExtRequestTargeting}.
     */
    private static Map<String, Map<String, BigDecimal>> currencyRates(ExtRequestTargeting targeting) {
        return targeting != null && targeting.getCurrency() != null ? targeting.getCurrency().getRates() : null;
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
    private Future<List<BidderRequest>> extractBidderRequests(AuctionContext context, List<Imp> requestedImps,
                                                              ExtBidRequest requestExt, Map<String, String> aliases,
                                                              Boolean isGdprEnforced) {
        // sanity check: discard imps without extension
        final List<Imp> imps = requestedImps.stream()
                .filter(imp -> imp.getExt() != null)
                .collect(Collectors.toList());

        // identify valid bidders and aliases out of imps
        final List<String> bidders = imps.stream()
                .flatMap(imp -> asStream(imp.getExt().fieldNames())
                        .filter(bidder -> !Objects.equals(bidder, PREBID_EXT) && !Objects.equals(bidder, CONTEXT_EXT))
                        .filter(bidder -> isValidBidder(bidder, aliases)))
                .distinct()
                .collect(Collectors.toList());

        return makeBidderRequests(bidders, context, aliases, requestExt, imps, isGdprEnforced);
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Checks if bidder name is valid in case when bidder can also be alias name.
     */
    private boolean isValidBidder(String bidder, Map<String, String> aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
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
    private Future<List<BidderRequest>> makeBidderRequests(
            List<String> bidders, AuctionContext context, Map<String, String> aliases,
            ExtBidRequest requestExt, List<Imp> imps, Boolean isGdprEnforced) {

        final BidRequest bidRequest = context.getBidRequest();
        final ExtUser extUser = extUser(bidRequest.getUser());
        final Map<String, String> uidsBody = uidsFromBody(extUser);

        final List<String> firstPartyDataBidders = firstPartyDataBidders(requestExt);
        final Map<String, ExtBidderConfigFpd> biddersToConfigs = getBiddersToConfigs(requestExt);

        final Map<String, User> bidderToUser = new HashMap<>();
        for (String bidder : bidders) {
            final ExtBidderConfigFpd fpdConfig = ObjectUtils.firstNonNull(biddersToConfigs.get(ALL_BIDDERS_CONFIG),
                    biddersToConfigs.get(bidder));
            final User fpdUser = fpdConfig != null ? fpdConfig.getUser() : null;

            final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.contains(bidder);
            final User preparedUser = prepareUser(bidRequest.getUser(), extUser, bidder, aliases, uidsBody,
                    context.getUidsCookie(), useFirstPartyData, fpdUser);
            bidderToUser.put(bidder, preparedUser);
        }

        return privacyEnforcementService
                .mask(bidderToUser, extUser, bidders, aliases, bidRequest, isGdprEnforced, context.getTimeout())
                .map(bidderToPrivacyEnforcementResult -> getBidderRequests(bidderToPrivacyEnforcementResult,
                        bidRequest, requestExt, imps, firstPartyDataBidders, biddersToConfigs));
    }

    private static Map<String, ExtBidderConfigFpd> getBiddersToConfigs(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final List<ExtRequestPrebidBidderConfig> bidderConfigs = prebid == null ? null : prebid.getBidderconfig();

        if (CollectionUtils.isEmpty(bidderConfigs)) {
            return Collections.emptyMap();
        }

        final Map<String, ExtBidderConfigFpd> bidderToConfig = new HashMap<>();
        for (ExtRequestPrebidBidderConfig config : bidderConfigs) {
            if (config.getBidders().contains(ALL_BIDDERS_CONFIG)) {
                return Collections.singletonMap(ALL_BIDDERS_CONFIG, config.getConfig().getFpd());
            }
            for (String bidder : config.getBidders()) {
                bidderToConfig.putIfAbsent(bidder, config.getConfig().getFpd());
            }
        }
        return bidderToConfig;
    }

    /**
     * Extracts {@link ExtUser} from request.user.ext or returns null if not presents.
     */
    private ExtUser extUser(User user) {
        final ObjectNode userExt = user != null ? user.getExt() : null;
        if (userExt != null) {
            try {
                return mapper.mapper().treeToValue(userExt, ExtUser.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.user.ext: %s", e.getMessage()), e);
            }
        }
        return null;
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
    private static List<String> firstPartyDataBidders(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final ExtRequestPrebidData data = prebid == null ? null : prebid.getData();
        return data == null ? null : data.getBidders();
    }

    /**
     * Returns original {@link User} if user.buyeruid already contains uid value for bidder.
     * Otherwise, returns new {@link User} containing updated {@link ExtUser} and user.buyeruid.
     * <p>
     * Also, removes user.keywords, gender, yob, geo and ext in case bidder does not use first party data.
     */
    private User prepareUser(User user, ExtUser extUser, String bidder, Map<String, String> aliases,
                             Map<String, String> uidsBody, UidsCookie uidsCookie, boolean useFirstPartyData,
                             User fpdUser) {
        if (fpdUser != null) {
            return fpdUser;
        }

        final String updatedBuyerUid = updateUserBuyerUid(user, bidder, aliases, uidsBody, uidsCookie);
        final boolean shouldUpdateUserExt = extUser != null && extUser.getPrebid() != null;
        final boolean shouldRemoveUserFields = !useFirstPartyData && checkUserFieldsHavingValue(user);

        if (updatedBuyerUid != null || shouldRemoveUserFields || shouldUpdateUserExt) {
            final User.UserBuilder userBuilder = user == null ? User.builder() : user.toBuilder();
            if (updatedBuyerUid != null) {
                userBuilder.buyeruid(updatedBuyerUid);
            }

            if (shouldRemoveUserFields) {
                userBuilder
                        .keywords(null)
                        .gender(null)
                        .yob(null)
                        .geo(null)
                        .ext(null);
            } else if (shouldUpdateUserExt) {
                userBuilder.ext(mapper.mapper().valueToTree(extUser.toBuilder().prebid(null).build()));
            }

            return userBuilder.build();
        }
        return user;
    }

    /**
     * Returns updated buyerUid or null if it doesn't need to be updated.
     */
    private String updateUserBuyerUid(User user, String bidder, Map<String, String> aliases,
                                      Map<String, String> uidsBody, UidsCookie uidsCookie) {
        final String buyerUidFromBodyOrCookie = extractUid(uidsBody, uidsCookie, resolveBidder(bidder, aliases));
        final String buyerUidFromUser = user != null ? user.getBuyeruid() : null;

        return StringUtils.isBlank(buyerUidFromUser) && StringUtils.isNotBlank(buyerUidFromBodyOrCookie)
                ? buyerUidFromBodyOrCookie
                : null;
    }

    /**
     * Returns the name associated with bidder if bidder is an alias.
     * If it's not an alias, the bidder is returned.
     */
    private static String resolveBidder(String bidder, Map<String, String> aliases) {
        return aliases.getOrDefault(bidder, bidder);
    }

    /**
     * Extracts UID from uids from body or {@link UidsCookie}.
     */
    private String extractUid(Map<String, String> uidsBody, UidsCookie uidsCookie, String bidder) {
        final String uid = uidsBody.get(bidder);
        return StringUtils.isNotBlank(uid) ? uid : uidsCookie.uidFrom(resolveCookieFamilyName(bidder));
    }

    /**
     * Check whether the User fields should be changed.
     */
    private static boolean checkUserFieldsHavingValue(User user) {
        final boolean hasUser = user != null;
        final String keywords = hasUser ? user.getKeywords() : null;
        final String gender = hasUser ? user.getGender() : null;
        final Integer yob = hasUser ? user.getYob() : null;
        final Geo geo = hasUser ? user.getGeo() : null;
        final ObjectNode ext = hasUser ? user.getExt() : null;
        return ObjectUtils.anyNotNull(keywords, gender, yob, geo, ext);
    }

    /**
     * Extract cookie family name from bidder's {@link Usersyncer} if it is enabled. If not - return null.
     */
    private String resolveCookieFamilyName(String bidder) {
        return bidderCatalog.isActive(bidder) ? bidderCatalog.usersyncerByName(bidder).getCookieFamilyName() : null;
    }

    /**
     * Returns Shuffled List of {@link BidderRequest}
     */
    private List<BidderRequest> getBidderRequests(
            Map<String, PrivacyEnforcementResult> bidderToPrivacyEnforcementResult, BidRequest bidRequest,
            ExtBidRequest requestExt, List<Imp> imps, List<String> firstPartyDataBidders,
            Map<String, ExtBidderConfigFpd> biddersToConfigs) {

        final Map<String, JsonNode> bidderToPrebidBidders = bidderToPrebidBidders(requestExt);
        final Map<String, ObjectNode> bidderToPrebidSchains = bidderToPrebidSchains(requestExt);
        final List<BidderRequest> bidderRequests = bidderToPrivacyEnforcementResult.entrySet().stream()
                // for each bidder create a new request that is a copy of original request except buyerid, imp
                // extensions, ext.prebid.data.bidders and ext.prebid.bidders.
                // Also, check whether to pass user.ext.data, app.ext.data and site.ext.data or not.
                .map(entry -> createBidderRequest(entry.getKey(), bidRequest, requestExt, imps, entry.getValue(),
                        firstPartyDataBidders, biddersToConfigs, bidderToPrebidBidders, bidderToPrebidSchains))
                .collect(Collectors.toList());
        Collections.shuffle(bidderRequests);
        return bidderRequests;
    }

    /**
     * Extracts a map of bidders to their arguments from {@link ObjectNode} prebid.bidders.
     */
    private static Map<String, JsonNode> bidderToPrebidBidders(ExtBidRequest requestExt) {
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
    private static Map<String, ObjectNode> bidderToPrebidSchains(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final List<ExtRequestPrebidSchain> schains = prebid == null ? null : prebid.getSchains();

        if (CollectionUtils.isEmpty(schains)) {
            return Collections.emptyMap();
        }

        final Map<String, ObjectNode> bidderToPrebidSchains = new HashMap<>();
        for (ExtRequestPrebidSchain schain : schains) {
            final List<String> schainBidders = schain.getBidders();
            if (CollectionUtils.isNotEmpty(schainBidders)) {
                schainBidders.forEach(bidder -> bidderToPrebidSchains.put(bidder, schain.getSchain()));
            }
        }
        return bidderToPrebidSchains;
    }

    /**
     * Returns created {@link BidderRequest}
     */
    private BidderRequest createBidderRequest(
            String bidder, BidRequest bidRequest, ExtBidRequest requestExt, List<Imp> imps,
            PrivacyEnforcementResult privacyEnforcementResult, List<String> firstPartyDataBidders,
            Map<String, ExtBidderConfigFpd> biddersToConfigs, Map<String, JsonNode> bidderToPrebidBidders,
            Map<String, ObjectNode> bidderToPrebidSchains) {

        final boolean useFirstPartyData = firstPartyDataBidders == null || firstPartyDataBidders.contains(bidder);
        final ExtBidderConfigFpd fpdConfig = ObjectUtils.firstNonNull(biddersToConfigs.get(ALL_BIDDERS_CONFIG),
                biddersToConfigs.get(bidder));
        final boolean hasBidderConfig = fpdConfig != null;

        return BidderRequest.of(bidder, bidRequest.toBuilder()
                .user(privacyEnforcementResult.getUser())
                .device(privacyEnforcementResult.getDevice())
                .imp(prepareImps(bidder, imps, useFirstPartyData))
                .app(hasBidderConfig ? fpdConfig.getApp() : prepareApp(bidRequest.getApp(), useFirstPartyData))
                .site(hasBidderConfig ? fpdConfig.getSite() : prepareSite(bidRequest.getSite(), useFirstPartyData))
                .source(prepareSource(bidder, bidderToPrebidSchains, bidRequest.getSource()))
                .ext(prepareExt(bidder, firstPartyDataBidders, bidderToPrebidBidders, requestExt, bidRequest.getExt()))
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
        final ObjectNode result = mapper.mapper().valueToTree(ExtPrebid.of(impExt.get(PREBID_EXT), impExt.get(bidder)));

        if (useFirstPartyData) {
            result.set(CONTEXT_EXT, impExt.get(CONTEXT_EXT));
        }

        return result;
    }

    /**
     * Checks whether to pass the app.keywords and ext depending on request having a first party data
     * allowed for given bidder or not.
     */
    private static App prepareApp(App app, boolean useFirstPartyData) {
        return app != null && !useFirstPartyData
                ? app.toBuilder().keywords(null).ext(null).build()
                : app;
    }

    /**
     * Checks whether to pass the site.keywords, search and ext depending on request having a first party data
     * allowed for given bidder or not.
     */
    private static Site prepareSite(Site site, boolean useFirstPartyData) {
        return site != null && !useFirstPartyData
                ? site.toBuilder().keywords(null).search(null).ext(null).build()
                : site;
    }

    /**
     * Make Source with corresponding request.ext.prebid.schains
     */
    private Source prepareSource(String bidder, Map<String, ObjectNode> bidderToSchain, Source receivedSource) {
        final ObjectNode defaultSchain = bidderToSchain.get(GENERIC_SCHAIN_KEY);
        final ObjectNode bidderSchain = bidderToSchain.getOrDefault(bidder, defaultSchain);

        if (bidderSchain == null || bidderSchain.isNull()) {
            return receivedSource;
        }

        final ObjectNode jsonExtSource = mapper.mapper().valueToTree(ExtSource.of(bidderSchain));

        if (receivedSource == null) {
            return Source.builder().ext(jsonExtSource).build();
        } else {
            return receivedSource.toBuilder().ext(jsonExtSource).build();
        }
    }

    /**
     * Removes all bidders except the given bidder from bidrequest.ext.prebid.data.bidders and
     * bidrequest.ext.prebid.bidders to hide list of allowed bidders from initial request.
     * Also mask bidrequest.ext.prebid.schains.
     */
    private ObjectNode prepareExt(String bidder, List<String> firstPartyDataBidders,
                                  Map<String, JsonNode> bidderToPrebidBidders, ExtBidRequest requestExt,
                                  ObjectNode requestExtNode) {
        final ExtRequestPrebid extPrebid = requestExt != null ? requestExt.getPrebid() : null;
        final List<ExtRequestPrebidSchain> extPrebidSchains = extPrebid != null ? extPrebid.getSchains() : null;
        final List<ExtRequestPrebidBidderConfig> extConfig = extPrebid != null ? extPrebid.getBidderconfig() : null;
        final boolean suppressSchains = extPrebidSchains != null;
        final boolean suppressBidderConfig = extConfig != null;
        final boolean suppressPrebidData = firstPartyDataBidders != null;

        if (!suppressPrebidData && bidderToPrebidBidders.isEmpty() && !suppressSchains && !suppressBidderConfig) {
            return requestExtNode;
        }

        final ExtRequestPrebidData prebidData = suppressPrebidData && firstPartyDataBidders.contains(bidder)
                ? ExtRequestPrebidData.of(Collections.singletonList(bidder))
                : null;

        final JsonNode prebidParameters = bidderToPrebidBidders.get(bidder);
        final ObjectNode bidders = prebidParameters != null
                ? mapper.mapper().valueToTree(ExtPrebidBidders.of(prebidParameters))
                : null;

        final ExtRequestPrebid.ExtRequestPrebidBuilder extPrebidBuilder = extPrebid != null
                ? extPrebid.toBuilder()
                : ExtRequestPrebid.builder();

        return mapper.mapper().valueToTree(ExtBidRequest.of(extPrebidBuilder
                .data(prebidData)
                .bidders(bidders)
                .bidderconfig(null)
                .schains(null)
                .build()));
    }

    /**
     * Updates 'account.*.request', 'request' and 'no_cookie_requests' metrics for each {@link BidderRequest}.
     */
    private List<BidderRequest> updateRequestMetric(List<BidderRequest> bidderRequests, UidsCookie uidsCookie,
                                                    Map<String, String> aliases, String publisherId,
                                                    MetricName requestTypeMetric) {
        metrics.updateAccountRequestMetrics(publisherId, requestTypeMetric);

        for (BidderRequest bidderRequest : bidderRequests) {
            final String bidder = resolveBidder(bidderRequest.getBidder(), aliases);
            final boolean isApp = bidderRequest.getBidRequest().getApp() != null;
            final boolean noBuyerId = !bidderCatalog.isActive(bidder) || StringUtils.isBlank(
                    uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).getCookieFamilyName()));

            metrics.updateAdapterRequestTypeAndNoCookieMetrics(bidder, requestTypeMetric, !isApp && noBuyerId);
        }
        return bidderRequests;
    }

    /**
     * Extracts {@link ExtRequestTargeting} from {@link ExtBidRequest} model.
     */
    private static ExtRequestTargeting targeting(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        return prebid != null ? prebid.getTargeting() : null;
    }

    /**
     * Creates {@link BidRequestCacheInfo} based on {@link ExtBidRequest} model.
     */
    private BidRequestCacheInfo bidRequestCacheInfo(ExtRequestTargeting targeting, ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
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
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest, Timeout timeout,
                                               boolean debugEnabled, Map<String, String> aliases,
                                               Map<String, BigDecimal> bidAdjustments,
                                               Map<String, Map<String, BigDecimal>> currencyConversionRates) {
        final String bidderName = bidderRequest.getBidder();
        final BigDecimal bidPriceAdjustmentFactor = bidAdjustments.get(bidderName);
        final List<String> cur = bidderRequest.getBidRequest().getCur();
        final String adServerCurrency = cur.get(0);
        final Bidder<?> bidder = bidderCatalog.bidderByName(resolveBidder(bidderName, aliases));
        final long startTime = clock.millis();

        return httpBidderRequester.requestBids(bidder, bidderRequest.getBidRequest(), timeout, debugEnabled)
                .map(bidderSeatBid -> validBidderSeatBid(bidderSeatBid, cur))
                .map(seat -> applyBidPriceChanges(seat, currencyConversionRates, adServerCurrency,
                        bidPriceAdjustmentFactor))
                .map(result -> BidderResponse.of(bidderName, result, responseTime(startTime)));
    }

    /**
     * Validates bid response from exchange.
     * <p>
     * Removes invalid bids from response and adds corresponding error to {@link BidderSeatBid}.
     * <p>
     * Returns input argument as the result if no errors found or create new {@link BidderSeatBid} otherwise.
     */
    private BidderSeatBid validBidderSeatBid(BidderSeatBid bidderSeatBid, List<String> requestCurrencies) {
        final List<BidderBid> bids = bidderSeatBid.getBids();

        final List<BidderBid> validBids = new ArrayList<>(bids.size());
        final List<BidderError> errors = new ArrayList<>(bidderSeatBid.getErrors());

        if (requestCurrencies.size() > 1) {
            errors.add(BidderError.badInput(
                    String.format("Cur parameter contains more than one currency. %s will be used",
                            requestCurrencies.get(0))));
        }

        for (BidderBid bid : bids) {
            final ValidationResult validationResult = responseBidValidator.validate(bid.getBid());
            if (validationResult.hasErrors()) {
                for (String error : validationResult.getErrors()) {
                    errors.add(BidderError.generic(error));
                }
            } else {
                validBids.add(bid);
            }
        }

        return errors.isEmpty() ? bidderSeatBid : BidderSeatBid.of(validBids, bidderSeatBid.getHttpCalls(), errors);
    }

    /**
     * Performs changes on {@link Bid}s price depends on different between adServerCurrency and bidCurrency,
     * and adjustment factor. Will drop bid if currency conversion is needed but not possible.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderSeatBid(BidderSeatBid, List)}
     * to make sure {@link Bid#getPrice()} is not empty.
     */
    private BidderSeatBid applyBidPriceChanges(BidderSeatBid bidderSeatBid,
                                               Map<String, Map<String, BigDecimal>> requestCurrencyRates,
                                               String adServerCurrency, BigDecimal priceAdjustmentFactor) {
        final List<BidderBid> bidderBids = bidderSeatBid.getBids();
        if (bidderBids.isEmpty()) {
            return bidderSeatBid;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids.size());
        final List<BidderError> errors = new ArrayList<>(bidderSeatBid.getErrors());

        for (final BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            final String bidCurrency = bidderBid.getBidCurrency();
            final BigDecimal price = bid.getPrice();
            try {
                final BigDecimal finalPrice =
                        currencyService.convertCurrency(price, requestCurrencyRates, adServerCurrency, bidCurrency);

                final BigDecimal adjustedPrice = priceAdjustmentFactor != null
                        && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                        ? finalPrice.multiply(priceAdjustmentFactor)
                        : finalPrice;

                if (adjustedPrice.compareTo(price) != 0) {
                    bid.setPrice(adjustedPrice);
                }
                updatedBidderBids.add(bidderBid);
            } catch (PreBidException ex) {
                errors.add(BidderError.generic(
                        String.format("Unable to covert bid currency %s to desired ad server currency %s. %s",
                                bidCurrency, adServerCurrency, ex.getMessage())));
            }
        }

        return BidderSeatBid.of(updatedBidderBids, bidderSeatBid.getHttpCalls(), errors);
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
     * This method should always be invoked after {@link ExchangeService#validBidderSeatBid(BidderSeatBid, List)}
     * to make sure {@link Bid#getPrice()} is not empty.
     */
    private List<BidderResponse> updateMetricsFromResponses(List<BidderResponse> bidderResponses, String publisherId) {
        for (final BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();

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
