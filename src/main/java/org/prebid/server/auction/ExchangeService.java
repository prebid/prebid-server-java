package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.model.CacheContext;
import org.prebid.server.cache.model.CacheHttpCall;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.events.EventsService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Executes an OpenRTB v2.5 Auction.
 */
public class ExchangeService {

    private static final String PREBID_EXT = "prebid";
    private static final String CONTEXT_EXT = "context";

    private static final String CACHE = "cache";
    private static final String DEFAULT_CURRENCY = "USD";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final BidderCatalog bidderCatalog;
    private final StoredResponseProcessor storedResponseProcessor;
    private final HttpBidderRequester httpBidderRequester;
    private final ResponseBidValidator responseBidValidator;
    private final CacheService cacheService;
    private final BidResponsePostProcessor bidResponsePostProcessor;
    private final CurrencyConversionService currencyService;
    private final GdprService gdprService;
    private final EventsService eventsService;
    private final Metrics metrics;
    private final Clock clock;
    private final boolean useGeoLocation;
    private final long expectedCacheTime;

    public ExchangeService(BidderCatalog bidderCatalog, StoredResponseProcessor storedResponseProcessor,
                           HttpBidderRequester httpBidderRequester, ResponseBidValidator responseBidValidator,
                           CacheService cacheService, BidResponsePostProcessor bidResponsePostProcessor,
                           CurrencyConversionService currencyService, GdprService gdprService,
                           EventsService eventsService, Metrics metrics, Clock clock, boolean useGeoLocation,
                           long expectedCacheTime) {
        if (expectedCacheTime < 0) {
            throw new IllegalArgumentException("Expected cache time should be positive");
        }
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.storedResponseProcessor = Objects.requireNonNull(storedResponseProcessor);
        this.httpBidderRequester = Objects.requireNonNull(httpBidderRequester);
        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.currencyService = currencyService;
        this.bidResponsePostProcessor = Objects.requireNonNull(bidResponsePostProcessor);
        this.gdprService = gdprService;
        this.eventsService = eventsService;
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.useGeoLocation = useGeoLocation;
        this.expectedCacheTime = expectedCacheTime;
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

        final ExtBidRequest requestExt;
        try {
            requestExt = requestExt(bidRequest);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final Map<String, String> aliases = aliases(requestExt);
        final String publisherId = publisherId(bidRequest);
        final ExtRequestTargeting targeting = targeting(requestExt);
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(targeting, requestExt);
        final boolean isApp = bidRequest.getApp() != null;
        final TargetingKeywordsCreator keywordsCreator = keywordsCreator(targeting, isApp);
        final Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType =
                keywordsCreatorByBidType(targeting, isApp);
        final boolean debugEnabled = isDebugEnabled(bidRequest, requestExt);
        final long startTime = clock.millis();
        final List<SeatBid> storedResponse = new ArrayList<>();

        return storedResponseProcessor
                .getStoredResponseResult(bidRequest.getImp(), timeout, aliases)
                .map(storedResponseResult -> populateStoredResponse(storedResponseResult, storedResponse))
                .compose(impsRequiredRequest -> extractBidderRequests(bidRequest, impsRequiredRequest, requestExt,
                        uidsCookie, aliases, publisherId, timeout))
                .map(bidderRequests ->
                        updateRequestMetric(bidderRequests, uidsCookie, aliases, publisherId,
                                requestTypeMetric))
                .compose(bidderRequests -> CompositeFuture.join(bidderRequests.stream()
                        .map(bidderRequest -> requestBids(bidderRequest, startTime,
                                auctionTimeout(timeout, cacheInfo.doCaching), debugEnabled, aliases,
                                bidAdjustments(requestExt), currencyRates(targeting)))
                        .collect(Collectors.toList())))
                // send all the requests to the bidders and gathers results
                .map(CompositeFuture::<BidderResponse>list)
                // produce response from bidder results
                .map(bidderResponses -> updateMetricsFromResponses(bidderResponses, publisherId))
                .map(bidderResponses -> storedResponseProcessor.mergeWithBidderResponses(bidderResponses,
                        storedResponse, bidRequest.getImp()))
                .compose(bidderResponses -> eventsService.isEventsEnabled(publisherId, timeout)
                        .map(eventsEnabled -> Tuple2.of(bidderResponses, eventsEnabled)))
                .compose((Tuple2<List<BidderResponse>, Boolean> result) ->
                        toBidResponse(result.getLeft(), bidRequest, keywordsCreator,
                                keywordsCreatorByBidType, cacheInfo, publisherId, timeout,
                                result.getRight(), debugEnabled))
                .compose(bidResponse ->
                        bidResponsePostProcessor.postProcess(routingContext, uidsCookie, bidRequest,
                                bidResponse));
    }

    /**
     * Populates storedResponse parameter with stored {@link List<SeatBid>} and returns {@link List<Imp>} for which
     * request to bidders should be performed.
     */
    private List<Imp> populateStoredResponse(StoredResponseResult storedResponseResult, List<SeatBid> storedResponse) {
        storedResponseResult.getStoredResponse().stream().collect(Collectors.toCollection(() -> storedResponse));
        return storedResponseResult.getRequiredRequestImps();
    }

    /**
     * Extracts {@link ExtBidRequest} from {@link BidRequest}.
     */
    private static ExtBidRequest requestExt(BidRequest bidRequest) {
        try {
            return bidRequest.getExt() != null
                    ? Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class) : null;
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
     * Extracts publisher id either from {@link BidRequest}.app.publisher.id or {@link BidRequest}.site.publisher.id.
     * If neither is present returns empty string.
     */
    private static String publisherId(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final Publisher appPublisher = app != null ? app.getPublisher() : null;
        final Site site = bidRequest.getSite();
        final Publisher sitePublisher = site != null ? site.getPublisher() : null;

        final Publisher publisher = ObjectUtils.firstNonNull(appPublisher, sitePublisher);

        final String publisherId = publisher != null ? publisher.getId() : null;
        return ObjectUtils.defaultIfNull(publisherId, StringUtils.EMPTY);
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
    private Future<List<BidderRequest>> extractBidderRequests(BidRequest bidRequest, List<Imp> requestedImps,
                                                              ExtBidRequest requestExt, UidsCookie uidsCookie,
                                                              Map<String, String> aliases, String publisherId,
                                                              Timeout timeout) {
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

        final ExtUser extUser = extUser(bidRequest.getUser());
        final ExtRegs extRegs = extRegs(bidRequest.getRegs());

        return getVendorsToGdprPermission(bidRequest, bidders, aliases, publisherId, extUser, extRegs, timeout)
                .map(vendorsToGdpr -> makeBidderRequests(bidders, aliases, bidRequest, requestExt, uidsCookie,
                        extUser, extRegs, imps, vendorsToGdpr));
    }

    /**
     * Checks if bidder name is valid in case when bidder can also be alias name.
     */
    private boolean isValidBidder(String bidder, Map<String, String> aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
    }

    /**
     * Returns {@link Future&lt;{@link Map}&lt;{@link Integer}, {@link Boolean}&gt;&gt;}, where bidders vendor id mapped
     * to enabling or disabling GDPR in scope of pbs server. If bidder vendor id is not present in map, it means that
     * pbs not enforced particular bidder to follow pbs GDPR procedure.
     */
    private Future<Map<Integer, Boolean>> getVendorsToGdprPermission(BidRequest bidRequest, List<String> bidders,
                                                                     Map<String, String> aliases,
                                                                     String publisherId, ExtUser extUser,
                                                                     ExtRegs extRegs, Timeout timeout) {
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdprAsString = gdpr != null ? gdpr.toString() : null;
        final String gdprConsent = extUser != null ? extUser.getConsent() : null;
        final Device device = bidRequest.getDevice();
        final String ipAddress = useGeoLocation && device != null ? device.getIp() : null;
        final Set<Integer> vendorIds = extractGdprEnforcedVendors(bidders, aliases);

        return gdprService.isGdprEnforced(gdprAsString, publisherId, vendorIds, timeout)
                .compose(gdprEnforced -> !gdprEnforced
                        ? Future.succeededFuture(Collections.emptyMap())
                        : gdprService.resultByVendor(vendorIds, gdprAsString, gdprConsent, ipAddress, timeout)
                        .map(GdprResponse::getVendorsToGdpr));
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
     * - bidrequest.user.ext.data, bidrequest.app.ext.data and bidrequest.site.ext.data will be removed for bidders
     * that don't have first party data allowed.
     */
    private List<BidderRequest> makeBidderRequests(List<String> bidders, Map<String, String> aliases,
                                                   BidRequest bidRequest, ExtBidRequest requestExt,
                                                   UidsCookie uidsCookie, ExtUser extUser, ExtRegs extRegs,
                                                   List<Imp> imps, Map<Integer, Boolean> vendorsToGdpr) {

        final Map<String, String> uidsBody = uidsFromBody(extUser);

        final Regs regs = bidRequest.getRegs();
        final boolean coppaMasking = isCoppaMaskingRequired(regs);

        final Device device = bidRequest.getDevice();
        final Integer deviceLmt = device != null ? device.getLmt() : null;
        final Map<String, Boolean> bidderToGdprMasking = bidders.stream()
                .collect(Collectors.toMap(Function.identity(),
                        bidder -> isGdprMaskingRequiredFor(bidder, aliases, vendorsToGdpr, deviceLmt)));

        final List<String> firstPartyDataBidders = firstPartyDataBidders(requestExt);
        final App app = bidRequest.getApp();
        final ExtApp extApp = extApp(app);
        final Site site = bidRequest.getSite();
        final ExtSite extSite = extSite(site);

        final List<BidderRequest> bidderRequests = bidders.stream()
                // for each bidder create a new request that is a copy of original request except buyerid, imp
                // extensions and ext.prebid.data.bidders.
                // Also, check whether to pass user.ext.data, app.ext.data and site.ext.data or not.
                .map(bidder -> BidderRequest.of(bidder, bidRequest.toBuilder()
                        .user(prepareUser(bidRequest.getUser(), extUser, bidder, aliases, uidsBody, uidsCookie,
                                firstPartyDataBidders.contains(bidder), coppaMasking, bidderToGdprMasking.get(bidder)))
                        .device(prepareDevice(device, coppaMasking, bidderToGdprMasking.get(bidder)))
                        .regs(prepareRegs(regs, extRegs, bidderToGdprMasking.get(bidder)))
                        .imp(prepareImps(bidder, imps, firstPartyDataBidders.contains(bidder)))
                        .app(prepareApp(app, extApp, firstPartyDataBidders.contains(bidder)))
                        .site(prepareSite(site, extSite, firstPartyDataBidders.contains(bidder)))
                        .ext(cleanExtPrebidDataBidders(bidder, firstPartyDataBidders, requestExt, bidRequest.getExt()))
                        .build()))
                .collect(Collectors.toList());

        // randomize the list to make the auction more fair
        Collections.shuffle(bidderRequests);

        return bidderRequests;
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
     * Determines if COPPA is required.
     */
    private static boolean isCoppaMaskingRequired(Regs regs) {
        return regs != null && Objects.equals(regs.getCoppa(), 1);
    }

    /**
     * Returns flag if GDPR masking is required for bidder.
     */
    private boolean isGdprMaskingRequiredFor(String bidder, Map<String, String> aliases,
                                             Map<Integer, Boolean> vendorToGdprPermission, Integer deviceLmt) {
        final boolean maskingRequired;
        final boolean isLmtEnabled = deviceLmt != null && deviceLmt.equals(1);
        if (vendorToGdprPermission.isEmpty() && !isLmtEnabled) {
            maskingRequired = false;
        } else {
            final String resolvedBidderName = resolveBidder(bidder, aliases);
            final Boolean gdprAllowsUserData = vendorToGdprPermission.get(
                    bidderCatalog.bidderInfoByName(resolvedBidderName).getGdpr().getVendorId());

            // if bidder was not found in vendorToGdprPermission, it means that it was not enforced for GDPR,
            // so request for this bidder should be sent without changes
            maskingRequired = (gdprAllowsUserData != null && !gdprAllowsUserData) || isLmtEnabled;

            if (maskingRequired) {
                metrics.updateGdprMaskedMetric(resolvedBidderName);
            }
        }
        return maskingRequired;
    }

    /**
     * Returns the name associated with bidder if bidder is an alias.
     * If it's not an alias, the bidder is returned.
     */
    private static String resolveBidder(String bidder, Map<String, String> aliases) {
        return aliases.getOrDefault(bidder, bidder);
    }

    /**
     * Extracts a list of bidders for which first party data is allowed from {@link ExtRequestPrebidData} model.
     */
    private static List<String> firstPartyDataBidders(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt == null ? null : requestExt.getPrebid();
        final ExtRequestPrebidData data = prebid == null ? null : prebid.getData();
        final List<String> bidders = data == null ? null : data.getBidders();
        return ObjectUtils.defaultIfNull(bidders, Collections.emptyList());
    }

    /**
     * Extracts {@link ExtApp} from {@link App}.
     */
    private static ExtApp extApp(App app) {
        final ObjectNode appExt = app == null ? null : app.getExt();
        if (appExt != null) {
            try {
                return Json.mapper.treeToValue(appExt, ExtApp.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.app.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    /**
     * Extracts {@link ExtSite} from {@link Site}.
     */
    private static ExtSite extSite(Site site) {
        final ObjectNode siteExt = site == null ? null : site.getExt();
        if (siteExt != null) {
            try {
                return Json.mapper.treeToValue(siteExt, ExtSite.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.site.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    /**
     * Checks whether to pass the app.ext.data depending on request having a first party data
     * allowed for given bidder or not.
     */
    private static App prepareApp(App app, ExtApp extApp, boolean useFirstPartyData) {
        final ObjectNode extSiteDataNode = extApp == null ? null : extApp.getData();

        return app != null && extSiteDataNode != null && !useFirstPartyData
                ? app.toBuilder().ext(Json.mapper.valueToTree(ExtApp.of(extApp.getPrebid(), null))).build()
                : app;
    }

    /**
     * Checks whether to pass the site.ext.data depending on request having a first party data
     * allowed for given bidder or not.
     */
    private static Site prepareSite(Site site, ExtSite extSite, boolean useFirstPartyData) {
        final ObjectNode extSiteDataNode = extSite == null ? null : extSite.getData();

        return site != null && extSiteDataNode != null && !useFirstPartyData
                ? site.toBuilder().ext(Json.mapper.valueToTree(ExtSite.of(extSite.getAmp(), null))).build()
                : site;
    }

    /**
     * Removes all bidders except the given bidder from bidrequest.ext.prebid.data.bidders
     * to hide list of allowed bidders from initial request.
     */
    private static ObjectNode cleanExtPrebidDataBidders(String bidder, List<String> firstPartyDataBidders,
                                                        ExtBidRequest requestExt, ObjectNode requestExtNode) {
        if (firstPartyDataBidders.isEmpty()) {
            return requestExtNode;
        }

        final ExtRequestPrebidData prebidData = firstPartyDataBidders.contains(bidder)
                ? ExtRequestPrebidData.of(Collections.singletonList(bidder))
                : null;
        return Json.mapper.valueToTree(ExtBidRequest.of(requestExt.getPrebid().toBuilder()
                .data(prebidData)
                .build()));
    }

    /**
     * Extracts {@link ExtRegs} from {@link Regs}.
     */
    private static ExtRegs extRegs(Regs regs) {
        final ObjectNode regsExt = regs != null ? regs.getExt() : null;
        if (regsExt != null) {
            try {
                return Json.mapper.treeToValue(regsExt, ExtRegs.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.regs.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    /**
     * Extracts GDPR enforced vendor IDs.
     */
    private Set<Integer> extractGdprEnforcedVendors(List<String> bidders, Map<String, String> aliases) {
        return bidders.stream()
                .map(bidder -> bidderCatalog.bidderInfoByName(resolveBidder(bidder, aliases)).getGdpr())
                .filter(BidderInfo.GdprInfo::isEnforced)
                .map(BidderInfo.GdprInfo::getVendorId)
                .collect(Collectors.toSet());
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Extracts {@link ExtUser} from request.user.ext or returns null if not presents.
     */
    private static ExtUser extUser(User user) {
        final ObjectNode userExt = user != null ? user.getExt() : null;
        if (userExt != null) {
            try {
                return Json.mapper.treeToValue(userExt, ExtUser.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.user.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    /**
     * Returns original {@link User} if user.buyeruid already contains uid value for bidder.
     * Otherwise, returns new {@link User} containing updated {@link ExtUser} and user.buyeruid.
     * <p>
     * Also, applies COPPA, GDPR and First Data Party processing.
     */
    private User prepareUser(User user, ExtUser extUser, String bidder, Map<String, String> aliases,
                             Map<String, String> uidsBody, UidsCookie uidsCookie,
                             boolean useFirstPartyData, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {

        final ObjectNode updatedExt = updateUserExt(extUser, useFirstPartyData);
        final String updatedBuyerUid = !coppaMaskingRequired && !gdprMaskingRequired
                ? updateUserBuyerUid(user, bidder, aliases, uidsBody, uidsCookie)
                : null;

        if (updatedExt != null || updatedBuyerUid != null || coppaMaskingRequired || gdprMaskingRequired) {
            final User.UserBuilder builder;
            if (user != null) {
                builder = user.toBuilder();

                if (updatedExt != null) {
                    builder.ext(updatedExt);
                }

                // clean user.id, user.yob, and user.gender (COPPA masking)
                if (coppaMaskingRequired) {
                    builder
                            .id(null)
                            .yob(null)
                            .gender(null);
                }

                // clean user.buyeruid and user.geo (COPPA and GDPR masking)
                if (coppaMaskingRequired || gdprMaskingRequired) {
                    builder
                            .buyeruid(null)
                            .geo(coppaMaskingRequired ? maskGeoForCoppa(user.getGeo()) : maskGeoForGdpr(user.getGeo()));
                }
            } else {
                builder = User.builder();
            }

            if (updatedBuyerUid != null) {
                builder.buyeruid(updatedBuyerUid);
            }

            return builder.build();
        }

        return user;
    }

    /**
     * Returns json encoded {@link ObjectNode} of {@link ExtUser} with changes applied:
     * <p>
     * - Removes request.user.ext.prebid.buyeruids to avoid leaking of buyeruids across bidders.
     * <p>
     * - Removes request.user.ext.data if bidder doesn't allow first party data to be passed.
     * <p>
     * Returns null if {@link ExtUser} doesn't need to be updated.
     */
    private static ObjectNode updateUserExt(ExtUser extUser, boolean useFirstPartyData) {
        if (extUser != null) {
            final boolean removePrebid = extUser.getPrebid() != null;
            final boolean removeFirstPartyData = !useFirstPartyData && extUser.getData() != null;

            if (removePrebid || removeFirstPartyData) {
                final ExtUser.ExtUserBuilder builder = extUser.toBuilder();

                if (removePrebid) {
                    builder.prebid(null);
                }
                if (removeFirstPartyData) {
                    builder.data(null);
                }

                return Json.mapper.valueToTree(builder.build());
            }
        }
        return null;
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
     * Returns masked for COPPA {@link Geo}.
     */
    private static Geo maskGeoForCoppa(Geo geo) {
        final Geo updatedGeo = geo != null
                ? geo.toBuilder().lat(null).lon(null).metro(null).city(null).zip(null).build()
                : null;
        return updatedGeo == null || updatedGeo.equals(Geo.EMPTY) ? null : updatedGeo;
    }

    /**
     * Returns masked for GDPR {@link Geo} by rounding lon and lat properties.
     */
    private static Geo maskGeoForGdpr(Geo geo) {
        return geo != null
                ? geo.toBuilder()
                .lat(maskGeoCoordinate(geo.getLat()))
                .lon(maskGeoCoordinate(geo.getLon()))
                .build()
                : null;
    }

    /**
     * Returns masked geo coordinate with rounded value to two decimals.
     */
    private static Float maskGeoCoordinate(Float coordinate) {
        return coordinate != null ? Float.valueOf(ROUND_TWO_DECIMALS.format(coordinate)) : null;
    }

    /**
     * Prepares device, suppresses device information if COPPA or GDPR masking is required.
     */
    private static Device prepareDevice(Device device, boolean coppaMaskingRequired, boolean gdprMaskingRequired) {
        return device != null && (coppaMaskingRequired || gdprMaskingRequired)
                ? device.toBuilder()
                .ip(maskIpv4(device.getIp()))
                .ipv6(maskIpv6(device.getIpv6()))
                .geo(coppaMaskingRequired ? maskGeoForCoppa(device.getGeo()) : maskGeoForGdpr(device.getGeo()))
                .ifa(null)
                .macsha1(null).macmd5(null)
                .dpidsha1(null).dpidmd5(null)
                .didsha1(null).didmd5(null)
                .build()
                : device;
    }

    /**
     * Masks ip v4 address by replacing last group with zero.
     */
    private static String maskIpv4(String ip) {
        return maskIp(ip, '.');
    }

    /**
     * Masks ip v6 address by replacing last group with zero.
     */
    private static String maskIpv6(String ip) {
        return maskIp(ip, ':');
    }

    /**
     * Masks ip address by replacing bits after last separator with zero.
     */
    private static String maskIp(String ip, char delimiter) {
        return StringUtils.isNotEmpty(ip) ? ip.substring(0, ip.lastIndexOf(delimiter) + 1) + "0" : ip;
    }

    /**
     * Sets GDPR value 1, if bidder required GDPR masking, but regs.ext.gdpr is not defined.
     */
    private static Regs prepareRegs(Regs regs, ExtRegs extRegs, boolean gdprMaskingRequired) {
        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;

        return gdpr == null && gdprMaskingRequired
                ? Regs.of(regs != null ? regs.getCoppa() : null, Json.mapper.valueToTree(ExtRegs.of(1)))
                : regs;
    }

    /**
     * For each given imp creates a new imp with extension crafted to contain only "prebid", "context" and
     * bidder-specific extension.
     */
    private static List<Imp> prepareImps(String bidder, List<Imp> imps, boolean useFirstPartyData) {
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
    private static ObjectNode prepareImpExt(String bidder, ObjectNode impExt, boolean useFirstPartyData) {
        final ObjectNode result = Json.mapper.valueToTree(ExtPrebid.of(impExt.get(PREBID_EXT), impExt.get(bidder)));

        if (useFirstPartyData) {
            result.set(CONTEXT_EXT, impExt.get(CONTEXT_EXT));
        }

        return result;
    }

    /**
     * Extracts UID from uids from body or {@link UidsCookie}. If absent returns null.
     */
    private String extractUid(Map<String, String> uidsBody, UidsCookie uidsCookie, String bidder) {
        final String uid = uidsBody.get(bidder);
        return StringUtils.isNotBlank(uid)
                ? uid
                : uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).getCookieFamilyName());
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
    private static BidRequestCacheInfo bidRequestCacheInfo(ExtRequestTargeting targeting, ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidCache cache = prebid != null ? prebid.getCache() : null;

        if (targeting != null && cache != null) {
            final boolean shouldCacheBids = cache.getBids() != null;
            final boolean shouldCacheVideoBids = cache.getVastxml() != null;

            if (shouldCacheBids || shouldCacheVideoBids) {
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
                        .build();
            }
        }

        return BidRequestCacheInfo.noCache();
    }

    /**
     * Extracts targeting keywords settings from the bid request and creates {@link TargetingKeywordsCreator}
     * instance if they are present.
     * <p>
     * Returns null if bidrequest.ext.prebid.targeting is missing - it means that no targeting keywords
     * should be included in bid response.
     */
    private static TargetingKeywordsCreator keywordsCreator(ExtRequestTargeting targeting, boolean isApp) {
        return targeting != null
                ? TargetingKeywordsCreator.create(parsePriceGranularity(targeting.getPricegranularity()),
                targeting.getIncludewinners(), targeting.getIncludebidderkeys(), isApp)
                : null;
    }

    /**
     * Returns a map of {@link BidType} to correspondent {@link TargetingKeywordsCreator}
     * extracted from {@link ExtRequestTargeting} if it exists.
     */
    private static Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType(ExtRequestTargeting targeting,
                                                                                   boolean isApp) {
        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = targeting != null
                ? targeting.getMediatypepricegranularity() : null;

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
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest, long startTime, Timeout timeout,
                                               boolean debugEnabled, Map<String, String> aliases,
                                               Map<String, BigDecimal> bidAdjustments,
                                               Map<String, Map<String, BigDecimal>> currencyConversionRates) {
        final String bidderName = bidderRequest.getBidder();
        final BigDecimal bidPriceAdjustmentFactor = bidAdjustments.get(bidderName);
        final String adServerCurrency = bidderRequest.getBidRequest().getCur().get(0);
        final Bidder<?> bidder = bidderCatalog.bidderByName(resolveBidder(bidderName, aliases));
        return httpBidderRequester.requestBids(bidder, bidderRequest.getBidRequest(), timeout, debugEnabled)
                .map(bidderSeatBid -> validateAndUpdateResponse(bidderSeatBid, bidderRequest.getBidRequest().getCur()))
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
    private BidderSeatBid validateAndUpdateResponse(BidderSeatBid bidderSeatBid, List<String> requestCurrencies) {
        final List<String> effectiveRequestCurrencies = requestCurrencies.isEmpty()
                ? Collections.singletonList(DEFAULT_CURRENCY) : requestCurrencies;

        final List<BidderBid> bids = bidderSeatBid.getBids();

        final List<BidderBid> validBids = new ArrayList<>(bids.size());
        final List<BidderError> errors = new ArrayList<>(bidderSeatBid.getErrors());

        if (isValidCurFromResponse(effectiveRequestCurrencies, bids, errors)) {
            validateResponseBids(bids, validBids, errors);
        }

        return validBids.size() == bids.size()
                ? bidderSeatBid
                : BidderSeatBid.of(validBids, bidderSeatBid.getHttpCalls(), errors);
    }

    private boolean isValidCurFromResponse(List<String> requestCurrencies, List<BidderBid> bids,
                                           List<BidderError> errors) {
        if (CollectionUtils.isNotEmpty(bids)) {
            // assume that currencies are the same among all bids
            final List<String> bidderCurrencies = bids.stream()
                    .map(bid -> ObjectUtils.firstNonNull(bid.getBidCurrency(), DEFAULT_CURRENCY))
                    .distinct()
                    .collect(Collectors.toList());

            if (bidderCurrencies.size() > 1) {
                errors.add(BidderError.generic("Bid currencies mismatch found. "
                        + "Expected all bids to have the same currencies."));
                return false;
            }

            final String bidderCurrency = bidderCurrencies.get(0);
            if (!requestCurrencies.contains(bidderCurrency)) {
                errors.add(BidderError.generic(String.format(
                        "Bid currency is not allowed. Was %s, wants: [%s]",
                        String.join(",", requestCurrencies), bidderCurrency)));
                return false;
            }
        }

        return true;
    }

    private void validateResponseBids(List<BidderBid> bids, List<BidderBid> validBids, List<BidderError> errors) {
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
    }

    /**
     * Performs changes on {@link Bid}s price depends on different between adServerCurrency and bidCurrency,
     * and adjustment factor. Will drop bid if currency conversion is needed but not possible.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validateAndUpdateResponse(BidderSeatBid, List)}
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
                final BigDecimal finalPrice = currencyService != null
                        ? currencyService.convertCurrency(price, requestCurrencyRates, adServerCurrency, bidCurrency)
                        : price;

                final BigDecimal adjustedPrice = priceAdjustmentFactor != null
                        && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                        ? finalPrice.multiply(priceAdjustmentFactor)
                        : finalPrice;

                if (adjustedPrice.compareTo(price) != 0) {
                    bid.setPrice(adjustedPrice);
                }
                updatedBidderBids.add(bidderBid);
            } catch (PreBidException ex) {
                errors.add(BidderError.generic(ex.getMessage()));
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
     * This method should always be invoked after {@link ExchangeService#validateAndUpdateResponse(BidderSeatBid, List)}
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

    /**
     * Takes all the bids supplied by the bidder and crafts an OpenRTB {@link BidResponse} to send back to the
     * requester.
     */
    private Future<BidResponse> toBidResponse(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                              TargetingKeywordsCreator keywordsCreator,
                                              Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType,
                                              BidRequestCacheInfo cacheInfo, String publisherId, Timeout timeout,
                                              boolean eventsEnabled, boolean debugEnabled) {
        final Set<Bid> bids = newOrEmptyOrderedSet(keywordsCreator);
        final Set<Bid> winningBids = newOrEmptySet(keywordsCreator);
        final Set<Bid> winningBidsByBidder = newOrEmptySet(keywordsCreator);
        populateWinningBids(keywordsCreator, bidderResponses, bids, winningBids, winningBidsByBidder);

        return toBidsWithCacheIds(bids, bidRequest.getImp(), cacheInfo, publisherId, timeout)
                .map(cacheResult -> toBidResponseWithCacheInfo(bidderResponses, bidRequest, keywordsCreator,
                        keywordsCreatorByBidType, cacheResult, winningBids, winningBidsByBidder, cacheInfo,
                        eventsEnabled, debugEnabled));
    }

    /**
     * Returns new {@link HashSet} in case of existing keywordsCreator or empty collection if null.
     */
    private static Set<Bid> newOrEmptySet(TargetingKeywordsCreator keywordsCreator) {
        return keywordsCreator != null ? new HashSet<>() : Collections.emptySet();
    }

    /**
     * Returns new {@link LinkedHashSet} in case of existing keywordsCreator or empty collection if null.
     */
    private static Set<Bid> newOrEmptyOrderedSet(TargetingKeywordsCreator keywordsCreator) {
        return keywordsCreator != null ? new LinkedHashSet<>() : Collections.emptySet();
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
    private static void populateWinningBids(TargetingKeywordsCreator keywordsCreator,
                                            List<BidderResponse> bidderResponses, Set<Bid> bids,
                                            Set<Bid> winningBids, Set<Bid> winningBidsByBidder) {
        // determine winning bids only if targeting keywords are requested
        if (keywordsCreator != null) {
            final Map<String, Bid> winningBidsMap = new HashMap<>(); // impId -> Bid
            final Map<String, Map<String, Bid>> winningBidsByBidderMap = new HashMap<>(); // impId -> [bidder -> Bid]

            for (BidderResponse bidderResponse : bidderResponses) {
                final String bidder = bidderResponse.getBidder();

                for (BidderBid bidderBid : bidderResponse.getSeatBid().getBids()) {
                    final Bid bid = bidderBid.getBid();

                    bids.add(bid);
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
     * Corresponds cacheId (or null if not present) to each {@link Bid}.
     */
    private Future<CacheServiceResult> toBidsWithCacheIds(Set<Bid> bids, List<Imp> imps, BidRequestCacheInfo cacheInfo,
                                                          String publisherId, Timeout timeout) {
        final Future<CacheServiceResult> result;

        if (!cacheInfo.doCaching) {
            result = Future.succeededFuture(CacheServiceResult.of(null, null, toMapBidsWithEmptyCacheIds(bids)));
        } else {
            // do not submit bids with zero price to prebid cache
            final List<Bid> bidsWithNonZeroPrice = bids.stream()
                    .filter(bid -> bid.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());

            final CacheContext cacheContext = CacheContext.of(cacheInfo.shouldCacheBids, cacheInfo.cacheBidsTtl,
                    cacheInfo.shouldCacheVideoBids, cacheInfo.cacheVideoBidsTtl);

            result = cacheService.cacheBidsOpenrtb(bidsWithNonZeroPrice, imps, cacheContext, publisherId, timeout)
                    .map(cacheResult -> addNotCachedBids(cacheResult, bids));
        }

        return result;
    }

    /**
     * Creates a map with {@link Bid} as a key and null as a value.
     */
    private static Map<Bid, CacheIdInfo> toMapBidsWithEmptyCacheIds(Set<Bid> bids) {
        final Map<Bid, CacheIdInfo> result = new HashMap<>(bids.size());
        bids.forEach(bid -> result.put(bid, CacheIdInfo.empty()));
        return result;
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

    /**
     * Creates an OpenRTB {@link BidResponse} from the bids supplied by the bidder,
     * including processing of winning bids with cache IDs.
     */
    private BidResponse toBidResponseWithCacheInfo(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                                   TargetingKeywordsCreator keywordsCreator,
                                                   Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType,
                                                   CacheServiceResult cacheResult, Set<Bid> winningBids,
                                                   Set<Bid> winningBidsByBidder, BidRequestCacheInfo cacheInfo,
                                                   boolean eventsEnabled, boolean debugEnabled) {
        final List<SeatBid> responseSeatBids = bidderResponses.stream()
                .filter(bidderResponse -> !bidderResponse.getSeatBid().getBids().isEmpty())
                .map(bidderResponse -> toSeatBid(bidderResponse, keywordsCreator, keywordsCreatorByBidType, cacheResult,
                        winningBids, winningBidsByBidder, cacheInfo, eventsEnabled))
                .collect(Collectors.toList());

        final ExtBidResponse bidResponseExt = toExtBidResponse(bidderResponses, bidRequest, cacheResult, debugEnabled);

        return BidResponse.builder()
                .id(bidRequest.getId())
                .cur(bidRequest.getCur().get(0))
                .nbr(bidderResponses.isEmpty() ? 2 : null) // signal "Invalid Request" if no valid bidders
                .seatbid(responseSeatBids)
                .ext(Json.mapper.valueToTree(bidResponseExt))
                .build();
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private SeatBid toSeatBid(BidderResponse bidderResponse, TargetingKeywordsCreator keywordsCreator,
                              Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType,
                              CacheServiceResult cacheResult, Set<Bid> winningBid, Set<Bid> winningBidsByBidder,
                              BidRequestCacheInfo cacheInfo, boolean eventsEnabled) {

        final String bidder = bidderResponse.getBidder();

        final List<Bid> bids = bidderResponse.getSeatBid().getBids().stream()
                .map(bidderBid -> toBid(bidderBid, bidder, keywordsCreator, keywordsCreatorByBidType,
                        cacheResult.getCacheBids(), winningBid, winningBidsByBidder, cacheInfo, eventsEnabled))
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
    private Bid toBid(BidderBid bidderBid, String bidder, TargetingKeywordsCreator keywordsCreator,
                      Map<BidType, TargetingKeywordsCreator> keywordsCreatorByBidType,
                      Map<Bid, CacheIdInfo> bidsWithCacheIds, Set<Bid> winningBid, Set<Bid> winningBidsByBidder,
                      BidRequestCacheInfo cacheInfo, boolean eventsEnabled) {

        final Bid bid = bidderBid.getBid();
        final BidType bidType = bidderBid.getType();
        final Map<String, String> targetingKeywords;
        final ExtResponseCache cache;
        final Events events = eventsEnabled ? eventsService.createEvent(bid.getId(), bidder) : null;

        if (keywordsCreator != null && winningBidsByBidder.contains(bid)) {
            final boolean isWinningBid = winningBid.contains(bid);
            final String cacheId = bidsWithCacheIds.get(bid).getCacheId();
            final String videoCacheId = bidsWithCacheIds.get(bid).getVideoCacheId();

            if ((videoCacheId != null && !cacheInfo.returnCreativeVideoBids)
                    || (cacheId != null && !cacheInfo.returnCreativeBids)) {
                bid.setAdm(null);
            }

            targetingKeywords = keywordsCreatorByBidType.getOrDefault(bidType, keywordsCreator)
                    .makeFor(bid, bidder, isWinningBid, cacheId, videoCacheId,
                            cacheService.getEndpointHost(), cacheService.getEndpointPath(),
                            events != null ? events.getWin() : null);
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
     * Creates {@link CacheAsset} for the given cache ID.
     */
    private CacheAsset toCacheAsset(String cacheId) {
        return CacheAsset.of(cacheService.getCachedAssetURL(cacheId), cacheId);
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

        final ExtHttpCall cacheExtHttpCall = toExtHttpCall(cacheResult.getHttpCall());
        final Map<String, List<ExtHttpCall>> cacheHttpCalls = cacheExtHttpCall != null
                ? Collections.singletonMap(CACHE, Collections.singletonList(cacheExtHttpCall))
                : Collections.emptyMap();

        final Map<String, List<ExtHttpCall>> httpCalls = new HashMap<>();
        httpCalls.putAll(bidderHttpCalls);
        httpCalls.putAll(cacheHttpCalls);
        return httpCalls.isEmpty() ? null : httpCalls;
    }

    private static ExtHttpCall toExtHttpCall(CacheHttpCall cacheHttpCall) {
        if (cacheHttpCall != null) {
            final CacheHttpRequest request = cacheHttpCall.getRequest();
            final CacheHttpResponse response = cacheHttpCall.getResponse();

            return ExtHttpCall.builder()
                    .uri(request.getUri())
                    .requestbody(request.getBody())
                    .status(response != null ? response.getStatusCode() : null)
                    .responsebody(response != null ? response.getBody() : null)
                    .build();
        }
        return null;
    }

    private Map<String, List<ExtBidderError>> toExtBidderErrors(List<BidderResponse> bidderResponses,
                                                                BidRequest bidRequest, CacheServiceResult cacheResult) {
        final Map<String, List<ExtBidderError>> errors = new HashMap<>();

        for (BidderResponse bidderResponse : bidderResponses) {
            final List<BidderError> bidderErrors = bidderResponse.getSeatBid().getErrors();
            if (CollectionUtils.isNotEmpty(bidderErrors)) {
                errors.put(bidderResponse.getBidder(), errorsDetails(bidderErrors));
            }
        }
        errors.putAll(extractDeprecatedBiddersErrors(bidRequest));
        errors.putAll(extractCacheErrors(cacheResult));

        return errors.isEmpty() ? null : errors;
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
    private Map<String, List<ExtBidderError>> extractCacheErrors(CacheServiceResult cacheResult) {
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

    /**
     * Holds caching information extracted from incoming auction request.
     */
    @Builder
    @Value
    private static class BidRequestCacheInfo {

        boolean doCaching;

        boolean shouldCacheBids;

        Integer cacheBidsTtl;

        boolean shouldCacheVideoBids;

        Integer cacheVideoBidsTtl;

        boolean returnCreativeBids;

        boolean returnCreativeVideoBids;

        static BidRequestCacheInfo noCache() {
            return BidRequestCacheInfo.builder()
                    .doCaching(false)
                    .shouldCacheBids(false)
                    .cacheBidsTtl(null)
                    .shouldCacheVideoBids(false)
                    .cacheVideoBidsTtl(null)
                    .returnCreativeBids(false)
                    .returnCreativeVideoBids(false)
                    .build();
        }
    }
}
