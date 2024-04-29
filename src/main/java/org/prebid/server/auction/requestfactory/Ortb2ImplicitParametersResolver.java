package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.auction.SecBrowsingTopicsResolver;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.auction.model.SecBrowsingTopic;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.util.StreamUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Ortb2ImplicitParametersResolver {

    private static final Logger logger = LoggerFactory.getLogger(Ortb2ImplicitParametersResolver.class);

    public static final String WEB_CHANNEL = "web";
    public static final String APP_CHANNEL = "app";
    public static final String AMP_CHANNEL = "amp";
    public static final String DOOH_CHANNEL = "dooh";

    private static final String PREBID_EXT = "prebid";
    private static final String BIDDER_EXT = "bidder";

    private static final Set<String> IMP_EXT_NON_BIDDER_FIELDS =
            Set.of(PREBID_EXT, "context", "all", "general", "skadn", "data", "gpid", "tid", "ae");
    private static final String OVERRIDE_SOURCE_ID_TEMPLATE = "{{UUID}}";

    private final boolean shouldCacheOnlyWinningBids;
    private final boolean generateBidRequestId;
    private final String adServerCurrency;
    private final List<String> blacklistedApps;
    private final ExtRequestPrebidServer serverInfo;
    private final ImplicitParametersExtractor paramsExtractor;
    private final TimeoutResolver timeoutResolver;
    private final IpAddressHelper ipAddressHelper;
    private final IdGenerator tidGenerator;
    private final SecBrowsingTopicsResolver topicsResolver;
    private final JsonMerger jsonMerger;
    private final JacksonMapper mapper;

    public Ortb2ImplicitParametersResolver(boolean shouldCacheOnlyWinningBids,
                                           boolean generateBidRequestId,
                                           String adServerCurrency,
                                           List<String> blacklistedApps,
                                           String externalUrl,
                                           Integer hostVendorId,
                                           String datacenterRegion,
                                           ImplicitParametersExtractor paramsExtractor,
                                           TimeoutResolver timeoutResolver,
                                           IpAddressHelper ipAddressHelper,
                                           IdGenerator tidGenerator,
                                           SecBrowsingTopicsResolver topicsResolver,
                                           JsonMerger jsonMerger,
                                           JacksonMapper mapper) {

        this.shouldCacheOnlyWinningBids = shouldCacheOnlyWinningBids;
        this.generateBidRequestId = generateBidRequestId;
        this.adServerCurrency = validateCurrency(Objects.requireNonNull(adServerCurrency));
        this.blacklistedApps = Objects.requireNonNull(blacklistedApps);
        this.serverInfo = ExtRequestPrebidServer.of(externalUrl, hostVendorId, datacenterRegion, null);
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.timeoutResolver = Objects.requireNonNull(timeoutResolver);
        this.ipAddressHelper = Objects.requireNonNull(ipAddressHelper);
        this.tidGenerator = Objects.requireNonNull(tidGenerator);
        this.topicsResolver = Objects.requireNonNull(topicsResolver);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Validates ISO-4217 currency code.
     */
    private static String validateCurrency(String code) {
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currency code supplied is not valid: " + code, e);
        }
        return code;
    }

    /**
     * If needed creates a new {@link BidRequest} which is a copy of original but with some fields set with values
     * derived from request parameters (headers, cookie etc.).
     * <p>
     * Note: {@link TimeoutResolver} used here as argument because this method is utilized in AMP processing.
     */
    public BidRequest resolve(BidRequest bidRequest,
                              AuctionContext auctionContext,
                              String endpoint,
                              boolean hasStoredBidRequest) {

        checkBlacklistedApp(bidRequest);

        final HttpRequestContext httpRequest = auctionContext.getHttpRequest();

        final Device device = bidRequest.getDevice();
        final Device populatedDevice = populateDevice(device, bidRequest.getApp(), httpRequest);

        final Site site = bidRequest.getSite();
        final Site populatedSite = bidRequest.getApp() != null || bidRequest.getDooh() != null
                ? null
                : populateSite(site, httpRequest);

        final List<Imp> populatedImps = populateImps(
                bidRequest,
                generateBidRequestId,
                hasStoredBidRequest);

        final Integer at = bidRequest.getAt();
        final Integer resolvedAt = resolveAt(at);

        final List<String> cur = bidRequest.getCur();
        final List<String> resolvedCurrencies = resolveCurrencies(cur);

        final Long tmax = bidRequest.getTmax();
        final Long resolvedTmax = resolveTmax(tmax);

        final ExtRequest ext = bidRequest.getExt();
        final List<Imp> imps = bidRequest.getImp();
        final ExtRequest populatedExt = populateRequestExt(
                ext, bidRequest, ObjectUtils.defaultIfNull(populatedImps, imps), endpoint);

        final Source source = bidRequest.getSource();
        final Source populatedSource = populateSource(source, populatedExt, hasStoredBidRequest);

        final User user = bidRequest.getUser();
        final User populatedUser = populateUser(
                user,
                httpRequest.getHeaders(),
                auctionContext.getDebugContext().isDebugEnabled(),
                auctionContext.getDebugWarnings());

        return bidRequest.toBuilder()
                .device(populatedDevice != null ? populatedDevice : device)
                .site(populatedSite != null ? populatedSite : site)
                .imp(populatedImps != null ? populatedImps : imps)
                .at(resolvedAt != null ? resolvedAt : at)
                .cur(resolvedCurrencies != null ? resolvedCurrencies : cur)
                .tmax(resolvedTmax != null ? resolvedTmax : tmax)
                .source(populatedSource != null ? populatedSource : source)
                .user(populatedUser != null ? populatedUser : user)
                .ext(populatedExt)
                .build();
    }

    public static boolean isImpExtBidder(String field) {
        return !IMP_EXT_NON_BIDDER_FIELDS.contains(field);
    }

    private void checkBlacklistedApp(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        final String appId = app != null ? app.getId() : null;

        if (StringUtils.isNotBlank(appId) && blacklistedApps.contains(appId)) {
            throw new BlacklistedAppException(
                    "Prebid-server does not process requests from App ID: " + appId);
        }
    }

    /**
     * Populates the request body's 'device' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (User-Agent, IP-address).
     */
    private Device populateDevice(Device device, App app, HttpRequestContext httpRequest) {
        final String deviceIp = device != null ? device.getIp() : null;
        final String deviceIpv6 = device != null ? device.getIpv6() : null;

        String resolvedIp = sanitizeIp(deviceIp, IpAddress.IP.v4);
        String resolvedIpv6 = sanitizeIp(deviceIpv6, IpAddress.IP.v6);

        if (resolvedIp == null && resolvedIpv6 == null) {
            final IpAddress requestIp = findIpFromRequest(httpRequest);

            resolvedIp = getIpIfVersionIs(requestIp, IpAddress.IP.v4);
            resolvedIpv6 = getIpIfVersionIs(requestIp, IpAddress.IP.v6);
        }

        logWarnIfNoIp(resolvedIp, resolvedIpv6);

        final String ua = device != null ? device.getUa() : null;
        final Integer dnt = resolveDntHeader(httpRequest);
        final Integer lmt = resolveLmt(device, app);

        if (!Objects.equals(deviceIp, resolvedIp)
                || !Objects.equals(deviceIpv6, resolvedIpv6)
                || StringUtils.isBlank(ua)
                || dnt != null
                || lmt != null) {

            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();

            if (StringUtils.isBlank(ua)) {
                builder.ua(paramsExtractor.uaFrom(httpRequest));
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

    private Integer resolveDntHeader(HttpRequestContext request) {
        final String dnt = request.getHeaders().get(HttpUtil.DNT_HEADER.toString());
        return StringUtils.equalsAny(dnt, "0", "1") ? Integer.valueOf(dnt) : null;
    }

    private String sanitizeIp(String ip, IpAddress.IP version) {
        final IpAddress ipAddress = ip != null ? ipAddressHelper.toIpAddress(ip) : null;
        return ipAddress != null && ipAddress.getVersion() == version ? ipAddress.getIp() : null;
    }

    public IpAddress findIpFromRequest(HttpRequestContext request) {
        final CaseInsensitiveMultiMap headers = request.getHeaders();
        final String remoteHost = request.getRemoteHost();
        final List<String> requestIps = paramsExtractor.ipFrom(headers, remoteHost);
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

        return !Objects.equals(lmt, 0) ? 0 : null;
    }

    private static Integer resolveLmtForIos14Minor2AndHigher(Device device) {
        final Integer lmt = device.getLmt();
        final Integer atts = ObjectUtil.getIfNotNull(device.getExt(), ExtDevice::getAtts);

        if (atts == null) {
            return null;
        }

        if (atts == 3) {
            return !Objects.equals(lmt, 0) ? 0 : null;
        }

        if (atts == 0 || atts == 1 || atts == 2) {
            return !Objects.equals(lmt, 1) ? 1 : null;
        }

        return null;
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     */
    private Site populateSite(Site site, HttpRequestContext httpRequest) {
        final String page = site != null ? StringUtils.trimToNull(site.getPage()) : null;
        final String updatedPage = page == null ? paramsExtractor.refererFrom(httpRequest) : null;

        final String domain = site != null ? StringUtils.trimToNull(site.getDomain()) : null;
        final String updatedDomain = domain == null
                ? HttpUtil.getHostFromUrl(ObjectUtils.defaultIfNull(updatedPage, page))
                : null;

        final Publisher publisher = site != null ? site.getPublisher() : null;
        final Publisher updatedPublisher = populateSitePublisher(
                publisher, ObjectUtils.defaultIfNull(updatedDomain, domain));

        final ExtSite siteExt = site != null ? site.getExt() : null;
        final ExtSite updatedSiteExt = siteExt == null || siteExt.getAmp() == null
                ? ExtSite.of(0, ObjectUtil.getIfNotNull(siteExt, ExtSite::getData))
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
            logger.warn("Error occurred while populating bid request: {}", e.getMessage());
            return null;
        }
    }

    private Source populateSource(Source source,
                                  ExtRequest extRequest,
                                  boolean hasStoredBidRequest) {

        final String tid = source != null ? source.getTid() : null;
        final String populatedTid = populateTidValue(
                tid,
                generateBidRequestId,
                hasStoredBidRequest,
                tidGenerator);

        final SupplyChain supplyChain = source != null ? source.getSchain() : null;
        final SupplyChain populatedSupplyChain = populateSupplyChain(supplyChain, extRequest);

        if (ObjectUtils.anyNotNull(populatedTid, populatedSupplyChain)) {
            return (source != null ? source.toBuilder() : Source.builder())
                    .tid(populatedTid != null ? populatedTid : tid)
                    .schain(populatedSupplyChain != null ? populatedSupplyChain : supplyChain)
                    .build();
        }

        return null;
    }

    private static String populateTidValue(String tid,
                                           boolean generateBidRequestId,
                                           boolean hasStoredBidRequest,
                                           IdGenerator tidGenerator) {

        final boolean containsTidMacro = StringUtils.containsIgnoreCase(tid, OVERRIDE_SOURCE_ID_TEMPLATE);
        if (StringUtils.isNotBlank(tid)
                && !containsTidMacro
                && !(generateBidRequestId
                && hasStoredBidRequest)) {
            return null;
        }

        final String generatedId = tidGenerator.generateId();

        return StringUtils.isBlank(tid)
                || (generateBidRequestId
                && hasStoredBidRequest)
                && !containsTidMacro
                ? generatedId
                : StringUtils.replaceIgnoreCase(tid, OVERRIDE_SOURCE_ID_TEMPLATE, generatedId);
    }

    private SupplyChain populateSupplyChain(SupplyChain supplyChain, ExtRequest extRequest) {
        if (supplyChain != null || extRequest == null) {
            return null;
        }

        try {
            return mapper.mapper().convertValue(extRequest.getProperty("schain"), SupplyChain.class);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private User populateUser(User user, CaseInsensitiveMultiMap headers, boolean debugEnabled, List<String> warnings) {
        final List<Data> data = user != null ? user.getData() : null;
        final List<Data> populatedData = populateUserData(data, headers, debugEnabled, warnings);

        return populatedData != null
                ? Optional.ofNullable(user)
                .map(User::toBuilder)
                .orElseGet(User::builder)
                .data(populatedData)
                .build()
                : null;
    }

    private List<Data> populateUserData(List<Data> userData,
                                        CaseInsensitiveMultiMap headers,
                                        boolean debugEnabled,
                                        List<String> warnings) {

        final List<SecBrowsingTopic> topics = topicsResolver.resolve(headers, debugEnabled, warnings);
        if (topics.isEmpty()) {
            return null;
        }

        final List<Data> updatedUserData = new ArrayList<>();
        final MultiKeyMap<Object, Data> domainSegTaxSegClassToData =
                CollectionUtils.emptyIfNull(userData).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                Ortb2ImplicitParametersResolver::multiKeyForData,
                                Function.identity(),
                                (first, second) -> {
                                    updatedUserData.add(second);
                                    return first;
                                },
                                MultiKeyMap::new));

        for (SecBrowsingTopic topic : topics) {
            final String topicDomain = topic.getDomain();
            final int topicTaxonomy = topicTaxonomy(topic.getTaxonomyVersion());
            final String topicModelVersion = topic.getModelVersion();

            final Data data = domainSegTaxSegClassToData.get(topicDomain, topicTaxonomy, topicModelVersion);
            if (data == null) {
                domainSegTaxSegClassToData.put(
                        topicDomain,
                        topicTaxonomy,
                        topicModelVersion,
                        createDataForTopic(topic));

                continue;
            }

            final List<Segment> segments = data.getSegment();
            final Set<String> newSegmentsIds = newSegmentsIds(segments, topic.getSegments());
            if (!newSegmentsIds.isEmpty()) {
                domainSegTaxSegClassToData.put(
                        topicDomain,
                        topicTaxonomy,
                        topicModelVersion,
                        data.toBuilder()
                                .segment(addNewSegmentsWithIds(segments, newSegmentsIds))
                                .build());
            }
        }

        updatedUserData.addAll(domainSegTaxSegClassToData.values());
        return updatedUserData;
    }

    private static MultiKey<Object> multiKeyForData(Data data) {
        final ObjectNode ext = data.getExt();

        final String domain = data.getName();

        final JsonNode segTaxNode = ext != null ? ext.get("segtax") : null;
        final Integer segTax = segTaxNode != null && segTaxNode.isNumber() ? segTaxNode.intValue() : null;

        final JsonNode segClassNode = ext != null ? ext.get("segclass") : null;
        final String segClass = segClassNode != null && segClassNode.isTextual() ? segClassNode.textValue() : null;

        return new MultiKey<>(domain, segTax, segClass);
    }

    private static int topicTaxonomy(int taxonomyVersion) {
        return 600 + taxonomyVersion - 1;
    }

    private Data createDataForTopic(SecBrowsingTopic topic) {
        final ObjectNode ext = mapper.mapper().createObjectNode();
        ext.put("segtax", topicTaxonomy(topic.getTaxonomyVersion()));
        ext.put("segclass", topic.getModelVersion());

        return Data.builder()
                .name(topic.getDomain())
                .segment(topic.getSegments().stream()
                        .map(Ortb2ImplicitParametersResolver::segmentWithId)
                        .toList())
                .ext(ext)
                .build();
    }

    private static Segment segmentWithId(String id) {
        return Segment.builder().id(id).build();
    }

    private static Set<String> newSegmentsIds(List<Segment> segments, Set<String> newIds) {
        return CollectionUtils.isNotEmpty(segments)
                ? SetUtils.difference(
                newIds,
                CollectionUtils.emptyIfNull(segments).stream()
                        .filter(Objects::nonNull)
                        .map(Segment::getId)
                        .collect(Collectors.toSet()))
                : newIds;
    }

    private static List<Segment> addNewSegmentsWithIds(List<Segment> segments, Set<String> newIds) {
        final List<Segment> updatedSegments = new ArrayList<>(segments);
        newIds.stream()
                .map(Ortb2ImplicitParametersResolver::segmentWithId)
                .forEach(updatedSegments::add);

        return updatedSegments;
    }

    private List<Imp> populateImps(BidRequest bidRequest,
                                   boolean generateBidRequestId,
                                   boolean hasStoredBidRequest) {

        final List<Imp> imps = bidRequest.getImp();
        if (CollectionUtils.isEmpty(imps)) {
            return null;
        }

        final ObjectNode globalBidderParams = extractGlobalBidderParams(bidRequest);

        final boolean isUniqueIds = isUniqueIds(imps);
        final List<ImpPopulationContext> impPopulationContexts = IntStream
                .range(0, imps.size())
                .mapToObj(index -> new ImpPopulationContext(
                        imps.get(index),
                        globalBidderParams,
                        generateBidRequestId,
                        hasStoredBidRequest,
                        !isUniqueIds ? String.valueOf(index + 1) : null,
                        mapper,
                        tidGenerator,
                        jsonMerger))
                .toList();

        if (impPopulationContexts.stream()
                .map(ImpPopulationContext::getPopulatedImp)
                .allMatch(Objects::isNull)) {

            return null;
        }

        return impPopulationContexts.stream()
                .map(ImpPopulationContext::getPopulationResult)
                .toList();
    }

    private static ObjectNode extractGlobalBidderParams(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extBidPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final ObjectNode bidderParams = extBidPrebid != null ? extBidPrebid.getBidderparams() : null;

        return isObjectNode(bidderParams)
                ? removeNonBidderFields(bidderParams)
                : null;
    }

    private static ObjectNode removeNonBidderFields(ObjectNode node) {
        final ObjectNode modifiedNode = node.deepCopy();
        IMP_EXT_NON_BIDDER_FIELDS.forEach(modifiedNode::remove);

        return !modifiedNode.isEmpty() ? modifiedNode : null;
    }

    private static boolean isObjectNode(JsonNode node) {
        return node != null && node.isObject();
    }

    private static boolean isUniqueIds(List<Imp> imps) {
        final List<String> impIdsList = imps.stream()
                .filter(Objects::nonNull)
                .map(Imp::getId)
                .toList();
        final Set<String> impIdsSet = new HashSet<>(impIdsList);

        return impIdsSet.size() == impIdsList.size();
    }

    private ExtRequest populateRequestExt(ExtRequest ext, BidRequest bidRequest, List<Imp> imps, String endpoint) {
        final ExtRequestPrebid prebid = ObjectUtil.getIfNotNull(ext, ExtRequest::getPrebid);

        final ExtRequestTargeting updatedTargeting = targetingOrNull(prebid, imps);
        final ExtRequestPrebidCache updatedCache = cacheOrNull(prebid);
        final ExtRequestPrebidChannel updatedChannel = channelOrNull(prebid, bidRequest, endpoint);

        final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = prebid != null
                ? prebid.toBuilder()
                : ExtRequestPrebid.builder();

        final ExtRequest updatedExt = ExtRequest.of(prebidBuilder
                .targeting(ObjectUtils.defaultIfNull(updatedTargeting,
                        ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getTargeting)))
                .cache(ObjectUtils.defaultIfNull(updatedCache,
                        ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getCache)))
                .channel(ObjectUtils.defaultIfNull(updatedChannel,
                        ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getChannel)))
                .server(serverInfo.with(endpoint))
                .build());

        final Map<String, JsonNode> extProperties = ObjectUtil.getIfNotNull(ext, ExtRequest::getProperties);
        if (extProperties != null) {
            updatedExt.addProperties(extProperties);
        }

        return updatedExt;
    }

    /**
     * Iterates through impressions to check what media types each impression has and add them to the resulting set.
     * If all four media types are present - no point to look any further.
     */
    private static Set<BidType> getImpMediaTypes(List<Imp> imps) {
        final Set<BidType> impMediaTypes = new HashSet<>();

        if (CollectionUtils.isEmpty(imps)) {
            return impMediaTypes;
        }

        for (Imp imp : imps) {
            resolveImpMediaTypes(imp, impMediaTypes);
            if (impMediaTypes.size() >= 4) {
                break;
            }
        }
        return impMediaTypes;
    }

    /**
     * Adds an existing media type to a set.
     */
    private static void resolveImpMediaTypes(Imp imp, Set<BidType> impsMediaTypes) {
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
    private ExtRequestTargeting targetingOrNull(ExtRequestPrebid prebid, List<Imp> imps) {
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
                            isPriceGranularityTextual, imps))
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
                                             boolean isPriceGranularityTextual, List<Imp> imps) {

        final boolean hasAllMediaTypes = checkExistingMediaTypes(targeting.getMediatypepricegranularity())
                .containsAll(getImpMediaTypes(imps));

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
                    ObjectUtil.getIfNotNull(cache, ExtRequestPrebidCache::getBids),
                    ObjectUtil.getIfNotNull(cache, ExtRequestPrebidCache::getVastxml),
                    true);
        }
        return null;
    }

    /**
     * Returns populated {@link ExtRequestPrebidChannel} or null if no changes were applied.
     */
    private ExtRequestPrebidChannel channelOrNull(ExtRequestPrebid prebid, BidRequest bidRequest, String endpoint) {
        final ExtRequestPrebidChannel channel = ObjectUtil.getIfNotNull(prebid, ExtRequestPrebid::getChannel);
        final String channelName = ObjectUtil.getIfNotNull(channel, ExtRequestPrebidChannel::getName);

        if (channel != null && StringUtils.isBlank(channelName)) {
            throw new PreBidException("ext.prebid.channel.name can't be empty");
        }

        return channel == null ? populateChannel(bidRequest, endpoint) : null;
    }

    private static ExtRequestPrebidChannel populateChannel(BidRequest bidRequest, String endpoint) {
        if (StringUtils.equals(Endpoint.openrtb2_amp.value(), endpoint)) {
            return ExtRequestPrebidChannel.of(AMP_CHANNEL);
        } else if (bidRequest.getApp() != null) {
            return ExtRequestPrebidChannel.of(APP_CHANNEL);
        } else if (bidRequest.getSite() != null) {
            return ExtRequestPrebidChannel.of(WEB_CHANNEL);
        } else if (bidRequest.getDooh() != null) {
            return ExtRequestPrebidChannel.of(DOOH_CHANNEL);
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
        return CollectionUtils.isEmpty(currencies)
                ? Collections.singletonList(adServerCurrency)
                : null;
    }

    /**
     * Determines request timeout with the help of {@link TimeoutResolver}.
     * Returns resolved new value or null if existing request timeout doesn't need to update.
     */
    private Long resolveTmax(Long requestTimeout) {
        final long timeout = timeoutResolver.limitToMax(requestTimeout);
        return !Objects.equals(requestTimeout, timeout) ? timeout : null;
    }

    @Value
    private static class ImpPopulationContext {

        private static final String DEALS_ONLY = "dealsonly";
        private static final String PG_DEALS_ONLY = "pgdealsonly";
        private static final String TID = "tid";

        Imp imp;

        Imp populatedImp;

        ImpPopulationContext(Imp imp,
                             ObjectNode globalBidderParams,
                             boolean generateBidRequestId,
                             boolean hasStoredBidRequest,
                             String impIdOverride,
                             JacksonMapper mapper,
                             IdGenerator tidGenerator,
                             JsonMerger jsonMerger) {

            this.imp = imp;
            populatedImp = populateImp(
                    imp,
                    globalBidderParams,
                    generateBidRequestId,
                    hasStoredBidRequest,
                    impIdOverride,
                    mapper,
                    tidGenerator,
                    jsonMerger);
        }

        public Imp getPopulationResult() {
            return populatedImp != null ? populatedImp : imp;
        }

        private static Imp populateImp(Imp imp,
                                       ObjectNode globalBidderParams,
                                       boolean generateBidRequestId,
                                       boolean hasStoredBidRequest,
                                       String impIdOverride,
                                       JacksonMapper mapper,
                                       IdGenerator tidGenerator,
                                       JsonMerger jsonMerger) {

            final String impId = imp.getId();
            final String populatedImpId = populateImpId(impId, impIdOverride);

            final Integer impSecure = imp.getSecure();
            final Integer populatedImpSecure = populateImpSecure(impSecure);

            final ObjectNode impExt = imp.getExt();
            final ObjectNode populatedImpExt = populateImpExt(
                    impExt,
                    globalBidderParams,
                    generateBidRequestId,
                    hasStoredBidRequest,
                    mapper,
                    tidGenerator,
                    jsonMerger);

            return ObjectUtils.anyNotNull(populatedImpId, populatedImpSecure, populatedImpExt)
                    ? imp.toBuilder()
                    .id(populatedImpId != null ? populatedImpId : impId)
                    .secure(populatedImpSecure != null ? populatedImpSecure : impSecure)
                    .ext(populatedImpExt != null ? populatedImpExt : impExt)
                    .build()
                    : null;
        }

        private static String populateImpId(String impId, String impIdOverride) {
            return StringUtils.isNotBlank(impIdOverride) || StringUtils.isBlank(impId)
                    ? StringUtils.isNotBlank(impIdOverride) ? impIdOverride : generateSixteenDigitRandomString()
                    : null;
        }

        private static String generateSixteenDigitRandomString() {
            return String.valueOf(
                    ThreadLocalRandom.current().nextLong(
                            1000_0000_0000_0000L,
                            1_0000_0000_0000_0000L));
        }

        private static Integer populateImpSecure(Integer impSecure) {
            return impSecure == null ? 1 : null;
        }

        private static ObjectNode populateImpExt(ObjectNode impExt,
                                                 ObjectNode globalBidderParams,
                                                 boolean generateBidRequestId,
                                                 boolean hasStoredBidRequest,
                                                 JacksonMapper mapper,
                                                 IdGenerator tidGenerator,
                                                 JsonMerger jsonMerger) {

            final ObjectNode modifiedImpExt = prepareValidImpExtCopy(impExt, mapper);
            final boolean isMoved = moveBidderParamsToPrebid(modifiedImpExt);
            final boolean isMerged = mergeGlobalBidderParamsToImpExt(modifiedImpExt, globalBidderParams, jsonMerger);
            final boolean isDealsOnlyModified = modifyDealsOnly(modifiedImpExt);
            final boolean isNonBidderFieldsModified = modifyNonBidderFields(
                    modifiedImpExt,
                    generateBidRequestId,
                    hasStoredBidRequest,
                    tidGenerator);

            return isMoved || isMerged || isDealsOnlyModified || isNonBidderFieldsModified
                    ? modifiedImpExt
                    : null;
        }

        private static ObjectNode prepareValidImpExtCopy(ObjectNode impExt, JacksonMapper mapper) {
            final ObjectNode copiedImpExt = impExt != null ? impExt.deepCopy() : mapper.mapper().createObjectNode();

            final ObjectNode modifiedExtPrebid = getOrCreateChildObjectNode(copiedImpExt, PREBID_EXT);
            getOrCreateChildObjectNode(modifiedExtPrebid, BIDDER_EXT);

            return copiedImpExt;
        }

        private static ObjectNode getOrCreateChildObjectNode(ObjectNode parentNode, String fieldName) {
            final JsonNode childNode = parentNode.get(fieldName);
            return isObjectNode(childNode) ? (ObjectNode) childNode : parentNode.putObject(fieldName);
        }

        private static boolean moveBidderParamsToPrebid(ObjectNode impExt) {
            final ObjectNode extPrebidBidder = bidderParamsFromImpExt(impExt);

            final Set<String> bidders = StreamUtil.asStream(impExt.fieldNames())
                    .filter(Ortb2ImplicitParametersResolver::isImpExtBidder)
                    .collect(Collectors.toSet());

            if (bidders.isEmpty()) {
                return false;
            }

            for (String bidder : bidders) {
                final ObjectNode bidderNode = getOrCreateChildObjectNode(extPrebidBidder, bidder);

                final JsonNode impExtBidderNode = impExt.remove(bidder);
                if (isObjectNode(impExtBidderNode)) {
                    bidderNode.setAll((ObjectNode) impExtBidderNode);
                }
            }

            return true;
        }

        private static ObjectNode bidderParamsFromImpExt(ObjectNode ext) {
            return (ObjectNode) ext.path(PREBID_EXT).path(BIDDER_EXT);
        }

        private static boolean mergeGlobalBidderParamsToImpExt(ObjectNode impExt,
                                                               ObjectNode globalBidderParams,
                                                               JsonMerger jsonMerger) {

            if (globalBidderParams == null || globalBidderParams.isEmpty()) {
                return false;
            }

            final ObjectNode impExtPrebidBidder = bidderParamsFromImpExt(impExt);

            StreamUtil.asStream(globalBidderParams.fields())
                    .forEach(bidderToParam -> mergeBidderParams(impExtPrebidBidder, bidderToParam, jsonMerger));

            return true;
        }

        private static void mergeBidderParams(ObjectNode impExtPrebidBidder,
                                              Map.Entry<String, JsonNode> bidderToParam,
                                              JsonMerger jsonMerger) {

            final String bidder = bidderToParam.getKey();
            final JsonNode bidderParams = impExtPrebidBidder.get(bidder);
            final JsonNode requestParams = bidderToParam.getValue();
            final JsonNode mergedParams = bidderParams == null
                    ? requestParams
                    : jsonMerger.merge(bidderParams, requestParams);

            impExtPrebidBidder.set(bidder, mergedParams);
        }

        private static boolean modifyDealsOnly(ObjectNode impExt) {
            final Boolean[] isModified = StreamUtil.asStream(bidderParamsFromImpExt(impExt).fields())
                    .map(Map.Entry::getValue)
                    .filter(ImpPopulationContext::isPgDealsOnlyBidder)
                    .map(ObjectNode.class::cast)
                    .map(ImpPopulationContext::modifyDealsOnlyIfNotSpecified)
                    .toArray(Boolean[]::new);

            if (ArrayUtils.isEmpty(isModified)) {
                return false;
            }

            return BooleanUtils.or(isModified);
        }

        private static boolean isPgDealsOnlyBidder(JsonNode bidderFields) {
            final JsonNode pgDealsOnlyNode = bidderFields.path(PG_DEALS_ONLY);
            return pgDealsOnlyNode.isBoolean() && pgDealsOnlyNode.asBoolean();
        }

        private static boolean modifyDealsOnlyIfNotSpecified(ObjectNode bidderFields) {
            final JsonNode dealsOnlyNode = bidderFields.path(DEALS_ONLY);
            if (dealsOnlyNode.isBoolean()) {
                return false;
            }

            bidderFields.set(DEALS_ONLY, BooleanNode.TRUE);
            return true;
        }

        private static boolean modifyNonBidderFields(ObjectNode impExt,
                                                     boolean generateBidRequestId,
                                                     boolean hasStoredBidRequest,
                                                     IdGenerator tidGenerator) {

            final JsonNode impExtTid = Optional.of(impExt)
                    .map(extNode -> extNode.get(TID))
                    .filter(JsonNode::isTextual)
                    .orElse(null);

            final String populatedTid = populateTidValue(
                    Optional.ofNullable(impExtTid)
                            .map(JsonNode::asText)
                            .orElse(null),
                    generateBidRequestId,
                    hasStoredBidRequest,
                    tidGenerator);

            if (populatedTid != null) {
                impExt.set(TID, new TextNode(populatedTid));
                return true;
            }

            return false;
        }
    }
}
