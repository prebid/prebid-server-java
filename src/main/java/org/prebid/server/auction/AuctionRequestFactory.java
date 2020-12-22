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
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import io.netty.buffer.ByteBufInputStream;
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
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.io.IOException;
import java.util.ArrayList;
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

    public static final String WEB_CHANNEL = "web";
    public static final String APP_CHANNEL = "app";

    private final long maxRequestSize;
    private final boolean enforceValidAccount;
    private final boolean shouldCacheOnlyWinningBids;
    private final String adServerCurrency;
    private final List<String> blacklistedApps;
    private final List<String> blacklistedAccounts;
    private final StoredRequestProcessor storedRequestProcessor;
    private final ImplicitParametersExtractor paramsExtractor;
    private final IpAddressHelper ipAddressHelper;
    private final UidsCookieService uidsCookieService;
    private final BidderCatalog bidderCatalog;
    private final RequestValidator requestValidator;
    private final InterstitialProcessor interstitialProcessor;
    private final TimeoutResolver timeoutResolver;
    private final TimeoutFactory timeoutFactory;
    private final ApplicationSettings applicationSettings;
    private final IdGenerator idGenerator;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final JacksonMapper mapper;
    private final OrtbTypesResolver ortbTypesResolver;

    public AuctionRequestFactory(long maxRequestSize,
                                 boolean enforceValidAccount,
                                 boolean shouldCacheOnlyWinningBids,
                                 String adServerCurrency,
                                 List<String> blacklistedApps,
                                 List<String> blacklistedAccounts,
                                 StoredRequestProcessor storedRequestProcessor,
                                 ImplicitParametersExtractor paramsExtractor,
                                 IpAddressHelper ipAddressHelper,
                                 UidsCookieService uidsCookieService,
                                 BidderCatalog bidderCatalog,
                                 RequestValidator requestValidator,
                                 InterstitialProcessor interstitialProcessor,
                                 OrtbTypesResolver ortbTypesResolver,
                                 TimeoutResolver timeoutResolver,
                                 TimeoutFactory timeoutFactory,
                                 ApplicationSettings applicationSettings,
                                 IdGenerator idGenerator,
                                 PrivacyEnforcementService privacyEnforcementService,
                                 JacksonMapper mapper) {

        this.maxRequestSize = maxRequestSize;
        this.enforceValidAccount = enforceValidAccount;
        this.shouldCacheOnlyWinningBids = shouldCacheOnlyWinningBids;
        this.adServerCurrency = validateCurrency(Objects.requireNonNull(adServerCurrency));
        this.blacklistedApps = Objects.requireNonNull(blacklistedApps);
        this.blacklistedAccounts = Objects.requireNonNull(blacklistedAccounts);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.interstitialProcessor = Objects.requireNonNull(interstitialProcessor);
        this.ortbTypesResolver = Objects.requireNonNull(ortbTypesResolver);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Validates ISO-4217 currency code.
     */
    private static String validateCurrency(String code) {
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
        final List<String> errors = new ArrayList<>();
        final BidRequest incomingBidRequest;
        try {
            incomingBidRequest = parseRequest(routingContext, errors);
        } catch (InvalidRequestException e) {
            return Future.failedFuture(e);
        }

        return updateBidRequest(routingContext, incomingBidRequest)
                .compose(bidRequest -> toAuctionContext(
                        routingContext,
                        bidRequest,
                        requestTypeMetric(bidRequest),
                        errors,
                        startTime,
                        timeoutResolver));
    }

    /**
     * Returns filled out {@link AuctionContext} based on given arguments.
     * <p>
     * Note: {@link TimeoutResolver} used here as argument because this method is utilized in AMP processing.
     */
    Future<AuctionContext> toAuctionContext(RoutingContext routingContext,
                                            BidRequest bidRequest,
                                            MetricName requestTypeMetric,
                                            List<String> errors,
                                            long startTime,
                                            TimeoutResolver timeoutResolver) {

        final Timeout timeout = timeout(bidRequest, startTime, timeoutResolver);

        return accountFrom(bidRequest, timeout, routingContext)
                .compose(account -> privacyEnforcementService.contextFromBidRequest(
                        bidRequest, account, requestTypeMetric, timeout, errors)
                        .map(privacyContext -> AuctionContext.builder()
                                .routingContext(routingContext)
                                .uidsCookie(uidsCookieService.parseFromRequest(routingContext))
                                .bidRequest(enrichBidRequestWithAccountAndPrivacyData(
                                        bidRequest, account, privacyContext))
                                .requestTypeMetric(requestTypeMetric)
                                .timeout(timeout)
                                .account(account)
                                .prebidErrors(errors)
                                .privacyContext(privacyContext)
                                .geoInfo(privacyContext.getTcfContext().getGeoInfo())
                                .build()));
    }

    /**
     * Parses request body to {@link BidRequest}.
     * <p>
     * Throws {@link InvalidRequestException} if body is empty, exceeds max request size or couldn't be deserialized.
     */
    private BidRequest parseRequest(RoutingContext context, List<String> errors) {
        final Buffer body = context.getBody();
        if (body == null) {
            throw new InvalidRequestException("Incoming request has no body");
        }

        if (body.length() > maxRequestSize) {
            throw new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize));
        }

        final JsonNode bidRequestNode;
        try (ByteBufInputStream inputStream = new ByteBufInputStream(body.getByteBuf())) {
            bidRequestNode = mapper.mapper().readTree(inputStream);
        } catch (IOException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest: %s", e.getMessage()));
        }

        final String referer = paramsExtractor.refererFrom(context.request());
        ortbTypesResolver.normalizeBidRequest(bidRequestNode, errors, referer);

        try {
            return mapper.mapper().treeToValue(bidRequestNode, BidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error decoding bidRequest: %s", e.getMessage()));
        }
    }

    /**
     * Sets {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    private Future<BidRequest> updateBidRequest(RoutingContext context, BidRequest bidRequest) {
        return storedRequestProcessor.processStoredRequests(accountIdFrom(bidRequest), bidRequest)
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
        checkBlacklistedApp(bidRequest);

        final BidRequest result;
        final HttpServerRequest request = context.request();

        final Device device = bidRequest.getDevice();
        final Device populatedDevice = populateDevice(device, request);

        final Site site = bidRequest.getSite();
        final Site populatedSite = bidRequest.getApp() != null ? null : populateSite(site, request);

        final User user = bidRequest.getUser();

        final Source source = bidRequest.getSource();
        final Source populatedSource = populateSource(source);

        final List<Imp> imps = bidRequest.getImp();
        final List<Imp> populatedImps = populateImps(imps, request);

        final Integer at = bidRequest.getAt();
        final Integer resolvedAt = resolveAt(at);

        final List<String> cur = bidRequest.getCur();
        final List<String> resolvedCurrencies = resolveCurrencies(cur);

        final Long tmax = bidRequest.getTmax();
        final Long resolvedTmax = resolveTmax(tmax, timeoutResolver);

        final ExtRequest ext = bidRequest.getExt();
        final ExtRequest populatedExt = populateRequestExt(
                ext, bidRequest, ObjectUtils.defaultIfNull(populatedImps, imps));

        if (populatedDevice != null || populatedSite != null || populatedSource != null
                || populatedImps != null || resolvedAt != null || resolvedCurrencies != null || resolvedTmax != null
                || populatedExt != null) {

            result = bidRequest.toBuilder()
                    .device(populatedDevice != null ? populatedDevice : device)
                    .site(populatedSite != null ? populatedSite : site)
                    .source(populatedSource != null ? populatedSource : source)
                    .imp(populatedImps != null ? populatedImps : imps)
                    .at(resolvedAt != null ? resolvedAt : at)
                    .cur(resolvedCurrencies != null ? resolvedCurrencies : cur)
                    .tmax(resolvedTmax != null ? resolvedTmax : tmax)
                    .ext(populatedExt != null ? populatedExt : ext)
                    .build();
        } else {
            result = bidRequest;
        }
        return result;
    }

    private void checkBlacklistedApp(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final String appId = app != null ? app.getId() : null;

        if (StringUtils.isNotBlank(appId) && blacklistedApps.contains(appId)) {
            throw new BlacklistedAppException(
                    String.format("Prebid-server does not process requests from App ID: %s", appId));
        }
    }

    /**
     * Populates the request body's 'device' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (User-Agent, IP-address).
     */
    private Device populateDevice(Device device, HttpServerRequest request) {
        final String deviceIp = device != null ? device.getIp() : null;
        final String deviceIpv6 = device != null ? device.getIpv6() : null;

        String resolvedIp = sanitizeIp(deviceIp, IpAddress.IP.v4);
        String resolvedIpv6 = sanitizeIp(deviceIpv6, IpAddress.IP.v6);

        if (resolvedIp == null && resolvedIpv6 == null) {
            final IpAddress requestIp = findIpFromRequest(request);

            resolvedIp = getIpIfVersionIs(requestIp, IpAddress.IP.v4);
            resolvedIpv6 = getIpIfVersionIs(requestIp, IpAddress.IP.v6);
        }

        logWarnIfNoIp(resolvedIp, resolvedIpv6);

        final String ua = device != null ? device.getUa() : null;
        final Integer dnt = resolveDntHeader(request);

        if (!Objects.equals(deviceIp, resolvedIp)
                || !Objects.equals(deviceIpv6, resolvedIpv6)
                || StringUtils.isBlank(ua) || dnt != null) {

            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();

            if (StringUtils.isBlank(ua)) {
                builder.ua(paramsExtractor.uaFrom(request));
            }
            if (dnt != null) {
                builder.dnt(dnt);
            }

            builder
                    .ip(resolvedIp)
                    .ipv6(resolvedIpv6);

            return builder.build();
        }

        return null;
    }

    private Integer resolveDntHeader(HttpServerRequest request) {
        final String dnt = request.getHeader(HttpUtil.DNT_HEADER.toString());
        return StringUtils.equalsAny(dnt, "0", "1") ? Integer.valueOf(dnt) : null;
    }

    private String sanitizeIp(String ip, IpAddress.IP version) {
        final IpAddress ipAddress = ip != null ? ipAddressHelper.toIpAddress(ip) : null;
        return ipAddress != null && ipAddress.getVersion() == version ? ipAddress.getIp() : null;
    }

    private IpAddress findIpFromRequest(HttpServerRequest request) {
        final List<String> requestIps = paramsExtractor.ipFrom(request);
        return requestIps.stream()
                .map(ipAddressHelper::toIpAddress)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String getIpIfVersionIs(IpAddress requestIp, IpAddress.IP version) {
        return requestIp != null && requestIp.getVersion() == version ? requestIp.getIp() : null;
    }

    private void logWarnIfNoIp(String resolvedIp, String resolvedIpv6) {
        if (resolvedIp == null && resolvedIpv6 == null) {
            logger.warn("No IP address found in OpenRTB request and HTTP request headers.");
        }
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     */
    private Site populateSite(Site site, HttpServerRequest request) {
        Site result = null;

        final String page = site != null ? site.getPage() : null;
        final String domain = site != null ? site.getDomain() : null;
        final ExtSite siteExt = site != null ? site.getExt() : null;
        final ObjectNode data = siteExt != null ? siteExt.getData() : null;
        final boolean shouldSetExtAmp = siteExt == null || siteExt.getAmp() == null;
        final ExtSite modifiedSiteExt = shouldSetExtAmp
                ? ExtSite.of(0, data)
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
     * Returns {@link Source} with updated source.tid or null if nothing changed.
     */
    private Source populateSource(Source source) {
        final String tid = source != null ? source.getTid() : null;
        if (StringUtils.isEmpty(tid)) {
            final String generatedId = idGenerator.generateId();
            if (StringUtils.isNotEmpty(generatedId)) {
                final Source.SourceBuilder builder = source != null ? source.toBuilder() : Source.builder();
                return builder
                        .tid(generatedId)
                        .build();
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
     * Returns updated {@link ExtRequest} if required or null otherwise.
     */
    private ExtRequest populateRequestExt(ExtRequest ext, BidRequest bidRequest, List<Imp> imps) {
        if (ext == null) {
            return null;
        }

        final ExtRequestPrebid prebid = ext.getPrebid();

        final ExtRequestTargeting updatedTargeting = targetingOrNull(prebid, getImpMediaTypes(imps));
        final Map<String, String> updatedAliases = aliasesOrNull(prebid, imps);
        final ExtRequestPrebidCache updatedCache = cacheOrNull(prebid);
        final ExtRequestPrebidChannel updatedChannel = channelOrNull(prebid, bidRequest);

        if (updatedTargeting != null || updatedAliases != null || updatedCache != null || updatedChannel != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                    ? prebid.toBuilder()
                    : ExtRequestPrebid.builder();

            return ExtRequest.of(prebidBuilder
                    .aliases(ObjectUtils.defaultIfNull(updatedAliases,
                            getIfNotNull(prebid, ExtRequestPrebid::getAliases)))
                    .targeting(ObjectUtils.defaultIfNull(updatedTargeting,
                            getIfNotNull(prebid, ExtRequestPrebid::getTargeting)))
                    .cache(ObjectUtils.defaultIfNull(updatedCache,
                            getIfNotNull(prebid, ExtRequestPrebid::getCache)))
                    .channel(ObjectUtils.defaultIfNull(updatedChannel,
                            getIfNotNull(prebid, ExtRequestPrebid::getChannel)))
                    .build());
        }

        return null;
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
        final boolean isPriceGranularityNull = isTargetingNotNull
                && (targeting.getPricegranularity() == null || targeting.getPricegranularity().isNull());
        final boolean isPriceGranularityTextual = isTargetingNotNull && !isPriceGranularityNull
                && targeting.getPricegranularity().isTextual();
        final boolean isIncludeWinnersNull = isTargetingNotNull && targeting.getIncludewinners() == null;
        final boolean isIncludeBidderKeysNull = isTargetingNotNull && targeting.getIncludebidderkeys() == null;

        final ExtRequestTargeting result;
        if (isPriceGranularityNull || isPriceGranularityTextual || isIncludeWinnersNull || isIncludeBidderKeysNull) {
            result = ExtRequestTargeting.builder()
                    .pricegranularity(populatePriceGranularity(targeting, isPriceGranularityNull,
                            isPriceGranularityTextual, impMediaTypes))
                    .mediatypepricegranularity(targeting.getMediatypepricegranularity())
                    .includewinners(isIncludeWinnersNull || targeting.getIncludewinners())
                    .includebidderkeys(isIncludeBidderKeysNull
                            ? !isWinningOnly(prebid.getCache())
                            : targeting.getIncludebidderkeys())
                    .includeformat(targeting.getIncludeformat())
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
            } catch (PreBidException e) {
                throw new InvalidRequestException(e.getMessage());
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
     * Returns populated {@link ExtRequestPrebidChannel} or null if no changes were applied.
     */
    private ExtRequestPrebidChannel channelOrNull(ExtRequestPrebid prebid, BidRequest bidRequest) {
        final String existingChannelName = getIfNotNull(getIfNotNull(prebid,
                ExtRequestPrebid::getChannel),
                ExtRequestPrebidChannel::getName);

        if (StringUtils.isNotBlank(existingChannelName)) {
            return null;
        }

        if (bidRequest.getSite() != null) {
            return ExtRequestPrebidChannel.of(WEB_CHANNEL);
        } else if (bidRequest.getApp() != null) {
            return ExtRequestPrebidChannel.of(APP_CHANNEL);
        }

        return null;
    }

    /**
     * Returns updated request.at or null if nothing changed.
     * <p>
     * Set the auction type to 1 if it wasn't on the request, since header bidding is generally a first-price auction.
     */
    private static Integer resolveAt(Integer at) {
        return at == null || at == 0 ? 1 : null;
    }

    /**
     * Returns default list of currencies if it wasn't on the request, otherwise null.
     */
    private List<String> resolveCurrencies(List<String> currencies) {
        return CollectionUtils.isEmpty(currencies) && adServerCurrency != null
                ? Collections.singletonList(adServerCurrency)
                : null;
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
                .compose(this::ensureAccountActive, exception -> accountFallback(exception, accountId, routingContext));
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
    private String parentAccountIdFromExtPublisher(ExtPublisher extPublisher) {
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
                String.format("Unauthorized account id: %s", accountId), accountId))
                : Future.succeededFuture(Account.empty(accountId));
    }

    private Future<Account> ensureAccountActive(Account account) {
        final String accountId = account.getId();

        return account.getStatus() == AccountStatus.inactive
                ? Future.failedFuture(
                new UnauthorizedAccountException(String.format("Account %s is inactive", accountId), accountId))
                : Future.succeededFuture(account);
    }

    private BidRequest enrichBidRequestWithAccountAndPrivacyData(
            BidRequest bidRequest, Account account, PrivacyContext privacyContext) {

        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequest enrichedRequestExt = enrichExtRequest(requestExt, account);

        final Device device = bidRequest.getDevice();
        final Device enrichedDevice = enrichDevice(device, privacyContext);

        if (enrichedRequestExt != null || enrichedDevice != null) {
            return bidRequest.toBuilder()
                    .ext(ObjectUtils.defaultIfNull(enrichedRequestExt, requestExt))
                    .device(ObjectUtils.defaultIfNull(enrichedDevice, device))
                    .build();
        }

        return bidRequest;
    }

    private ExtRequest enrichExtRequest(ExtRequest ext, Account account) {
        final ExtRequestPrebid prebidExt = getIfNotNull(ext, ExtRequest::getPrebid);
        final String integration = getIfNotNull(prebidExt, ExtRequestPrebid::getIntegration);
        final String accountDefaultIntegration = account.getDefaultIntegration();

        if (StringUtils.isBlank(integration) && StringUtils.isNotBlank(accountDefaultIntegration)) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidExtBuilder =
                    prebidExt != null ? prebidExt.toBuilder() : ExtRequestPrebid.builder();

            prebidExtBuilder.integration(accountDefaultIntegration);

            return ExtRequest.of(prebidExtBuilder.build());
        }

        return null;
    }

    private Device enrichDevice(Device device, PrivacyContext privacyContext) {
        final String ipAddress = privacyContext.getIpAddress();
        final String country = getIfNotNull(privacyContext.getTcfContext().getGeoInfo(), GeoInfo::getCountry);

        final String ipAddressInRequest = getIfNotNull(device, Device::getIp);

        final Geo geo = getIfNotNull(device, Device::getGeo);
        final String countryFromRequest = getIfNotNull(geo, Geo::getCountry);

        final boolean shouldUpdateIp = ipAddress != null && !Objects.equals(ipAddressInRequest, ipAddress);
        final boolean shouldUpdateCountry = country != null && !Objects.equals(countryFromRequest, country);

        if (shouldUpdateIp || shouldUpdateCountry) {
            final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

            if (shouldUpdateIp) {
                deviceBuilder.ip(ipAddress);
            }

            if (shouldUpdateCountry) {
                final Geo.GeoBuilder geoBuilder = geo != null ? geo.toBuilder() : Geo.builder();
                geoBuilder.country(country);
                deviceBuilder.geo(geoBuilder.build());
            }

            return deviceBuilder.build();
        }

        return null;
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        return bidRequest.getApp() != null ? MetricName.openrtb2app : MetricName.openrtb2web;
    }
}
