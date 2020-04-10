package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.Collections;
import java.util.Currency;
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

/**
 * Used in OpenRTB request processing.
 */
public class AuctionRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(AuctionRequestFactory.class);
    private static final ConditionalLogger EMPTY_ACCOUNT_LOGGER = new ConditionalLogger("empty_account", logger);
    private static final ConditionalLogger UNKNOWN_ACCOUNT_LOGGER = new ConditionalLogger("unknown_account", logger);

    private final long maxRequestSize;
    private final boolean enforceValidAccount;
    private final boolean shouldCacheOnlyWinningBids;
    private final String adServerCurrency;
    private final List<String> blacklistedApps;
    private final List<String> blacklistedAccounts;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ImplicitParametersExtractor paramsExtractor;
    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final RequestValidator requestValidator;
    private final InterstitialProcessor interstitialProcessor;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final ApplicationSettings applicationSettings;
    private final JacksonMapper mapper;

    public AuctionRequestFactory(long maxRequestSize,
                                 boolean enforceValidAccount,
                                 boolean shouldCacheOnlyWinningBids,
                                 String adServerCurrency,
                                 List<String> blacklistedApps,
                                 List<String> blacklistedAccounts,
                                 StoredRequestProcessor storedRequestProcessor,
                                 ImplicitParametersExtractor paramsExtractor,
                                 UidsCookieService uidsCookieService,
                                 BidderCatalog bidderCatalog,
                                 RequestValidator requestValidator,
                                 InterstitialProcessor interstitialProcessor,
                                 TimeoutResolver timeoutResolver,
                                 TimeoutFactory timeoutFactory,
                                 ApplicationSettings applicationSettings,
                                 JacksonMapper mapper) {

        this.maxRequestSize = maxRequestSize;
        this.enforceValidAccount = enforceValidAccount;
        this.shouldCacheOnlyWinningBids = shouldCacheOnlyWinningBids;
        this.adServerCurrency = validateCurrency(adServerCurrency);
        this.blacklistedApps = Objects.requireNonNull(blacklistedApps);
        this.blacklistedAccounts = Objects.requireNonNull(blacklistedAccounts);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.interstitialProcessor = Objects.requireNonNull(interstitialProcessor);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Validates ISO-4217 currency code.
     */
    private static String validateCurrency(String code) {
        if (StringUtils.isBlank(code)) {
            return code;
        }

        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format("Currency code supplied is not valid: %s", code), e);
        }
        return code;
    }

    /**
     * Creates {@link AuctionContext} based on {@link RoutingContext}.
     */
    public Future<AuctionContext> fromRequest(RoutingContext routingContext, long startTime) {
        final BidRequest incomingBidRequest;
        try {
            incomingBidRequest = parseRequest(routingContext);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        return updateBidRequest(routingContext, incomingBidRequest)
                .compose(bidRequest -> toAuctionContext(routingContext, bidRequest, startTime, timeoutResolver));
    }

    /**
     * Returns filled out {@link AuctionContext} based on given arguments.
     * <p>
     * Note: {@link TimeoutResolver} used here as argument because this method is utilized in AMP processing.
     */
    Future<AuctionContext> toAuctionContext(RoutingContext routingContext, BidRequest bidRequest,
                                            long startTime, TimeoutResolver timeoutResolver) {
        final Timeout timeout = timeout(bidRequest, startTime, timeoutResolver);

        return accountFrom(bidRequest, timeout, routingContext)
                .map(account -> AuctionContext.builder()
                        .routingContext(routingContext)
                        .uidsCookie(uidsCookieService.parseFromRequest(routingContext))
                        .bidRequest(bidRequest)
                        .timeout(timeout)
                        .account(account)
                        .build());
    }

    /**
     * Parses request body to {@link BidRequest}.
     * <p>
     * Throws {@link InvalidRequestException} if body is empty, exceeds max request size or couldn't be deserialized.
     */
    private BidRequest parseRequest(RoutingContext context) {
        final Buffer body = context.getBody();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        }

        try {
            return mapper.decodeValue(body, BidRequest.class);
        } catch (DecodeException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest: %s", e.getMessage()), true);
        }
    }

    /**
     * Sets {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> updateBidRequest(RoutingContext context, BidRequest bidRequest) {
        return storedRequestProcessor.processStoredRequests(bidRequest)
                .map(resolvedBidRequest -> fillImplicitParameters(resolvedBidRequest, context, timeoutResolver))
                .map(this::validateRequest)
                .map(interstitialProcessor::process);
    }

    /**
     * If needed creates a new {@link BidRequest} which is a copy of original but with some fields set with values
     * derived from request parameters (headers, cookie etc.).
     * <p>
     * Note: {@link TimeoutResolver} used here as argument because this method is utilized in AMP processing.
     */
    BidRequest fillImplicitParameters(BidRequest bidRequest, RoutingContext context, TimeoutResolver timeoutResolver) {
        final boolean hasApp = bidRequest.getApp() != null;
        if (hasApp) {
            checkBlacklistedApp(bidRequest.getApp());
        }

        final BidRequest result;

        final HttpServerRequest request = context.request();
        final List<Imp> imps = bidRequest.getImp();

        final Device populatedDevice = populateDevice(bidRequest.getDevice(), request);
        final Site populatedSite = hasApp ? null : populateSite(bidRequest.getSite(), request);
        final User populatedUser = populateUser(bidRequest.getUser());
        final List<Imp> populatedImps = populateImps(imps, request);
        final Integer at = bidRequest.getAt();
        final boolean updateAt = at == null || at == 0;
        final ObjectNode ext = bidRequest.getExt();
        final ObjectNode populatedExt = ext != null
                ? populateBidRequestExtension(ext, ObjectUtils.defaultIfNull(populatedImps, imps))
                : null;
        final boolean updateCurrency = CollectionUtils.isEmpty(bidRequest.getCur()) && adServerCurrency != null;
        final Long resolvedTmax = resolveTmax(bidRequest.getTmax(), timeoutResolver);

        if (populatedDevice != null || populatedSite != null || populatedUser != null || populatedImps != null
                || updateAt || populatedExt != null || updateCurrency || resolvedTmax != null) {

            result = bidRequest.toBuilder()
                    .device(populatedDevice != null ? populatedDevice : bidRequest.getDevice())
                    .site(populatedSite != null ? populatedSite : bidRequest.getSite())
                    .user(populatedUser != null ? populatedUser : bidRequest.getUser())
                    .imp(populatedImps != null ? populatedImps : imps)
                    // set the auction type to 1 if it wasn't on the request,
                    // since header bidding is generally a first-price auction.
                    .at(updateAt ? Integer.valueOf(1) : at)
                    .ext(populatedExt != null ? populatedExt : ext)
                    .cur(updateCurrency ? Collections.singletonList(adServerCurrency) : bidRequest.getCur())
                    .tmax(resolvedTmax != null ? resolvedTmax : bidRequest.getTmax())
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    private void checkBlacklistedApp(App app) {
        final String appId = app.getId();
        if (CollectionUtils.isNotEmpty(blacklistedApps) && StringUtils.isNotBlank(appId)
                && blacklistedApps.contains(appId)) {
            throw new BlacklistedAppException(String.format(
                    "Prebid-server does not process requests from App ID: %s", appId));
        }
    }

    /**
     * Populates the request body's 'device' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (User-Agent, IP-address).
     */
    private Device populateDevice(Device device, HttpServerRequest request) {
        final Device result;

        final String ip = device != null ? device.getIp() : null;
        final String ua = device != null ? device.getUa() : null;

        if (StringUtils.isBlank(ip) || StringUtils.isBlank(ua)) {
            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();
            builder.ip(StringUtils.isNotBlank(ip) ? ip : paramsExtractor.ipFrom(request));
            builder.ua(StringUtils.isNotBlank(ua) ? ua : paramsExtractor.uaFrom(request));

            result = builder.build();
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     */
    private Site populateSite(Site site, HttpServerRequest request) {
        Site result = null;

        final String page = site != null ? site.getPage() : null;
        final String domain = site != null ? site.getDomain() : null;
        final ObjectNode siteExt = site != null ? site.getExt() : null;
        final ObjectNode data = siteExt != null ? (ObjectNode) siteExt.get("data") : null;
        final boolean shouldSetExtAmp = siteExt == null || siteExt.get("amp") == null;
        final ObjectNode modifiedSiteExt = shouldSetExtAmp
                ? mapper.mapper().valueToTree(ExtSite.of(0, data))
                : null;

        String referer = null;
        String parsedDomain = null;
        if (StringUtils.isBlank(page) || StringUtils.isBlank(domain)) {
            referer = paramsExtractor.refererFrom(request);
            if (StringUtils.isNotBlank(referer)) {
                try {
                    parsedDomain = paramsExtractor.domainFrom(referer);
                } catch (PreBidException e) {
                    logger.warn("Error occurred while populating bid request: {0}", e.getMessage());
                    logger.debug("Error occurred while populating bid request", e);
                }
            }
        }
        final boolean shouldModifyPageOrDomain = referer != null && parsedDomain != null;

        if (shouldModifyPageOrDomain || shouldSetExtAmp) {
            final Site.SiteBuilder builder = site == null ? Site.builder() : site.toBuilder();
            if (shouldModifyPageOrDomain) {
                builder.domain(StringUtils.isNotBlank(domain) ? domain : parsedDomain);
                builder.page(StringUtils.isNotBlank(page) ? page : referer);
            }
            if (shouldSetExtAmp) {
                builder.ext(modifiedSiteExt);
            }
            result = builder.build();
        }
        return result;
    }

    /**
     * Populates the request body's 'user' section from the incoming http request if the original is partially filled.
     */
    private User populateUser(User user) {
        final ObjectNode ext = userExtOrNull(user);

        if (ext != null) {
            final User.UserBuilder builder = user == null ? User.builder() : user.toBuilder();
            return builder.ext(ext).build();
        }
        return null;
    }

    /**
     * Returns {@link ObjectNode} of updated {@link ExtUser} or null if no updates needed.
     */
    private ObjectNode userExtOrNull(User user) {
        final ExtUser extUser = extUser(user);

        // set request.user.ext.digitrust.perf if not defined
        final ExtUserDigiTrust digitrust = extUser != null ? extUser.getDigitrust() : null;
        if (digitrust != null && digitrust.getPref() == null) {
            final ExtUser updatedExtUser = extUser.toBuilder()
                    .digitrust(ExtUserDigiTrust.of(digitrust.getId(), digitrust.getKeyv(), 0))
                    .build();
            return mapper.mapper().valueToTree(updatedExtUser);
        }
        return null;
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
     * Updates imps with security 1, when secured request was received and imp security was not defined.
     */
    private List<Imp> populateImps(List<Imp> imps, HttpServerRequest request) {
        List<Imp> result = null;

        if (Objects.equals(paramsExtractor.secureFrom(request), 1)
                && imps.stream().map(Imp::getSecure).anyMatch(Objects::isNull)) {
            result = imps.stream()
                    .map(imp -> imp.getSecure() == null ? imp.toBuilder().secure(1).build() : imp)
                    .collect(Collectors.toList());
        }
        return result;
    }

    /**
     * Returns updated {@link ExtBidRequest} if required or null otherwise.
     */
    private ObjectNode populateBidRequestExtension(ObjectNode extNode, List<Imp> imps) {
        final ExtBidRequest extBidRequest = extBidRequest(extNode);
        final ExtRequestPrebid prebid = extBidRequest.getPrebid();

        final Set<BidType> impMediaTypes = getImpMediaTypes(imps);
        final ExtRequestTargeting updatedTargeting = targetingOrNull(prebid, impMediaTypes);

        final Map<String, String> updatedAliases = aliasesOrNull(prebid, imps);
        final ExtRequestPrebidCache updatedCache = cacheOrNull(prebid);

        final ObjectNode result;
        if (updatedTargeting != null || updatedAliases != null || updatedCache != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                    ? prebid.toBuilder()
                    : ExtRequestPrebid.builder();

            result = mapper.mapper().valueToTree(ExtBidRequest.of(prebidBuilder
                    .aliases(ObjectUtils.defaultIfNull(updatedAliases,
                            getIfNotNull(prebid, ExtRequestPrebid::getAliases)))
                    .targeting(ObjectUtils.defaultIfNull(updatedTargeting,
                            getIfNotNull(prebid, ExtRequestPrebid::getTargeting)))
                    .cache(ObjectUtils.defaultIfNull(updatedCache,
                            getIfNotNull(prebid, ExtRequestPrebid::getCache)))
                    .build()));
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Extracts {@link ExtBidRequest} from bidrequest.ext {@link ObjectNode}.
     */
    private ExtBidRequest extBidRequest(ObjectNode extBidRequestNode) {
        try {
            return mapper.mapper().treeToValue(extBidRequestNode, ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest.ext: %s", e.getMessage()));
        }
    }

    /**
     * Iterates through impressions to check what media types each impression has and add them to the resulting set.
     * If all four media types are present - no point to look any further.
     */
    private static Set<BidType> getImpMediaTypes(List<Imp> imps) {
        final Set<BidType> impMediaTypes = new HashSet<>();
        for (Imp imp : imps) {
            checkImpMediaTypes(imp, impMediaTypes);
            if (impMediaTypes.size() > 3) {
                break;
            }
        }
        return impMediaTypes;
    }

    /**
     * Adds an existing media type to a set.
     */
    private static void checkImpMediaTypes(Imp imp, Set<BidType> impsMediaTypes) {
        if (imp.getBanner() != null) {
            impsMediaTypes.add(BidType.banner);
        }
        if (imp.getVideo() != null) {
            impsMediaTypes.add(BidType.video);
        }
        if (imp.getAudio() != null) {
            impsMediaTypes.add(BidType.audio);
        }
        if (imp.getXNative() != null) {
            impsMediaTypes.add(BidType.xNative);
        }
    }

    /**
     * Returns populated {@link ExtRequestTargeting} or null if no changes were applied.
     */
    private ExtRequestTargeting targetingOrNull(ExtRequestPrebid prebid, Set<BidType> impMediaTypes) {
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;

        final boolean isTargetingNotNull = targeting != null;
        final boolean isPriceGranularityNull = isTargetingNotNull && targeting.getPricegranularity().isNull();
        final boolean isPriceGranularityTextual = isTargetingNotNull && targeting.getPricegranularity().isTextual();
        final boolean isIncludeWinnersNull = isTargetingNotNull && targeting.getIncludewinners() == null;
        final boolean isIncludeBidderKeysNull = isTargetingNotNull && targeting.getIncludebidderkeys() == null;

        final ExtRequestTargeting result;
        if (isPriceGranularityNull || isPriceGranularityTextual || isIncludeWinnersNull || isIncludeBidderKeysNull) {
            result = ExtRequestTargeting.builder()
                    .pricegranularity(populatePriceGranularity(targeting, isPriceGranularityNull,
                            isPriceGranularityTextual, impMediaTypes))
                    .mediatypepricegranularity(targeting.getMediatypepricegranularity())
                    .includewinners(isIncludeWinnersNull ? true : targeting.getIncludewinners())
                    .includebidderkeys(isIncludeBidderKeysNull
                            ? !isWinningOnly(prebid.getCache())
                            : targeting.getIncludebidderkeys())
                    .build();
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Returns winning only flag value.
     */
    private boolean isWinningOnly(ExtRequestPrebidCache cache) {
        final Boolean cacheWinningOnly = cache != null ? cache.getWinningonly() : null;
        return ObjectUtils.defaultIfNull(cacheWinningOnly, shouldCacheOnlyWinningBids);
    }

    /**
     * Populates priceGranularity with converted value.
     * <p>
     * In case of missing Json node and missing media type price granularities - sets default custom value.
     * In case of valid string price granularity replaced it with appropriate custom view.
     * In case of invalid string value throws {@link InvalidRequestException}.
     */
    private JsonNode populatePriceGranularity(ExtRequestTargeting targeting, boolean isPriceGranularityNull,
                                              boolean isPriceGranularityTextual, Set<BidType> impMediaTypes) {
        final JsonNode priceGranularityNode = targeting.getPricegranularity();

        final boolean hasAllMediaTypes = checkExistingMediaTypes(targeting.getMediatypepricegranularity())
                .containsAll(impMediaTypes);

        if (isPriceGranularityNull && !hasAllMediaTypes) {
            return mapper.mapper().valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT));
        }
        if (isPriceGranularityTextual) {
            final PriceGranularity priceGranularity;
            try {
                priceGranularity = PriceGranularity.createFromString(priceGranularityNode.textValue());
            } catch (PreBidException ex) {
                throw new InvalidRequestException(ex.getMessage());
            }
            return mapper.mapper().valueToTree(ExtPriceGranularity.from(priceGranularity));
        }
        return priceGranularityNode;
    }

    /**
     * Checks {@link ExtMediaTypePriceGranularity} object for present media types and returns a set of existing ones.
     */
    private static Set<BidType> checkExistingMediaTypes(ExtMediaTypePriceGranularity mediaTypePriceGranularity) {
        if (mediaTypePriceGranularity == null) {
            return Collections.emptySet();
        }
        final Set<BidType> priceGranularityTypes = new HashSet<>();

        final JsonNode banner = mediaTypePriceGranularity.getBanner();
        if (banner != null && !banner.isNull()) {
            priceGranularityTypes.add(BidType.banner);
        }
        final JsonNode video = mediaTypePriceGranularity.getVideo();
        if (video != null && !video.isNull()) {
            priceGranularityTypes.add(BidType.video);
        }
        final JsonNode xNative = mediaTypePriceGranularity.getXNative();
        if (xNative != null && !xNative.isNull()) {
            priceGranularityTypes.add(BidType.xNative);
        }
        return priceGranularityTypes;
    }

    /**
     * Returns aliases according to request.imp[i].ext.{bidder}
     * or null (if no aliases at all or they are already presented in request).
     */
    private Map<String, String> aliasesOrNull(ExtRequestPrebid prebid, List<Imp> imps) {
        final Map<String, String> aliases = getIfNotNullOrDefault(prebid, ExtRequestPrebid::getAliases,
                Collections.emptyMap());

        // go through imps' bidders and figure out preconfigured aliases
        final Map<String, String> resolvedAliases = imps.stream()
                .filter(Objects::nonNull)
                .filter(imp -> imp.getExt() != null) // request validator is not called yet
                .flatMap(imp -> asStream(imp.getExt().fieldNames())
                        .filter(bidder -> !aliases.containsKey(bidder))
                        .filter(bidderCatalog::isAlias))
                .distinct()
                .collect(Collectors.toMap(Function.identity(), bidderCatalog::nameByAlias));

        final Map<String, String> result;
        if (resolvedAliases.isEmpty()) {
            result = null;
        } else {
            result = new HashMap<>(aliases);
            result.putAll(resolvedAliases);
        }
        return result;
    }

    /**
     * Returns populated {@link ExtRequestPrebidCache} or null if no changes were applied.
     */
    private ExtRequestPrebidCache cacheOrNull(ExtRequestPrebid prebid) {
        final ExtRequestPrebidCache cache = prebid != null ? prebid.getCache() : null;
        final Boolean cacheWinningOnly = cache != null ? cache.getWinningonly() : null;
        if (cacheWinningOnly == null && shouldCacheOnlyWinningBids) {
            return ExtRequestPrebidCache.of(
                    getIfNotNull(cache, ExtRequestPrebidCache::getBids),
                    getIfNotNull(cache, ExtRequestPrebidCache::getVastxml),
                    true);
        }
        return null;
    }

    /**
     * Determines request timeout with the help of {@link TimeoutResolver}.
     * Returns resolved new value or null if existing request timeout doesn't need to update.
     */
    private static Long resolveTmax(Long requestTimeout, TimeoutResolver timeoutResolver) {
        final long timeout = timeoutResolver.resolve(requestTimeout);
        return !Objects.equals(requestTimeout, timeout) ? timeout : null;
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        final Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }

    private static <T, R> R getIfNotNullOrDefault(T target, Function<T, R> getter, R defaultValue) {
        return ObjectUtils.defaultIfNull(getIfNotNull(target, getter), defaultValue);
    }

    /**
     * Performs thorough validation of fully constructed {@link BidRequest} that is going to be used to hold an auction.
     */
    BidRequest validateRequest(BidRequest bidRequest) {
        final ValidationResult validationResult = requestValidator.validate(bidRequest);
        if (validationResult.hasErrors()) {
            throw new InvalidRequestException(validationResult.getErrors());
        }
        return bidRequest;
    }

    /**
     * Returns {@link Timeout} based on request.tmax and adjustment value of {@link TimeoutResolver}.
     */
    private Timeout timeout(BidRequest bidRequest, long startTime, TimeoutResolver timeoutResolver) {
        final long timeout = timeoutResolver.adjustTimeout(bidRequest.getTmax());
        return timeoutFactory.create(startTime, timeout);
    }

    /**
     * Returns {@link Account} fetched by {@link ApplicationSettings}.
     */
    private Future<Account> accountFrom(BidRequest bidRequest, Timeout timeout, RoutingContext routingContext) {
        final String accountId = accountIdFrom(bidRequest);
        final boolean blankAccountId = StringUtils.isBlank(accountId);

        if (CollectionUtils.isNotEmpty(blacklistedAccounts) && !blankAccountId
                && blacklistedAccounts.contains(accountId)) {
            throw new BlacklistedAccountException(String.format("Prebid-server has blacklisted Account ID: %s, please "
                    + "reach out to the prebid server host.", accountId));
        }

        return blankAccountId
                ? responseForEmptyAccount(routingContext)
                : applicationSettings.getAccountById(accountId, timeout)
                .recover(exception -> accountFallback(exception, accountId, routingContext));
    }

    /**
     * Extracts publisher id either from {@link BidRequest}.app.publisher or {@link BidRequest}.site.publisher.
     * If neither is present returns empty string.
     */
    private String accountIdFrom(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final Publisher appPublisher = app != null ? app.getPublisher() : null;
        final Site site = bidRequest.getSite();
        final Publisher sitePublisher = site != null ? site.getPublisher() : null;

        final Publisher publisher = ObjectUtils.defaultIfNull(appPublisher, sitePublisher);
        final String publisherId = publisher != null ? resolvePublisherId(publisher) : null;
        return ObjectUtils.defaultIfNull(publisherId, StringUtils.EMPTY);
    }

    /**
     * Resolves what value should be used as a publisher id - either taken from publisher.ext.parentAccount
     * or publisher.id in this respective priority.
     */
    private String resolvePublisherId(Publisher publisher) {
        final String parentAccountId = parentAccountIdFromExtPublisher(publisher.getExt());
        return ObjectUtils.defaultIfNull(parentAccountId, publisher.getId());
    }

    /**
     * Parses publisher.ext and returns parentAccount value from it. Returns null if any parsing error occurs.
     */
    private String parentAccountIdFromExtPublisher(ObjectNode extPublisherNode) {
        if (extPublisherNode == null) {
            return null;
        }

        final ExtPublisher extPublisher;
        try {
            extPublisher = mapper.mapper().convertValue(extPublisherNode, ExtPublisher.class);
        } catch (IllegalArgumentException e) {
            return null; // not critical
        }

        final ExtPublisherPrebid extPublisherPrebid = extPublisher != null ? extPublisher.getPrebid() : null;
        return extPublisherPrebid != null ? StringUtils.stripToNull(extPublisherPrebid.getParentAccount()) : null;
    }

    private Future<Account> responseForEmptyAccount(RoutingContext routingContext) {
        EMPTY_ACCOUNT_LOGGER.warn(accountErrorMessage("Account not specified", routingContext), 100);
        return responseForUnknownAccount(StringUtils.EMPTY);
    }

    private static String accountErrorMessage(String message, RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        return String.format("%s, Url: %s and Referer: %s", message, request.absoluteURI(),
                request.headers().get(HttpUtil.REFERER_HEADER));
    }

    private Future<Account> accountFallback(Throwable exception, String accountId,
                                            RoutingContext routingContext) {
        if (exception instanceof PreBidException) {
            UNKNOWN_ACCOUNT_LOGGER.warn(accountErrorMessage(exception.getMessage(), routingContext), 100);
        } else {
            logger.warn("Error occurred while fetching account: {0}", exception.getMessage());
            logger.debug("Error occurred while fetching account", exception);
        }

        // hide all errors occurred while fetching account
        return responseForUnknownAccount(accountId);
    }

    private Future<Account> responseForUnknownAccount(String accountId) {
        return enforceValidAccount
                ? Future.failedFuture(new UnauthorizedAccountException(
                String.format("Unauthorised account id %s", accountId), accountId))
                : Future.succeededFuture(Account.empty(accountId));
    }
}
