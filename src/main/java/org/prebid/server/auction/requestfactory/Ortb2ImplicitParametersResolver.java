package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.StreamUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Ortb2ImplicitParametersResolver {

    private static final Logger logger = LoggerFactory.getLogger(Ortb2ImplicitParametersResolver.class);

    private static final String WEB_CHANNEL = "web";
    private static final String APP_CHANNEL = "app";

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";

    private static final Set<String> IMP_EXT_NON_BIDDER_FIELDS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(PREBID_EXT, "context", "all", "general", "skadn", "data")));

    private final boolean shouldCacheOnlyWinningBids;
    private final String adServerCurrency;
    private final List<String> blacklistedApps;
    private final ImplicitParametersExtractor paramsExtractor;
    private final IpAddressHelper ipAddressHelper;
    private final IdGenerator sourceIdGenerator;
    private final JacksonMapper mapper;

    public Ortb2ImplicitParametersResolver(boolean shouldCacheOnlyWinningBids,
                                           String adServerCurrency,
                                           List<String> blacklistedApps,
                                           ImplicitParametersExtractor paramsExtractor,
                                           IpAddressHelper ipAddressHelper,
                                           IdGenerator sourceIdGenerator,
                                           JacksonMapper mapper) {

        this.shouldCacheOnlyWinningBids = shouldCacheOnlyWinningBids;
        this.adServerCurrency = validateCurrency(Objects.requireNonNull(adServerCurrency));
        this.blacklistedApps = Objects.requireNonNull(blacklistedApps);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.sourceIdGenerator = Objects.requireNonNull(sourceIdGenerator);
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
     * If needed creates a new {@link BidRequest} which is a copy of original but with some fields set with values
     * derived from request parameters (headers, cookie etc.).
     * <p>
     * Note: {@link TimeoutResolver} used here as argument because this method is utilized in AMP processing.
     */
    BidRequest resolve(BidRequest bidRequest,
                       RoutingContext context,
                       TimeoutResolver timeoutResolver) {
        checkBlacklistedApp(bidRequest);

        final BidRequest result;
        final HttpServerRequest request = context.request();

        final Device device = bidRequest.getDevice();
        final Device populatedDevice = populateDevice(device, bidRequest.getApp(), request);

        final Site site = bidRequest.getSite();
        final Site populatedSite = bidRequest.getApp() != null ? null : populateSite(site, request);

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
    private Device populateDevice(Device device, App app, HttpServerRequest request) {
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
        final Integer lmt = resolveLmt(device, app);

        if (!Objects.equals(deviceIp, resolvedIp)
                || !Objects.equals(deviceIpv6, resolvedIpv6)
                || StringUtils.isBlank(ua)
                || dnt != null
                || lmt != null) {

            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();

            if (StringUtils.isBlank(ua)) {
                builder.ua(paramsExtractor.uaFrom(request));
            }
            if (dnt != null) {
                builder.dnt(dnt);
            }

            if (lmt != null) {
                builder.lmt(lmt);
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

    private static void logWarnIfNoIp(String resolvedIp, String resolvedIpv6) {
        if (resolvedIp == null && resolvedIpv6 == null) {
            logger.warn("No IP address found in OpenRTB request and HTTP request headers.");
        }
    }

    private static Integer resolveLmt(Device device, App app) {
        if (app == null || device == null || !StringUtils.equalsIgnoreCase(device.getOs(), "ios")) {
            return null;
        }

        final String osv = device.getOsv();
        if (osv == null) {
            return null;
        }

        // osv format expected: "[major].[minor]". Example: 14.0
        final String[] versionParts = StringUtils.split(osv, '.');
        if (versionParts.length < 2) {
            return null;
        }

        final Integer versionMajor = tryParseAsNumber(versionParts[0]);
        final Integer versionMinor = tryParseAsNumber(versionParts[1]);
        if (versionMajor == null || versionMinor == null) {
            return null;
        }

        return resolveLmtForIos(device, versionMajor, versionMinor);
    }

    private static Integer tryParseAsNumber(String number) {
        try {
            return Integer.parseUnsignedInt(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer resolveLmtForIos(Device device, Integer versionMajor, Integer versionMinor) {
        if (versionMajor < 14) {
            return null;
        }

        if (versionMajor == 14 && (versionMinor == 0 || versionMinor == 1)) {
            return resolveLmtForIos14Minor0And1(device);
        }

        if (versionMajor > 14 || versionMinor >= 2) {
            return resolveLmtForIos14Minor2AndHigher(device);
        }

        return null;
    }

    private static Integer resolveLmtForIos14Minor0And1(Device device) {
        final String ifa = device.getIfa();
        final Integer lmt = device.getLmt();

        if (StringUtils.isEmpty(ifa) || ifa.equals("00000000-0000-0000-0000-000000000000")) {
            return !Objects.equals(lmt, 1) ? 1 : null;
        }

        return lmt == null ? 0 : null;
    }

    private static Integer resolveLmtForIos14Minor2AndHigher(Device device) {
        final Integer lmt = device.getLmt();
        if (lmt != null) {
            return null;
        }

        final Integer atts = getIfNotNull(device.getExt(), ExtDevice::getAtts);
        if (atts == null) {
            return null;
        }

        if (atts == 1 || atts == 2) {
            return 1;
        }

        if (atts == 0 || atts == 3) {
            return 0;
        }

        return null;
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     */
    private Site populateSite(Site site, HttpServerRequest request) {
        final String page = site != null ? StringUtils.trimToNull(site.getPage()) : null;
        final String updatedPage = page == null ? paramsExtractor.refererFrom(request) : null;

        final String domain = site != null ? StringUtils.trimToNull(site.getDomain()) : null;
        final String updatedDomain = domain == null
                ? HttpUtil.getHostFromUrl(ObjectUtils.defaultIfNull(updatedPage, page))
                : null;

        final Publisher publisher = site != null ? site.getPublisher() : null;
        final Publisher updatedPublisher = populateSitePublisher(
                publisher, ObjectUtils.defaultIfNull(updatedDomain, domain));

        final ExtSite siteExt = site != null ? site.getExt() : null;
        final ExtSite updatedSiteExt = siteExt == null || siteExt.getAmp() == null
                ? ExtSite.of(0, getIfNotNull(siteExt, ExtSite::getData))
                : null;

        if (ObjectUtils.anyNotNull(updatedPage, updatedDomain, updatedPublisher, updatedSiteExt)) {
            final boolean domainPresent = (publisher != null && publisher.getDomain() != null)
                    || (updatedPublisher != null && updatedPublisher.getDomain() != null);

            return (site == null ? Site.builder() : site.toBuilder())
                    // do not set page if domain was not parsed successfully
                    .page(domainPresent ? ObjectUtils.defaultIfNull(updatedPage, page) : page)
                    .domain(ObjectUtils.defaultIfNull(updatedDomain, domain))
                    .publisher(ObjectUtils.defaultIfNull(updatedPublisher, publisher))
                    .ext(ObjectUtils.defaultIfNull(updatedSiteExt, siteExt))
                    .build();
        }
        return null;
    }

    private Publisher populateSitePublisher(Publisher publisher, String domain) {
        final String publisherDomain = publisher != null ? publisher.getDomain() : null;
        final String updatedPublisherDomain = publisherDomain == null
                ? getDomainOrNull(domain)
                : null;

        if (updatedPublisherDomain != null) {
            return (publisher == null ? Publisher.builder() : publisher.toBuilder())
                    .domain(updatedPublisherDomain)
                    .build();
        }

        return null;
    }

    private String getDomainOrNull(String url) {
        try {
            return paramsExtractor.domainFrom(url);
        } catch (PreBidException e) {
            logger.warn("Error occurred while populating bid request: {0}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns {@link Source} with updated source.tid or null if nothing changed.
     */
    private Source populateSource(Source source) {
        final String tid = source != null ? source.getTid() : null;
        if (StringUtils.isEmpty(tid)) {
            final String generatedId = sourceIdGenerator.generateId();
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
     * - Updates imps with security 1, when secured request was received and imp security was not defined.
     * - Moves bidder parameters from imp.ext._bidder_ to imp.ext.prebid.bidder._bidder_
     */
    private List<Imp> populateImps(List<Imp> imps, HttpServerRequest request) {
        if (CollectionUtils.isEmpty(imps)) {
            return null;
        }

        final Integer secureFromRequest = paramsExtractor.secureFrom(request);

        if (!shouldModifyImps(imps, secureFromRequest)) {
            return imps;
        }

        return imps.stream()
                .map(imp -> populateImp(imp, secureFromRequest))
                .collect(Collectors.toList());
    }

    private boolean shouldModifyImps(List<Imp> imps, Integer secureFromRequest) {
        return imps.stream()
                .anyMatch(imp -> shouldSetImpSecure(imp, secureFromRequest) || shouldMoveBidderParams(imp));
    }

    private boolean shouldSetImpSecure(Imp imp, Integer secureFromRequest) {
        return imp.getSecure() == null && Objects.equals(secureFromRequest, 1);
    }

    private boolean shouldMoveBidderParams(Imp imp) {
        return imp.getExt() != null
                && StreamUtil.asStream(imp.getExt().fieldNames()).anyMatch(this::isImpExtBidderField);
    }

    private boolean isImpExtBidderField(String field) {
        return !IMP_EXT_NON_BIDDER_FIELDS.contains(field);
    }

    private Imp populateImp(Imp imp, Integer secureFromRequest) {
        final boolean shouldSetSecure = shouldSetImpSecure(imp, secureFromRequest);
        final boolean shouldMoveBidderParams = shouldMoveBidderParams(imp);

        if (shouldSetSecure || shouldMoveBidderParams) {
            final ObjectNode impExt = imp.getExt();

            return imp.toBuilder()
                    .secure(shouldSetSecure ? Integer.valueOf(1) : imp.getSecure())
                    .ext(shouldMoveBidderParams ? populateImpExt(impExt) : impExt)
                    .build();
        }

        return imp;
    }

    private ObjectNode populateImpExt(ObjectNode impExt) {
        final ObjectNode modifiedExt = impExt.deepCopy();

        final ObjectNode modifiedExtPrebid = getOrCreateChildObjectNode(modifiedExt, PREBID_EXT);
        modifiedExt.replace(PREBID_EXT, modifiedExtPrebid);
        final ObjectNode modifiedExtPrebidBidder = getOrCreateChildObjectNode(modifiedExtPrebid, BIDDER_EXT);
        modifiedExtPrebid.replace(BIDDER_EXT, modifiedExtPrebidBidder);

        final Set<String> bidderFields = StreamUtil.asStream(modifiedExt.fieldNames())
                .filter(this::isImpExtBidderField)
                .collect(Collectors.toSet());

        for (final String currentBidderField : bidderFields) {
            final ObjectNode modifiedExtPrebidBidderCurrentBidder =
                    getOrCreateChildObjectNode(modifiedExtPrebidBidder, currentBidderField);
            modifiedExtPrebidBidder.replace(currentBidderField, modifiedExtPrebidBidderCurrentBidder);

            final JsonNode extCurrentBidder = modifiedExt.remove(currentBidderField);
            if (isObjectNode(extCurrentBidder)) {
                modifiedExtPrebidBidderCurrentBidder.setAll((ObjectNode) extCurrentBidder);
            }
        }

        return modifiedExt;
    }

    private static ObjectNode getOrCreateChildObjectNode(ObjectNode parentNode, String fieldName) {
        final JsonNode childNode = parentNode.get(fieldName);

        return isObjectNode(childNode) ? (ObjectNode) childNode : parentNode.objectNode();
    }

    private static boolean isObjectNode(JsonNode node) {
        return node != null && node.isObject();
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
        final ExtRequestPrebidCache updatedCache = cacheOrNull(prebid);
        final ExtRequestPrebidChannel updatedChannel = channelOrNull(prebid, bidRequest);

        if (updatedTargeting != null || updatedCache != null || updatedChannel != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                    ? prebid.toBuilder()
                    : ExtRequestPrebid.builder();

            return ExtRequest.of(prebidBuilder
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

        if (isPriceGranularityNull || isPriceGranularityTextual || isIncludeWinnersNull || isIncludeBidderKeysNull) {
            return targeting.toBuilder()
                    .pricegranularity(resolvePriceGranularity(targeting, isPriceGranularityNull,
                            isPriceGranularityTextual, impMediaTypes))
                    .includewinners(isIncludeWinnersNull || targeting.getIncludewinners())
                    .includebidderkeys(isIncludeBidderKeysNull
                            ? !isWinningOnly(prebid.getCache())
                            : targeting.getIncludebidderkeys())
                    .build();
        }
        return null;
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
    private JsonNode resolvePriceGranularity(ExtRequestTargeting targeting, boolean isPriceGranularityNull,
                                             boolean isPriceGranularityTextual, Set<BidType> impMediaTypes) {

        final boolean hasAllMediaTypes = checkExistingMediaTypes(targeting.getMediatypepricegranularity())
                .containsAll(impMediaTypes);

        if (isPriceGranularityNull && !hasAllMediaTypes) {
            return mapper.mapper().valueToTree(ExtPriceGranularity.from(PriceGranularity.DEFAULT));
        }

        final JsonNode priceGranularityNode = targeting.getPricegranularity();
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

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
