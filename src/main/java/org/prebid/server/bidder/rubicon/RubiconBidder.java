package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.ViewabilityVendors;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.proto.RubiconAppExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExt;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconExtPrebidBidders;
import org.prebid.server.bidder.rubicon.proto.RubiconExtPrebidBiddersBidder;
import org.prebid.server.bidder.rubicon.proto.RubiconExtPrebidBiddersBidderDebug;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExt;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtPrebidBidder;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtPrebidRubiconDebug;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExt;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconTargeting;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExt;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExt;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconVideoExt;
import org.prebid.server.bidder.rubicon.proto.RubiconVideoExtRp;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUidExt;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <a href="https://rubiconproject.com">Rubicon Project</a> {@link Bidder} implementation.
 */
public class RubiconBidder implements Bidder<BidRequest> {

    private static final Logger logger = LoggerFactory.getLogger(RubiconBidder.class);

    private static final String TK_XINT_QUERY_PARAMETER = "tk_xint";
    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";
    private static final String ADSERVER_EID = "adserver.org";
    private static final String LIVEINTENT_EID = "liveintent.com";
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String PREBID_EXT = "prebid";

    private static final TypeReference<ExtPrebid<ExtImpPrebid, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtImpPrebid, ExtImpRubicon>>() {
            };

    private final String endpointUrl;
    private final Set<String> supportedVendors;
    private final boolean generateBidId;
    private final JacksonMapper mapper;

    private final MultiMap headers;

    public RubiconBidder(String endpoint,
                         String xapiUsername,
                         String xapiPassword,
                         List<String> supportedVendors,
                         boolean generateBidId,
                         JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        this.supportedVendors = new HashSet<>(supportedVendors);
        this.generateBidId = generateBidId;
        this.mapper = Objects.requireNonNull(mapper);

        this.headers = headers(Objects.requireNonNull(xapiUsername), Objects.requireNonNull(xapiPassword));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final boolean useFirstPartyData = useFirstPartyData(bidRequest);
        final Map<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> impToImpExt =
                parseRubiconImpExts(bidRequest.getImp(), errors);
        final String impLanguage = firstImpExtLanguage(impToImpExt.values());

        for (Map.Entry<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> impToExt : impToImpExt.entrySet()) {
            try {
                final Imp imp = impToExt.getKey();
                final ExtPrebid<ExtImpPrebid, ExtImpRubicon> ext = impToExt.getValue();
                final BidRequest singleRequest = createSingleRequest(
                        imp, ext.getPrebid(), ext.getBidder(), bidRequest, impLanguage, useFirstPartyData
                );
                final String body = mapper.encode(singleRequest);
                httpRequests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(makeUri(bidRequest))
                        .body(body)
                        .headers(headers)
                        .payload(singleRequest)
                        .build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final HttpResponse response = httpCall.getResponse();
        if (response.getStatusCode() == 204) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(response.getBody(), BidResponse.class);
            return Result.of(extractBids(bidRequest, httpCall.getRequest().getPayload(), bidResponse),
                    Collections.emptyList());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode extBidBidder) {
        final RubiconTargetingExt rubiconTargetingExt;
        try {
            rubiconTargetingExt = mapper.mapper().convertValue(extBidBidder, RubiconTargetingExt.class);
        } catch (IllegalArgumentException e) {
            logger.warn("Error adding rubicon specific targeting to amp response", e);
            return Collections.emptyMap();
        }

        final RubiconTargetingExtRp rp = rubiconTargetingExt.getRp();
        final List<RubiconTargeting> targeting = rp != null ? rp.getTargeting() : null;
        return targeting != null
                ? targeting.stream()
                .filter(rubiconTargeting -> !CollectionUtils.isEmpty(rubiconTargeting.getValues()))
                .collect(Collectors.toMap(RubiconTargeting::getKey, t -> t.getValues().get(0)))
                : Collections.emptyMap();
    }

    private static MultiMap headers(String xapiUsername, String xapiPassword) {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.AUTHORIZATION_HEADER, authHeader(xapiUsername, xapiPassword))
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpUtil.USER_AGENT_HEADER, PREBID_SERVER_USER_AGENT);
    }

    private static String authHeader(String xapiUsername, String xapiPassword) {
        return "Basic " + Base64.getEncoder().encodeToString((xapiUsername + ':' + xapiPassword).getBytes());
    }

    /**
     * Determines if First Party Data should be applied.
     * This mainly related to global fields like request.site.keywords, etc.
     */
    private boolean useFirstPartyData(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt() != null
                ? mapper.mapper().convertValue(bidRequest.getExt(), ExtRequest.class)
                : null;
        final ExtRequestPrebid prebid = extRequest == null ? null : extRequest.getPrebid();
        final ExtRequestPrebidData data = prebid == null ? null : prebid.getData();
        final List<String> bidders = data == null ? null : data.getBidders();
        return CollectionUtils.isNotEmpty(bidders); // this contains only current bidder
    }

    private Map<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> parseRubiconImpExts(
            List<Imp> imps, List<BidderError> errors
    ) {
        final Map<Imp, ExtPrebid<ExtImpPrebid, ExtImpRubicon>> impToImpExt = new HashMap<>();
        for (final Imp imp : imps) {
            try {
                final ExtPrebid<ExtImpPrebid, ExtImpRubicon> rubiconImpExt = parseRubiconExt(imp);
                impToImpExt.put(imp, rubiconImpExt);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return impToImpExt;
    }

    private ExtPrebid<ExtImpPrebid, ExtImpRubicon> parseRubiconExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), RUBICON_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static String firstImpExtLanguage(Collection<ExtPrebid<ExtImpPrebid, ExtImpRubicon>> rubiconImpExts) {
        return rubiconImpExts.stream()
                .filter(Objects::nonNull)
                .map(ExtPrebid::getBidder)
                .map(ExtImpRubicon::getVideo)
                .filter(Objects::nonNull)
                .map(RubiconVideoParams::getLanguage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private BidRequest createSingleRequest(Imp imp, ExtImpPrebid extPrebid, ExtImpRubicon extRubicon,
                                           BidRequest bidRequest, String impLanguage, boolean useFirstPartyData) {
        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(makeImp(imp, extPrebid, extRubicon, site, app, useFirstPartyData)))
                .user(makeUser(bidRequest.getUser(), extRubicon))
                .device(makeDevice(bidRequest.getDevice()))
                .site(makeSite(site, impLanguage, extRubicon))
                .app(makeApp(app, extRubicon))
                .source(makeSource(bidRequest.getSource(), extRubicon.getPchain()))
                .cur(null) // suppress currencies
                .ext(null) // suppress ext
                .build();
    }

    private String makeUri(BidRequest bidRequest) {
        final String tkXint = tkXintValue(bidRequest);
        if (StringUtils.isNotBlank(tkXint)) {
            try {
                return new URIBuilder(endpointUrl)
                        .setParameter(TK_XINT_QUERY_PARAMETER, tkXint)
                        .build().toString();
            } catch (URISyntaxException e) {
                throw new PreBidException(String.format("Cant add the tk_xint value for url: %s", tkXint), e);
            }
        }
        return endpointUrl;
    }

    private String tkXintValue(BidRequest bidRequest) {
        final RubiconExtPrebidBiddersBidder rubicon = extPrebidBiddersRubicon(bidRequest.getExt());
        final String integration = rubicon == null ? null : rubicon.getIntegration();
        return StringUtils.isBlank(integration) ? null : integration;
    }

    private RubiconExtPrebidBiddersBidder extPrebidBiddersRubicon(ExtRequest extRequest) {
        final ExtRequestPrebid prebid = extRequest == null ? null : extRequest.getPrebid();
        final ObjectNode biddersNode = prebid == null ? null : prebid.getBidders();
        if (biddersNode != null) {
            try {
                final RubiconExtPrebidBidders bidders = mapper.mapper().convertValue(biddersNode,
                        RubiconExtPrebidBidders.class);
                return bidders == null ? null : bidders.getBidder();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private Imp makeImp(Imp imp, ExtImpPrebid extPrebid, ExtImpRubicon extRubicon,
                        Site site, App app, boolean useFirstPartyData) {
        final Imp.ImpBuilder builder = imp.toBuilder()
                .metric(makeMetrics(imp))
                .ext(mapper.mapper().valueToTree(makeImpExt(imp, extRubicon, site, app, useFirstPartyData)));

        if (isVideo(imp)) {
            builder
                    .banner(null)
                    .video(makeVideo(imp.getVideo(), extRubicon.getVideo(), extPrebid));
        } else {
            builder
                    .banner(makeBanner(imp.getBanner(), overriddenSizes(extRubicon)))
                    .video(null);
        }

        return builder.build();
    }

    private List<Metric> makeMetrics(Imp imp) {
        final List<Metric> metrics = imp.getMetric();

        if (metrics == null) {
            return null;
        }

        final List<Metric> modifiedMetrics = new ArrayList<>();
        for (Metric metric : metrics) {
            if (isMetricSupported(metric)) {
                modifiedMetrics.add(metric.toBuilder().vendor("seller-declared").build());
            } else {
                modifiedMetrics.add(metric);
            }
        }

        return modifiedMetrics;
    }

    private boolean isMetricSupported(Metric metric) {
        return supportedVendors.contains(metric.getVendor()) && Objects.equals(metric.getType(), "viewability");
    }

    private RubiconImpExt makeImpExt(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app,
                                     boolean useFirstPartyData) {
        return RubiconImpExt.of(RubiconImpExtRp.of(rubiconImpExt.getZoneId(),
                makeTarget(imp, rubiconImpExt, site, app, useFirstPartyData), RubiconImpExtRpTrack.of("", "")),
                mapVendorsNamesToUrls(imp.getMetric()));
    }

    private JsonNode makeTarget(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app, boolean useFirstPartyData) {
        final ObjectNode inventory = rubiconImpExt.getInventory();
        final ObjectNode inventoryNode = inventory == null ? mapper.mapper().createObjectNode() : inventory;

        if (useFirstPartyData) {
            final ExtImpContext context = extImpContext(imp);

            // copy OPENRTB.site.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
            final ExtSite siteExt = site != null ? site.getExt() : null;
            if (siteExt != null) {
                populateObjectNode(inventoryNode, siteExt.getData());
            }

            // copy OPENRTB.app.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
            final ExtApp appExt = app != null ? app.getExt() : null;
            if (appExt != null) {
                populateObjectNode(inventoryNode, appExt.getData());
            }

            // copy OPENRTB.imp[].ext.context.data.* to XAPI.imp[].ext.rp.target.*
            final ObjectNode contextDataNode = context != null ? context.getData() : null;
            if (contextDataNode != null) {
                inventoryNode.setAll(contextDataNode);
                updateWithAdSlot(inventoryNode, contextDataNode);
            }

            // copy OPENRTB.imp[].ext.context.keywords to XAPI.imp[].ext.rp.target.keywords
            final String keywords = context != null ? context.getKeywords() : null;
            if (StringUtils.isNotBlank(keywords)) {
                inventoryNode.put("keywords", keywords);
            }

            // copy OPENRTB.imp[].ext.context.search to XAPI.imp[].ext.rp.target.search
            // copy OPENRTB.site.search to every impression XAPI.imp[].ext.rp.target.search
            // imp-specific values should take precedence over global values
            final String contextSearch = context != null ? context.getSearch() : null;
            final String siteSearch = site != null ? site.getSearch() : null;
            final String search = ObjectUtils.defaultIfNull(contextSearch, siteSearch);
            if (StringUtils.isNotBlank(search)) {
                inventoryNode.put("search", search);
            }
        }

        return inventoryNode.size() > 0 ? inventoryNode : null;
    }

    private void updateWithAdSlot(ObjectNode inventoryNode, ObjectNode contextDataNode) {
        // copy adslot to XAPI.imp[].ext.rp.target.dfp_ad_unit_code without leading slash
        final String adSlot = ObjectUtils.firstNonNull(
                getTextValueFromNodeByPath(contextDataNode, "adslot"),
                getAdSlotFromAdServer(contextDataNode),
                getTextValueFromNodeByPath(contextDataNode, "pbadslot"));
        if (adSlot != null) {
            updateInventoryWithAdSlot(inventoryNode, adSlot);
        }
    }

    private static String getTextValueFromNodeByPath(JsonNode node, String path) {
        final JsonNode nodeByPath = node != null ? node.get(path) : null;
        return nodeByPath != null && nodeByPath.isTextual() ? nodeByPath.textValue() : null;
    }

    private static String getAdSlotFromAdServer(JsonNode contextDataNode) {
        final JsonNode adServerNode = contextDataNode.get("adserver");

        final String adServerName = getTextValueFromNodeByPath(adServerNode, "name");
        if (Objects.equals(adServerName, "gam")) {
            return getTextValueFromNodeByPath(adServerNode, "adslot");
        }
        return null;
    }

    private static void updateInventoryWithAdSlot(ObjectNode inventoryNode, String adSlot) {
        final String adUnitCode = adSlot.indexOf('/') == 0 ? adSlot.substring(1) : adSlot;
        inventoryNode.put("dfp_ad_unit_code", adUnitCode);
    }

    private ExtImpContext extImpContext(Imp imp) {
        final JsonNode context = imp.getExt().get("context");
        if (context == null || context.isNull()) {
            return null;
        }
        try {
            return mapper.mapper().convertValue(context, ExtImpContext.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static void populateObjectNode(ObjectNode objectNode, JsonNode data) {
        if (data != null && !data.isNull()) {
            objectNode.setAll((ObjectNode) data);
        }
    }

    private List<String> mapVendorsNamesToUrls(List<Metric> metrics) {
        if (metrics == null) {
            return null;
        }
        final List<String> vendorsUrls = metrics.stream()
                .filter(this::isMetricSupported)
                .map(metric -> ViewabilityVendors.valueOf(metric.getVendor()).getUrl())
                .collect(Collectors.toList());
        return vendorsUrls.isEmpty() ? null : vendorsUrls;
    }

    private static boolean isVideo(Imp imp) {
        final Video video = imp.getVideo();
        if (video != null) {
            // Do any other media types exist? Or check required video fields.
            return imp.getBanner() == null || isFullyPopulatedVideo(video);
        }
        return false;
    }

    private static boolean isFullyPopulatedVideo(Video video) {
        // These are just recommended video fields for XAPI
        return video.getMimes() != null && video.getProtocols() != null && video.getMaxduration() != null
                && video.getLinearity() != null && video.getApi() != null;
    }

    private Video makeVideo(Video video, RubiconVideoParams rubiconVideoParams, ExtImpPrebid prebidImpExt) {
        final String videoType = prebidImpExt != null && prebidImpExt.getIsRewardedInventory() != null
                && prebidImpExt.getIsRewardedInventory() == 1 ? "rewarded" : null;

        if (rubiconVideoParams == null && videoType == null) {
            return video;
        }

        final Integer skip = rubiconVideoParams != null ? rubiconVideoParams.getSkip() : null;
        final Integer skipDelay = rubiconVideoParams != null ? rubiconVideoParams.getSkipdelay() : null;
        final Integer sizeId = rubiconVideoParams != null ? rubiconVideoParams.getSizeId() : null;
        return video.toBuilder()
                .ext(mapper.mapper().valueToTree(
                        RubiconVideoExt.of(skip, skipDelay, RubiconVideoExtRp.of(sizeId), videoType)))
                .build();
    }

    private static List<Format> overriddenSizes(ExtImpRubicon rubiconImpExt) {
        final List<Format> overriddenSizes;

        final List<Integer> sizeIds = rubiconImpExt.getSizes();
        if (sizeIds != null) {
            final List<Format> resolvedSizes = RubiconSize.idToSize(sizeIds);
            if (resolvedSizes.isEmpty()) {
                throw new PreBidException("Bad request.imp[].ext.rubicon.sizes");
            }
            overriddenSizes = resolvedSizes;
        } else {
            overriddenSizes = null;
        }

        return overriddenSizes;
    }

    private Banner makeBanner(Banner banner, List<Format> overriddenSizes) {
        final List<Format> sizes = ObjectUtils.defaultIfNull(overriddenSizes, banner.getFormat());
        if (CollectionUtils.isEmpty(sizes)) {
            throw new PreBidException("rubicon imps must have at least one imp.format element");
        }

        return banner.toBuilder()
                .format(sizes)
                .ext(mapper.mapper().valueToTree(makeBannerExt(sizes)))
                .build();
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> rubiconSizeIds = mapToRubiconSizeIds(sizes);
        final Integer primarySizeId = rubiconSizeIds.get(0);
        final List<Integer> altSizeIds = rubiconSizeIds.size() > 1
                ? rubiconSizeIds.subList(1, rubiconSizeIds.size())
                : null;

        return RubiconBannerExt.of(RubiconBannerExtRp.of(primarySizeId, altSizeIds, "text/html"));
    }

    private static List<Integer> mapToRubiconSizeIds(List<Format> sizes) {
        final List<Integer> validRubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .sorted(RubiconSize.comparator())
                .collect(Collectors.toList());

        if (validRubiconSizeIds.isEmpty()) {
            throw new PreBidException("No valid sizes");
        }
        return validRubiconSizeIds;
    }

    private User makeUser(User user, ExtImpRubicon rubiconImpExt) {
        final User result;

        final ExtUser extUser = user != null ? user.getExt() : null;
        final Map<String, List<ExtUserEid>> sourceToUserEidExt = extUser != null
                ? specialExtUserEids(extUser.getEids())
                : null;
        final List<ExtUserTpIdRubicon> userExtTpIds = sourceToUserEidExt != null
                ? extractExtUserTpIds(sourceToUserEidExt)
                : null;
        final RubiconUserExtRp userExtRp = rubiconUserExtRp(user, rubiconImpExt, sourceToUserEidExt);
        final ObjectNode userExtData = extUser != null ? extUser.getData() : null;

        if (userExtRp != null || userExtTpIds != null || userExtData != null) {
            final ExtUser userExt = extUser != null
                    ? ExtUser.builder().consent(extUser.getConsent())
                    .digitrust(extUser.getDigitrust())
                    .eids(extUser.getEids())
                    .build()
                    : ExtUser.builder().build();

            final RubiconUserExt rubiconUserExt = RubiconUserExt.builder()
                    .rp(userExtRp)
                    .tpid(userExtTpIds)
                    .build();

            final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();
            result = userBuilder
                    .ext(mapper.fillExtension(userExt, rubiconUserExt))
                    .build();
        } else {
            result = user;
        }

        return result;
    }

    private static Map<String, List<ExtUserEid>> specialExtUserEids(List<ExtUserEid> eids) {
        if (CollectionUtils.isEmpty(eids)) {
            return null;
        }

        return eids.stream()
                .filter(extUserEid -> StringUtils.equalsAny(extUserEid.getSource(), ADSERVER_EID, LIVEINTENT_EID))
                .filter(extUserEid -> CollectionUtils.isNotEmpty(extUserEid.getUids()))
                .collect(Collectors.groupingBy(ExtUserEid::getSource));
    }

    /**
     * Analyzes request.user.ext.eids and returns a list of new {@link ExtUserTpIdRubicon}s for supported vendors.
     */
    private static List<ExtUserTpIdRubicon> extractExtUserTpIds(Map<String, List<ExtUserEid>> specialExtUserEids) {
        final List<ExtUserTpIdRubicon> result = new ArrayList<>();

        specialExtUserEids.getOrDefault(ADSERVER_EID, Collections.emptyList()).stream()
                .map(extUserEid -> extUserTpIdForAdServer(extUserEid.getUids().get(0)))
                .filter(Objects::nonNull)
                .forEach(result::add);

        specialExtUserEids.getOrDefault(LIVEINTENT_EID, Collections.emptyList()).stream()
                .map(extUserEid -> extUserTpIdForLiveintent(extUserEid.getUids().get(0)))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(result::add);

        return result.isEmpty() ? null : result;
    }

    /**
     * Extracts {@link ExtUserTpIdRubicon} for AdServer.
     */
    private static ExtUserTpIdRubicon extUserTpIdForAdServer(ExtUserEidUid adServerEidUid) {
        final ExtUserEidUidExt ext = adServerEidUid != null ? adServerEidUid.getExt() : null;
        return ext != null && Objects.equals(ext.getRtiPartner(), "TDID")
                ? ExtUserTpIdRubicon.of("tdid", adServerEidUid.getId())
                : null;
    }

    /**
     * Extracts {@link ExtUserTpIdRubicon} for Liveintent.
     */
    private static ExtUserTpIdRubicon extUserTpIdForLiveintent(ExtUserEidUid adServerEidUid) {
        final String id = adServerEidUid != null ? adServerEidUid.getId() : null;
        return id != null ? ExtUserTpIdRubicon.of(LIVEINTENT_EID, id) : null;
    }

    private RubiconUserExtRp rubiconUserExtRp(User user, ExtImpRubicon rubiconImpExt,
                                              Map<String, List<ExtUserEid>> sourceToUserEidExt) {
        final ObjectNode impExtVisitor = rubiconImpExt.getVisitor();
        final ObjectNode visitor = impExtVisitor != null && impExtVisitor.size() != 0 ? impExtVisitor : null;

        final boolean hasUser = user != null;
        final String gender = hasUser ? user.getGender() : null;
        final Integer yob = hasUser ? user.getYob() : null;
        final Geo geo = hasUser ? user.getGeo() : null;

        final JsonNode target = rubiconUserExtRpTarget(sourceToUserEidExt, visitor);

        final ExtUser extUser = user != null ? user.getExt() : null;
        final ObjectNode data = extUser != null ? extUser.getData() : null;

        if (target != null || gender != null || yob != null || geo != null) {
            final RubiconUserExtRp rubiconUserExtRp = RubiconUserExtRp.of(target, gender, yob, geo);
            return data != null
                    ? mapper.fillExtension(rubiconUserExtRp, data)
                    : rubiconUserExtRp;
        }

        return null;
    }

    private JsonNode rubiconUserExtRpTarget(Map<String, List<ExtUserEid>> sourceToUserEidExt, ObjectNode visitor) {
        if (sourceToUserEidExt == null || CollectionUtils.isEmpty(sourceToUserEidExt.get(LIVEINTENT_EID))) {
            return visitor;
        }
        final ObjectNode ext = sourceToUserEidExt.get(LIVEINTENT_EID).get(0).getExt();
        final JsonNode segment = ext != null ? ext.get("segments") : null;

        if (segment == null) {
            return visitor;
        }
        final ObjectNode result = visitor != null ? visitor : mapper.mapper().createObjectNode();

        return result.set("LIseg", segment);
    }

    private Device makeDevice(Device device) {
        return device == null ? null : device.toBuilder()
                .ext(mapper.fillExtension(
                        ExtDevice.empty(),
                        RubiconDeviceExt.of(RubiconDeviceExtRp.of(device.getPxratio()))))
                .build();
    }

    private Site makeSite(Site site, String impLanguage, ExtImpRubicon rubiconImpExt) {
        if (site == null && StringUtils.isBlank(impLanguage)) {
            return null;
        }

        return site == null
                ? Site.builder().content(makeSiteContent(null, impLanguage)).build()
                : site.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .content(makeSiteContent(site.getContent(), impLanguage))
                .ext(makeSiteExt(site, rubiconImpExt))
                .build();
    }

    private static Content makeSiteContent(Content siteContent, String impLanguage) {
        if (StringUtils.isBlank(impLanguage)) {
            return siteContent;
        }
        if (siteContent == null) {
            return Content.builder().language(impLanguage).build();
        } else {
            return StringUtils.isBlank(siteContent.getLanguage())
                    ? siteContent.toBuilder().language(impLanguage).build()
                    : siteContent;
        }
    }

    private Publisher makePublisher(ExtImpRubicon rubiconImpExt) {
        return Publisher.builder()
                .ext(makePublisherExt(rubiconImpExt))
                .build();
    }

    private ExtPublisher makePublisherExt(ExtImpRubicon rubiconImpExt) {
        return mapper.fillExtension(
                ExtPublisher.empty(),
                RubiconPubExt.of(RubiconPubExtRp.of(rubiconImpExt.getAccountId())));
    }

    private ExtSite makeSiteExt(Site site, ExtImpRubicon rubiconImpExt) {
        final ExtSite extSite = site != null ? extSite(site) : null;
        final Integer siteExtAmp = extSite != null ? extSite.getAmp() : null;

        return mapper.fillExtension(
                ExtSite.of(siteExtAmp, null),
                RubiconSiteExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId())));
    }

    private ExtSite extSite(Site site) {
        try {
            return mapper.mapper().convertValue(site.getExt(), ExtSite.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e.getCause());
        }
    }

    private App makeApp(App app, ExtImpRubicon rubiconImpExt) {
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .ext(makeAppExt(rubiconImpExt))
                .build();
    }

    private ExtApp makeAppExt(ExtImpRubicon rubiconImpExt) {
        return mapper.fillExtension(ExtApp.of(null, null),
                RubiconAppExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId())));
    }

    private static Source makeSource(Source source, String pchain) {
        if (StringUtils.isNotEmpty(pchain)) {
            final Source.SourceBuilder builder = source != null ? source.toBuilder() : Source.builder();
            return builder.pchain(pchain).build();
        }
        return source;
    }

    private List<BidderBid> extractBids(BidRequest prebidRequest, BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(prebidRequest, bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest prebidRequest, BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, Imp> idToImp = prebidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
        final Float cmpOverrideFromRequest = cmpOverrideFromRequest(prebidRequest);
        final BidType bidType = bidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> updateBid(bid, idToImp.get(bid.getImpid()), cmpOverrideFromRequest, bidResponse))
                .filter(bid -> bid.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .map(bid -> BidderBid.of(bid, bidType, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private Bid updateBid(Bid bid, Imp imp, Float cmpOverrideFromRequest, BidResponse bidResponse) {
        if (generateBidId) {
            // Since Rubicon XAPI returns openrtb_response.seatbid.bid.id not unique enough
            // generate new value for it
            bid.setId(UUID.randomUUID().toString());
        } else if (Objects.equals(bid.getId(), "0")) {
            // Since Rubicon XAPI returns only one bid per response
            // copy bidResponse.bidid to openrtb_response.seatbid.bid.id
            bid.setId(bidResponse.getBidid());
        }

        // Unconditionally set price if coming from CPM override
        final Float cpmOverride = ObjectUtils.defaultIfNull(cpmOverrideFromImp(imp), cmpOverrideFromRequest);
        if (cpmOverride != null) {
            bid.setPrice(new BigDecimal(String.valueOf(cpmOverride)));
        }

        return bid;
    }

    private Float cmpOverrideFromRequest(BidRequest bidRequest) {
        final RubiconExtPrebidBiddersBidder bidder = extPrebidBiddersRubicon(bidRequest.getExt());
        final RubiconExtPrebidBiddersBidderDebug debug = bidder != null ? bidder.getDebug() : null;
        return debug != null ? debug.getCpmoverride() : null;
    }

    private Float cpmOverrideFromImp(Imp imp) {
        final JsonNode extImpPrebidNode = imp.getExt().get(PREBID_EXT);
        final ExtImpPrebid prebid = extImpPrebidNode != null ? extImpPrebid(extImpPrebidNode) : null;
        final RubiconImpExtPrebidBidder bidder = prebid != null ? extImpPrebidBidder(prebid.getBidder()) : null;
        final RubiconImpExtPrebidRubiconDebug debug = bidder != null ? bidder.getDebug() : null;
        return debug != null ? debug.getCpmoverride() : null;
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return mapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding imp.ext.prebid: %s", e.getMessage()), e);
        }
    }

    private RubiconImpExtPrebidBidder extImpPrebidBidder(JsonNode extImpPrebidBidder) {
        try {
            return mapper.mapper().treeToValue(extImpPrebidBidder, RubiconImpExtPrebidBidder.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding imp.ext.prebid.bidder: %s", e.getMessage()), e);
        }
    }

    private static BidType bidType(BidRequest bidRequest) {
        return isVideo(bidRequest.getImp().get(0)) ? BidType.video : BidType.banner;
    }
}
