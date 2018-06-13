package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.CacheIdInfo;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.gdpr.GdprException;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprPurpose;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseDebug;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final Set<GdprPurpose> GDPR_PURPOSES =
            Collections.unmodifiableSet(EnumSet.of(GdprPurpose.informationStorageAndAccess,
                    GdprPurpose.adSelectionAndDeliveryAndReporting));
    private static final DecimalFormat ROUND_TWO_DECIMALS =
            new DecimalFormat("###.##", DecimalFormatSymbols.getInstance(Locale.US));

    private final BidderCatalog bidderCatalog;
    private final ResponseBidValidator responseBidValidator;
    private final CacheService cacheService;
    private final BidResponsePostProcessor bidResponsePostProcessor;
    private final CurrencyConversionService currencyService;
    private final GdprService gdprService;
    private final Metrics metrics;
    private final Clock clock;
    private final long expectedCacheTime;
    private final boolean useGeoLocation;

    public ExchangeService(BidderCatalog bidderCatalog,
                           ResponseBidValidator responseBidValidator, CacheService cacheService,
                           BidResponsePostProcessor bidResponsePostProcessor,
                           CurrencyConversionService currencyService,
                           GdprService gdprService,
                           Metrics metrics, Clock clock,
                           long expectedCacheTime, boolean useGeoLocation) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.bidResponsePostProcessor = Objects.requireNonNull(bidResponsePostProcessor);
        this.gdprService = gdprService;
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        if (expectedCacheTime < 0) {
            throw new IllegalArgumentException("Expected cache time could not be negative");
        }
        this.expectedCacheTime = expectedCacheTime;
        this.useGeoLocation = useGeoLocation;
    }

    /**
     * Runs an auction: delegates request to applicable bidders, gathers responses from them and constructs final
     * response containing returned bids and additional information in extensions.
     */
    public Future<BidResponse> holdAuction(BidRequest bidRequest, UidsCookie uidsCookie, Timeout timeout) {
        // extract ext from bid request
        final ExtBidRequest requestExt;
        try {
            requestExt = requestExt(bidRequest);
        } catch (PreBidException e) {
            return Future.failedFuture(e);
        }

        final Map<String, String> aliases = getAliases(requestExt);

        final ExtRequestTargeting targeting = targeting(requestExt);

        // build cache specific params holder
        final BidRequestCacheInfo cacheInfo = bidRequestCacheInfo(targeting, requestExt);

        // build targeting keywords creator
        final TargetingKeywordsCreator keywordsCreator = buildKeywordsCreator(targeting, bidRequest.getApp() != null);

        final long startTime = clock.millis();

        return extractBidderRequests(bidRequest, uidsCookie, aliases)
                .map(bidderRequests -> updateRequestMetric(bidderRequests, uidsCookie, aliases))
                .compose(bidderRequests -> CompositeFuture.join(bidderRequests.stream()
                        .map(bidderRequest -> requestBids(bidderRequest, startTime,
                                auctionTimeout(timeout, cacheInfo.doCaching), aliases, bidAdjustments(requestExt),
                                currencyRates(targeting)))
                        .collect(Collectors.toList())))
                // send all the requests to the bidders and gathers results
                .map(CompositeFuture::<BidderResponse>list)
                // produce response from bidder results
                .map(this::updateMetricsFromResponses)
                .compose(result ->
                        toBidResponse(result, bidRequest, keywordsCreator, cacheInfo, timeout))
                .compose(bidResponse -> bidResponsePostProcessor.postProcess(bidRequest, bidResponse));
    }

    /**
     * Extracts {@link ExtBidRequest} from bid request.
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
    private static Map<String, String> getAliases(ExtBidRequest requestExt) {
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final Map<String, String> aliases = prebid != null ? prebid.getAliases() : null;
        return aliases != null ? aliases : Collections.emptyMap();
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
     * Extracts currency rates from {@link ExtRequestTargeting}
     */
    private static Map<String, Map<String, BigDecimal>> currencyRates(ExtRequestTargeting targeting) {
        return targeting != null && targeting.getCurrency() != null ? targeting.getCurrency().getRates() : null;
    }

    /**
     * Takes an OpenRTB request and returns the OpenRTB requests sanitized for each bidder.
     * <p>
     * This will copy the {@link BidRequest} into a list of requests, where the {@link BidRequest}.imp[].ext field
     * will only consist of the "prebid" field and the field for the appropriate bidder parameters. We will drop all
     * extended fields beyond this context, so this will not be compatible with any other uses of the extension area
     * i.e. the bidders will not see any other extension fields. If Imp extension name is alias, which is also defined
     * in BidRequest.ext.prebid.aliases and valid, separate {@link BidRequest} will be created for this alias and sent
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
     * {@link Imp}, and are known to {@link BidderCatalog} or aliases from {@link BidRequest}.ext.prebid.aliases.
     */
    private Future<List<BidderRequest>> extractBidderRequests(BidRequest bidRequest,
                                                              UidsCookie uidsCookie,
                                                              Map<String, String> aliases) {
        // sanity check: discard imps without extension
        final List<Imp> imps = bidRequest.getImp().stream()
                .filter(imp -> imp.getExt() != null)
                .collect(Collectors.toList());

        // identify valid bidders and aliases out of imps
        final List<String> bidders = imps.stream()
                .flatMap(imp -> asStream(imp.getExt().fieldNames())
                        .filter(bidder -> !Objects.equals(bidder, PREBID_EXT))
                        .filter(bidder -> isValidBidder(bidder, aliases)))
                .distinct()
                .collect(Collectors.toList());

        final User user = bidRequest.getUser();
        final ExtUser extUser = extUser(user);
        final Map<String, String> uidsBody = uidsFromBody(extUser);

        // set empty ext.prebid.buyerids attr to avoid leaking of buyerids across bidders
        final ObjectNode userExtNode = !uidsBody.isEmpty() && extUser != null
                ? removeBuyersidsFromUserExtPrebid(extUser) : null;
        final ExtRegs extRegs = extRegs(bidRequest.getRegs());

        // Splits the input request into requests which are sanitized for each bidder. Intended behavior is:
        // - bidrequest.imp[].ext will only contain the "prebid" field and a "bidder" field which has the params for
        // the intended Bidder.
        // - bidrequest.user.buyeruid will be set to that Bidder's ID.

        return getVendorsToGdprPermission(bidRequest, bidders, extUser, aliases, extRegs)
                .map(vendorToGdprPermission -> makeBidderRequests(bidders, bidRequest, uidsBody, uidsCookie,
                        userExtNode, extRegs, aliases, imps, vendorToGdprPermission));
    }


    /**
     * Returns {@link Future&lt;{@link Map}&lt;{@link Integer}, {@link Boolean}&gt;&gt;}, where bidders vendor id mapped
     * to enabling or disabling gdpr in scope of pbs server. If bidder vendor id is not present in map, it means that
     * pbs not enforced particular bidder to follow pbs gdpr procedure.
     */
    private Future<Map<Integer, Boolean>> getVendorsToGdprPermission(BidRequest bidRequest, List<String> bidders,
                                                                     ExtUser extUser, Map<String, String> aliases,
                                                                     ExtRegs extRegs) {
        final Set<Integer> gdprEnforcedVendorIds = extractGdprEnforcedVendors(bidders, aliases);
        if (gdprEnforcedVendorIds.isEmpty()) {
            return Future.succeededFuture(Collections.emptyMap());
        }

        final Integer gdpr = extRegs != null ? extRegs.getGdpr() : null;
        final String gdprConsent = extUser != null ? extUser.getConsent() : null;
        final Device device = bidRequest.getDevice();

        final String ipAddress = useGeoLocation && device != null ? device.getIp() : null;
        try {
            return gdprService.resultByVendor(GDPR_PURPOSES,
                    gdprEnforcedVendorIds, gdpr != null ? gdpr.toString() : null, gdprConsent, ipAddress);
        } catch (GdprException ex) {
            return Future.failedFuture(new PreBidException(ex.getMessage()));
        }
    }

    private List<BidderRequest> makeBidderRequests(List<String> bidders, BidRequest bidRequest,
                                                   Map<String, String> uidsBody, UidsCookie uidsCookie,
                                                   ObjectNode userExtNode, ExtRegs extRegs, Map<String, String> aliases,
                                                   List<Imp> imps, Map<Integer, Boolean> vendorsToGdpr) {

        final Map<String, Boolean> bidderToMaskingRequired = bidders.stream()
                .collect(Collectors.toMap(Function.identity(),
                        bidder -> isMaskingRequiredBidder(vendorsToGdpr, bidder, aliases)));

        final List<BidderRequest> bidderRequests = bidders.stream()
                // for each bidder create a new request that is a copy of original request except buyerid and imp
                // extensions
                .map(bidder -> BidderRequest.of(bidder, bidRequest.toBuilder()
                        .user(prepareUser(bidder, bidRequest, uidsBody, uidsCookie, userExtNode, aliases,
                                bidderToMaskingRequired.get(bidder)))
                        .device(prepareDevice(bidRequest.getDevice(), bidderToMaskingRequired.get(bidder)))
                        .regs(prepareRegs(bidRequest.getRegs(), extRegs, bidderToMaskingRequired.get(bidder)))
                        .imp(prepareImps(bidder, imps))
                        .build()))
                .collect(Collectors.toList());
        // randomize the list to make the auction more fair
        Collections.shuffle(bidderRequests);
        return bidderRequests;
    }

    /**
     * Returns flag if masking is required for bidder.
     */
    private boolean isMaskingRequiredBidder(Map<Integer, Boolean> vendorToGdprPermission, String bidder,
                                            Map<String, String> aliases) {
        final boolean maskingRequired;
        if (vendorToGdprPermission.isEmpty()) {
            maskingRequired = false;
        } else {
            final String resolvedBidderName = resolveBidder(bidder, aliases);
            final Boolean gdprAllowsUserData = vendorToGdprPermission.get(
                    bidderCatalog.usersyncerByName(resolvedBidderName).gdprVendorId());

            // if bidder was not found in vendorToGdprPermission, it means that it was not pbs enforced for gdpr, so
            // request for this bidder should be sent without changes
            maskingRequired = gdprAllowsUserData != null && !gdprAllowsUserData;

            if (maskingRequired) {
                metrics.forAdapter(resolvedBidderName).incCounter(MetricName.gdpr_masked);
            }
        }
        return maskingRequired;
    }

    /**
     * Extracts {@link ExtRegs} from {@link Regs}.
     */
    private static ExtRegs extRegs(Regs regs) {
        final ObjectNode regsExt = regs != null ? regs.getExt() : null;
        if (regsExt != null) {
            try {
                return Json.mapper.treeToValue(regs.getExt(), ExtRegs.class);
            } catch (JsonProcessingException e) {
                throw new PreBidException(String.format("Error decoding bidRequest.regs.ext: %s", e.getMessage()), e);
            }
        }
        return null;
    }

    /**
     * Extracts pbs gdpr enforced vendor ids.
     */
    private Set<Integer> extractGdprEnforcedVendors(List<String> bidders, Map<String, String> aliases) {
        return bidders.stream()
                .map(bidder -> bidderCatalog.usersyncerByName(resolveBidder(bidder, aliases)))
                .filter(Usersyncer::pbsEnforcesGdpr)
                .map(Usersyncer::gdprVendorId)
                .collect(Collectors.toSet());
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Extracts {@link ExtUser} from {@link User} or returns null if not presents.
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
     * Returns 'explicit' UIDs from request body.
     */
    private static Map<String, String> uidsFromBody(ExtUser extUser) {
        return extUser != null && extUser.getPrebid() != null
                // as long as ext.prebid exists we are guaranteed that user.ext.prebid.buyeruids also exists
                ? extUser.getPrebid().getBuyeruids()
                : Collections.emptyMap();
    }

    /**
     * Returns 'user.ext' with empty 'prebid.buyeryds'.
     */
    private static ObjectNode removeBuyersidsFromUserExtPrebid(ExtUser extUser) {
        return Json.mapper.valueToTree(ExtUser.of(ExtUserPrebid.of(null), extUser.getConsent(),
                extUser.getDigitrust()));
    }

    /**
     * Returns the name associated with bidder if bidder is an alias.
     * If it's not an alias, the bidder is returned.
     */
    private static String resolveBidder(String bidder, Map<String, String> aliases) {
        return aliases.getOrDefault(bidder, bidder);
    }

    /**
     * Returns original {@link User} (if 'user.buyeruid' already contains uid value for bidder or passed buyerUid and
     * updatedUserExt are empty otherwise returns new {@link User} containing updatedUserExt and buyerUid
     * (which means request contains 'explicit' buyeruid in 'request.user.ext.buyerids' or uidsCookie).
     */
    private User prepareUser(String bidder, BidRequest bidRequest, Map<String, String> uidsBody, UidsCookie uidsCookie,
                             ObjectNode updatedUserExt, Map<String, String> aliases, Boolean maskingRequired) {

        final User user = bidRequest.getUser();
        final User.UserBuilder builder = user != null ? user.toBuilder() : User.builder();

        // clean buyeruid from user and user.ext.prebid
        if (maskingRequired) {
            return builder.buyeruid(null).ext(updatedUserExt).build();
        }

        final String resolvedBidder = resolveBidder(bidder, aliases);
        final String buyerUid = extractUid(uidsBody, uidsCookie, resolvedBidder);

        if (updatedUserExt == null && StringUtils.isBlank(buyerUid)) {
            return user;
        }

        if (user == null || StringUtils.isBlank(user.getBuyeruid()) && StringUtils.isNotBlank(buyerUid)) {
            builder.buyeruid(buyerUid);
        }
        if (updatedUserExt != null) {
            builder.ext(updatedUserExt);
        }

        return builder.build();
    }

    /**
     * Prepared device for each bidder depends on gdpr enabling.
     */
    private static Device prepareDevice(Device device, Boolean maskingRequired) {
        return device != null && maskingRequired
                ? device.toBuilder().ip(maskIp(device.getIp(), '.')).ipv6(maskIp(device.getIpv6(), ':'))
                .geo(maskGeo(device.getGeo()))
                .build()
                : device;
    }

    /**
     * Sets gdpr value 1, if bidder required gdpr masking, but gdpr value in regs extension is not defined
     */
    private static Regs prepareRegs(Regs regs, ExtRegs extRegs, Boolean maskingRequired) {
        if (maskingRequired) {
            if (extRegs == null) {
                return Regs.of(regs != null ? regs.getCoppa() : null, Json.mapper.valueToTree(ExtRegs.of(1)));
            } else {
                return Regs.of(regs.getCoppa(), Json.mapper.valueToTree(ExtRegs.of(1)));
            }
        }
        return regs;
    }

    /**
     * Masks ip address by replacing bits after last separator with zeros.
     */
    private static String maskIp(String value, char delimiter) {
        if (StringUtils.isNotEmpty(value)) {
            final int lastIndexOfDelimiter = value.lastIndexOf(delimiter);
            return value.substring(0, lastIndexOfDelimiter + 1)
                    + StringUtils.repeat('0', value.length() - lastIndexOfDelimiter - 1);
        }
        return value;
    }

    /**
     * Masks {@link Geo} by rounding lon and lat properties to two decimals.
     */
    private static Geo maskGeo(Geo geo) {
        final boolean isNotNullGeo = geo != null;
        final Float lon = isNotNullGeo ? geo.getLon() : null;
        final Float lat = isNotNullGeo ? geo.getLat() : null;
        return isNotNullGeo
                ? geo.toBuilder()
                .lon(lon != null ? Float.valueOf(ROUND_TWO_DECIMALS.format(lon)) : null)
                .lat(lat != null ? Float.valueOf(ROUND_TWO_DECIMALS.format(lat)) : null)
                .build()
                : null;

    }

    private List<Imp> prepareImps(String bidder, List<Imp> imps) {
        return imps.stream()
                .filter(imp -> imp.getExt().hasNonNull(bidder))
                // for each imp create a new imp with extension crafted to contain only "prebid" and
                // bidder-specific extensions
                .map(imp -> imp.toBuilder()
                        .ext(Json.mapper.valueToTree(
                                extractBidderExt(bidder, imp.getExt())))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Extracts UID from uids from body or {@link UidsCookie}. If absent returns null.
     */
    private String extractUid(Map<String, String> uidsBody, UidsCookie uidsCookie, String bidder) {
        final String uid = uidsBody.get(bidder);
        return StringUtils.isNotBlank(uid)
                ? uid
                : uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).cookieFamilyName());
    }

    /**
     * Checks if bidder name is valid in case when bidder can also be alias name.
     */
    private boolean isValidBidder(String bidder, Map<String, String> aliases) {
        return bidderCatalog.isValidName(bidder) || aliases.containsKey(bidder);
    }

    /**
     * Updates 'request' and 'no_cookie_requests' metrics for each {@link BidderRequest}
     */
    private List<BidderRequest> updateRequestMetric(List<BidderRequest> bidderRequests, UidsCookie uidsCookie,
                                                    Map<String, String> aliases) {
        for (BidderRequest bidderRequest : bidderRequests) {
            final String bidder = resolveBidder(bidderRequest.getBidder(), aliases);

            metrics.forAdapter(bidder).incCounter(MetricName.requests);

            final boolean noBuyerId = !bidderCatalog.isValidName(bidder) || StringUtils.isBlank(
                    uidsCookie.uidFrom(bidderCatalog.usersyncerByName(bidder).cookieFamilyName()));

            if (bidderRequest.getBidRequest().getApp() == null && noBuyerId) {
                metrics.forAdapter(bidder).incCounter(MetricName.no_cookie_requests);
            }
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

                return BidRequestCacheInfo.of(true, shouldCacheBids, cacheBidsTtl,
                        shouldCacheVideoBids, cacheVideoBidsTtl);
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
    private static TargetingKeywordsCreator buildKeywordsCreator(ExtRequestTargeting targeting, boolean isApp) {
        return targeting != null
                ? TargetingKeywordsCreator.create(parsePriceGranularity(targeting.getPricegranularity()),
                targeting.getIncludewinners() != null ? targeting.getIncludewinners() : true, isApp)
                : null;
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
     * Creates a new imp extension for particular bidder having:
     * <ul>
     * <li>"bidder" field populated with an imp.ext.{bidder} field value, not null</li>
     * <li>"prebid" field populated with an imp.ext.prebid field value, may be null</li>
     * </ul>
     */
    private static ExtPrebid<JsonNode, JsonNode> extractBidderExt(String bidder, ObjectNode impExt) {
        return ExtPrebid.of(impExt.get(PREBID_EXT), impExt.get(bidder));
    }

    /**
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time.
     */
    private Future<BidderResponse> requestBids(BidderRequest bidderRequest, long startTime, Timeout timeout,
                                               Map<String, String> aliases, Map<String, BigDecimal> bidAdjustments,
                                               Map<String, Map<String, BigDecimal>> currencyConversionRates) {
        final String bidder = bidderRequest.getBidder();
        final BigDecimal bidPriceAdjustmentFactor = bidAdjustments.get(bidder);
        // bidrequest.cur should always have only one currency in list, all other cases discarded by RequestValidator
        final String adServerCurrency = bidderRequest.getBidRequest().getCur().get(0);
        return bidderCatalog.bidderRequesterByName(resolveBidder(bidder, aliases))
                .requestBids(bidderRequest.getBidRequest(), timeout)
                .map(this::validateAndUpdateResponse)
                .map(seat -> applyBidPriceChanges(seat, currencyConversionRates, adServerCurrency,
                        bidPriceAdjustmentFactor))
                .map(result -> BidderResponse.of(bidder, result, responseTime(startTime)));
    }

    /**
     * Validates bid response from exchange.
     * <p>
     * Removes invalid bids from response and adds corresponding error to {@link BidderSeatBid}.
     * <p>
     * Returns input argument as the result if no errors found or create new {@link BidderSeatBid} otherwise.
     */
    private BidderSeatBid validateAndUpdateResponse(BidderSeatBid bidderSeatBid) {
        final List<BidderBid> bids = bidderSeatBid.getBids();

        final List<BidderBid> validBids = new ArrayList<>(bids.size());
        final List<BidderError> errors = new ArrayList<>(bidderSeatBid.getErrors());

        for (BidderBid bid : bids) {
            final ValidationResult validationResult = responseBidValidator.validate(bid.getBid());
            if (validationResult.hasErrors()) {
                for (String error : validationResult.getErrors()) {
                    errors.add(BidderError.create(error));
                }
            } else {
                validBids.add(bid);
            }
        }

        return validBids.size() == bids.size()
                ? bidderSeatBid
                : BidderSeatBid.of(validBids, bidderSeatBid.getHttpCalls(), errors);
    }

    /**
     * Performs changes on {@link Bid}s price depends on different between adServerCurrency and bidCurrency,
     * and adjustment factor. Will drop bid if currency conversion is needed but not possible.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validateAndUpdateResponse(BidderSeatBid)}
     * to make sure {@link Bid#price} is not empty.
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
                final BigDecimal convertedPrice = currencyService.convertCurrency(price,
                        requestCurrencyRates, adServerCurrency, bidCurrency);

                final BigDecimal adjustedPrice = priceAdjustmentFactor != null
                        && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                        ? convertedPrice.multiply(priceAdjustmentFactor)
                        : convertedPrice;

                if (adjustedPrice.compareTo(price) != 0) {
                    bid.setPrice(adjustedPrice);
                }
                updatedBidderBids.add(bidderBid);
            } catch (PreBidException ex) {
                errors.add(BidderError.create(ex.getMessage()));
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
     * 'prices' metrics for each {@link BidderResponse}
     * This method should always be invoked after {@link ExchangeService#validateAndUpdateResponse(BidderSeatBid)}
     * to make sure {@link Bid#price} is not empty.
     */
    private List<BidderResponse> updateMetricsFromResponses(List<BidderResponse> bidderResponses) {
        for (final BidderResponse bidderResponse : bidderResponses) {
            final String bidder = bidderResponse.getBidder();
            final AdapterMetrics adapterMetrics = metrics.forAdapter(bidder);

            adapterMetrics.updateTimer(MetricName.request_time, bidderResponse.getResponseTime());

            final List<BidderBid> bidderBids = bidderResponse.getSeatBid().getBids();
            if (CollectionUtils.isEmpty(bidderBids)) {
                adapterMetrics.incCounter(MetricName.no_bid_requests);
            } else {
                for (final BidderBid bidderBid : bidderBids) {
                    final Bid bid = bidderBid.getBid();

                    adapterMetrics.updateHistogram(MetricName.prices, bid.getPrice().multiply(THOUSAND).longValue());

                    final MetricName markupMetricName = bid.getAdm() != null
                            ? MetricName.adm_bids_received : MetricName.nurl_bids_received;
                    adapterMetrics.forBidType(bidderBid.getType()).incCounter(markupMetricName);
                }
            }

            final List<BidderError> errors = bidderResponse.getSeatBid().getErrors();
            if (CollectionUtils.isNotEmpty(errors)) {
                for (final BidderError error : errors) {
                    adapterMetrics.incCounter(error.isTimedOut()
                            ? MetricName.timeout_requests
                            : MetricName.error_requests);
                }
            }
        }

        return bidderResponses;
    }

    /**
     * Takes all the bids supplied by the bidder and crafts an OpenRTB {@link BidResponse} to send back to the
     * requester.
     */
    private Future<BidResponse> toBidResponse(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                              TargetingKeywordsCreator keywordsCreator, BidRequestCacheInfo cacheInfo,
                                              Timeout timeout) {
        final Set<Bid> winningBids = newOrEmptySet(keywordsCreator);
        final Set<Bid> winningBidsByBidder = newOrEmptySet(keywordsCreator);
        populateWinningBids(keywordsCreator, bidderResponses, winningBids, winningBidsByBidder);

        return toWinningBidsWithCacheIds(winningBids, bidRequest.getImp(), keywordsCreator, cacheInfo, timeout)
                .map(winningBidsWithCacheIds -> toBidResponseWithCacheInfo(bidderResponses, bidRequest, keywordsCreator,
                        winningBidsWithCacheIds, winningBidsByBidder));
    }

    /**
     * Returns new {@link HashSet} in case of existing keywordsCreator or {@link Collections.EmptySet} if null.
     */
    private static Set<Bid> newOrEmptySet(TargetingKeywordsCreator keywordsCreator) {
        return keywordsCreator != null ? new HashSet<>() : Collections.emptySet();
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
                                            List<BidderResponse> bidderResponses, Set<Bid> winningBids,
                                            Set<Bid> winningBidsByBidder) {
        // determine winning bids only if targeting keywords are requested
        if (keywordsCreator != null) {
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
    private Future<Map<Bid, CacheIdInfo>> toWinningBidsWithCacheIds(Set<Bid> winningBids, List<Imp> imps,
                                                                    TargetingKeywordsCreator keywordsCreator,
                                                                    BidRequestCacheInfo cacheInfo, Timeout timeout) {
        final Future<Map<Bid, CacheIdInfo>> result;

        if (!cacheInfo.doCaching) {
            result = Future.succeededFuture(toMapBidsWithEmptyCacheIds(winningBids));
        } else {
            // do not submit bids with zero CPM to prebid cache
            final List<Bid> winningBidsWithNonZeroCpm = winningBids.stream()
                    .filter(bid -> keywordsCreator.isNonZeroCpm(bid.getPrice()))
                    .collect(Collectors.toList());

            final List<Bid> cacheBids = cacheInfo.shouldCacheBids
                    ? winningBidsWithNonZeroCpm
                    : Collections.emptyList();

            final List<Bid> cacheVideoBids;
            if (cacheInfo.shouldCacheVideoBids) {
                final List<String> videoImpIds = imps.stream()
                        .filter(imp -> imp.getVideo() != null)
                        .map(Imp::getId)
                        .collect(Collectors.toList());

                cacheVideoBids = winningBidsWithNonZeroCpm.stream()
                        .filter(bid -> videoImpIds.contains(bid.getImpid())) // bid is video
                        .collect(Collectors.toList());
            } else {
                cacheVideoBids = Collections.emptyList();
            }

            result = cacheService.cacheBidsOpenrtb(cacheBids, cacheVideoBids, cacheInfo.cacheBidsTtl,
                    cacheInfo.cacheVideoBidsTtl, timeout)
                    .recover(throwable -> Future.succeededFuture(Collections.emptyMap())) // just skip cache errors
                    .map(map -> addNotCachedBids(map, winningBids));
        }

        return result;
    }

    /**
     * Adds bids with no cache id info.
     */
    private Map<Bid, CacheIdInfo> addNotCachedBids(Map<Bid, CacheIdInfo> bidToCacheIdInfo, Set<Bid> winningBids) {
        if (winningBids.size() > bidToCacheIdInfo.size()) {
            final Map<Bid, CacheIdInfo> result = new HashMap<>(bidToCacheIdInfo);
            for (Bid bid : winningBids) {
                if (!result.containsKey(bid)) {
                    result.put(bid, CacheIdInfo.of(null, null));
                }
            }
            return result;
        }
        return bidToCacheIdInfo;
    }

    /**
     * Creates a map with {@link Bid} as a key and null as a value.
     */
    private static Map<Bid, CacheIdInfo> toMapBidsWithEmptyCacheIds(Set<Bid> bids) {
        final Map<Bid, CacheIdInfo> result = new HashMap<>(bids.size());
        bids.forEach(bid -> result.put(bid, CacheIdInfo.of(null, null)));
        return result;
    }

    /**
     * Creates an OpenRTB {@link BidResponse} from the bids supplied by the bidder,
     * including processing of winning bids with cache IDs.
     */
    private static BidResponse toBidResponseWithCacheInfo(List<BidderResponse> bidderResponses, BidRequest bidRequest,
                                                          TargetingKeywordsCreator keywordsCreator,
                                                          Map<Bid, CacheIdInfo> winningBidsWithCacheIds,
                                                          Set<Bid> winningBidsByBidder) {
        final List<SeatBid> seatBids = bidderResponses.stream()
                .filter(bidderResponse -> !bidderResponse.getSeatBid().getBids().isEmpty())
                .map(bidderResponse ->
                        toSeatBid(bidderResponse, keywordsCreator, winningBidsWithCacheIds, winningBidsByBidder))
                .collect(Collectors.toList());

        final ExtBidResponse bidResponseExt = toExtBidResponse(bidderResponses, bidRequest);

        return BidResponse.builder()
                .id(bidRequest.getId())
                .cur(bidRequest.getCur().get(0))
                // signal "Invalid Request" if no valid bidders.
                .nbr(bidderResponses.isEmpty() ? 2 : null)
                .seatbid(seatBids)
                .ext(Json.mapper.valueToTree(bidResponseExt))
                .build();
    }

    /**
     * Creates an OpenRTB {@link SeatBid} for a bidder. It will contain all the bids supplied by a bidder and a "bidder"
     * extension field populated.
     */
    private static SeatBid toSeatBid(BidderResponse bidderResponse, TargetingKeywordsCreator keywordsCreator,
                                     Map<Bid, CacheIdInfo> winningBidsWithCacheIds, Set<Bid> winningBidsByBidder) {
        final String bidder = bidderResponse.getBidder();
        final BidderSeatBid bidderSeatBid = bidderResponse.getSeatBid();

        final SeatBid.SeatBidBuilder seatBidBuilder = SeatBid.builder()
                .seat(bidder)
                // prebid cannot support roadblocking
                .group(0)
                .bid(bidderSeatBid.getBids().stream()
                        .map(bidderBid ->
                                toBid(bidderBid, bidder, keywordsCreator, winningBidsWithCacheIds, winningBidsByBidder))
                        .collect(Collectors.toList()));

        return seatBidBuilder.build();
    }

    /**
     * Returns an OpenRTB {@link Bid} with "prebid" and "bidder" extension fields populated.
     */
    private static Bid toBid(BidderBid bidderBid, String bidder, TargetingKeywordsCreator keywordsCreator,
                             Map<Bid, CacheIdInfo> winningBidsWithCacheIds, Set<Bid> winningBidsByBidder) {
        final Bid bid = bidderBid.getBid();

        final Map<String, String> targetingKeywords;
        if (keywordsCreator != null && winningBidsByBidder.contains(bid)) {
            final boolean isWinningBid = winningBidsWithCacheIds.containsKey(bid);
            final String cacheId = isWinningBid ? winningBidsWithCacheIds.get(bid).getCacheId() : null;
            final String videoCacheId = isWinningBid ? winningBidsWithCacheIds.get(bid).getVideoCacheId() : null;

            targetingKeywords = keywordsCreator.makeFor(bid, bidder, isWinningBid, cacheId, videoCacheId);
        } else {
            targetingKeywords = null;
        }

        final ExtBidPrebid prebidExt = ExtBidPrebid.of(bidderBid.getType(), targetingKeywords);
        final ExtPrebid<ExtBidPrebid, ObjectNode> bidExt = ExtPrebid.of(prebidExt, bid.getExt());

        bid.setExt(Json.mapper.valueToTree(bidExt));

        return bid;
    }

    /**
     * Creates {@link ExtBidResponse} populated with response time, errors and debug info (if requested) from all
     * bidders
     */
    private static ExtBidResponse toExtBidResponse(List<BidderResponse> results, BidRequest bidRequest) {
        final Map<String, List<ExtHttpCall>> httpCalls = Objects.equals(bidRequest.getTest(), 1)
                ? results.stream().collect(
                Collectors.toMap(BidderResponse::getBidder, r -> r.getSeatBid().getHttpCalls()))
                : null;
        final ExtResponseDebug extResponseDebug = httpCalls != null ? ExtResponseDebug.of(httpCalls, bidRequest) : null;

        final Map<String, List<String>> errors = results.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, r -> messages(r.getSeatBid().getErrors())));

        final Map<String, Integer> responseTimeMillis = results.stream()
                .collect(Collectors.toMap(BidderResponse::getBidder, BidderResponse::getResponseTime));

        return ExtBidResponse.of(extResponseDebug, errors, responseTimeMillis, null);
    }

    private static List<String> messages(List<BidderError> errors) {
        return CollectionUtils.emptyIfNull(errors).stream().map(BidderError::getMessage).collect(Collectors.toList());
    }

    /**
     * Holds caching information for auction request
     */
    @AllArgsConstructor(staticName = "of")
    @Value
    private static class BidRequestCacheInfo {

        boolean doCaching;

        boolean shouldCacheBids;

        Integer cacheBidsTtl;

        boolean shouldCacheVideoBids;

        Integer cacheVideoBidsTtl;

        static BidRequestCacheInfo noCache() {
            return BidRequestCacheInfo.of(false, false, null, false, null);
        }
    }
}
