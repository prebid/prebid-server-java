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
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
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
import org.prebid.server.bidder.rubicon.proto.RubiconExt;
import org.prebid.server.bidder.rubicon.proto.RubiconExtPrebid;
import org.prebid.server.bidder.rubicon.proto.RubiconExtPrebidBidders;
import org.prebid.server.bidder.rubicon.proto.RubiconExtPrebidBiddersBidder;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExt;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
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
    private static final String DATA_NODE_NAME = "data";

    private static final TypeReference<ExtPrebid<?, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpRubicon>>() {
            };

    private final String endpointUrl;
    private final MultiMap headers;
    private final Set<String> supportedVendors;

    public RubiconBidder(String endpoint, String xapiUsername, String xapiPassword, List<String> supportedVendors) {
        endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        headers = headers(Objects.requireNonNull(xapiUsername), Objects.requireNonNull(xapiPassword));
        this.supportedVendors = new HashSet<>(supportedVendors);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final boolean useFirstPartyData = useFirstPartyData(bidRequest);
        final Map<Imp, ExtImpRubicon> impToExtImpRubicon = parseRubiconImpExts(bidRequest.getImp(), errors);
        final String impLanguage = firstImpExtLanguage(impToExtImpRubicon.values());

        for (Map.Entry<Imp, ExtImpRubicon> impToExt : impToExtImpRubicon.entrySet()) {
            try {
                final Imp imp = impToExt.getKey();
                final ExtImpRubicon ext = impToExt.getValue();
                final BidRequest singleRequest = createSingleRequest(imp, ext, bidRequest, impLanguage,
                        useFirstPartyData);
                final String body = Json.encode(singleRequest);
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
            final BidResponse bidResponse = Json.decodeValue(response.getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode extBidBidder) {
        final RubiconTargetingExt rubiconTargetingExt;
        try {
            rubiconTargetingExt = Json.mapper.convertValue(extBidBidder, RubiconTargetingExt.class);
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
    private static boolean useFirstPartyData(BidRequest bidRequest) {
        final ExtBidRequest extBidRequest = bidRequest.getExt() != null
                ? Json.mapper.convertValue(bidRequest.getExt(), ExtBidRequest.class)
                : null;
        final ExtRequestPrebid prebid = extBidRequest == null ? null : extBidRequest.getPrebid();
        final ExtRequestPrebidData data = prebid == null ? null : prebid.getData();
        final List<String> bidders = data == null ? null : data.getBidders();
        return CollectionUtils.isNotEmpty(bidders); // this contains only current bidder
    }

    private static Map<Imp, ExtImpRubicon> parseRubiconImpExts(List<Imp> imps, List<BidderError> errors) {
        final Map<Imp, ExtImpRubicon> impToRubiconExt = new HashMap<>();
        for (final Imp imp : imps) {
            try {
                final ExtImpRubicon rubiconImpExt = parseRubiconExt(imp);
                impToRubiconExt.put(imp, rubiconImpExt);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return impToRubiconExt;
    }

    private static ExtImpRubicon parseRubiconExt(Imp imp) {
        try {
            return Json.mapper.convertValue(imp.getExt(), RUBICON_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static String firstImpExtLanguage(Collection<ExtImpRubicon> rubiconImpExts) {
        return rubiconImpExts.stream()
                .map(ExtImpRubicon::getVideo)
                .filter(Objects::nonNull)
                .map(RubiconVideoParams::getLanguage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private BidRequest createSingleRequest(Imp imp, ExtImpRubicon rubiconImpExt, BidRequest bidRequest,
                                           String impLanguage, boolean useFirstPartyData) {
        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(makeImp(imp, rubiconImpExt, site, app, useFirstPartyData)))
                .user(makeUser(bidRequest.getUser(), rubiconImpExt))
                .device(makeDevice(bidRequest.getDevice()))
                .site(makeSite(site, impLanguage, rubiconImpExt))
                .app(makeApp(app, rubiconImpExt))
                .source(makeSource(bidRequest.getSource(), rubiconImpExt.getPchain()))
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

    private static String tkXintValue(BidRequest bidRequest) {
        try {
            final RubiconExt rubiconExt = Json.mapper.convertValue(bidRequest.getExt(), RubiconExt.class);
            final RubiconExtPrebid prebid = rubiconExt == null ? null : rubiconExt.getPrebid();
            final RubiconExtPrebidBidders bidders = prebid == null ? null : prebid.getBidders();
            final RubiconExtPrebidBiddersBidder rubiconBidder = bidders == null ? null : bidders.getBidder();
            final String integration = rubiconBidder == null ? null : rubiconBidder.getIntegration();

            return StringUtils.isBlank(integration) ? null : integration;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Imp makeImp(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app, boolean useFirstPartyData) {
        final Imp.ImpBuilder builder = imp.toBuilder()
                .metric(makeMetrics(imp))
                .ext(Json.mapper.valueToTree(makeImpExt(imp, rubiconImpExt, site, app, useFirstPartyData)));

        if (isVideo(imp)) {
            builder
                    .banner(null)
                    .video(makeVideo(imp.getVideo(), rubiconImpExt.getVideo()));
        } else {
            builder
                    .banner(makeBanner(imp.getBanner(), overriddenSizes(rubiconImpExt)))
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

    private static JsonNode makeTarget(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app,
                                       boolean useFirstPartyData) {
        final ObjectNode inventory = rubiconImpExt.getInventory();
        final ObjectNode inventoryNode = inventory == null ? Json.mapper.createObjectNode() : inventory;

        if (useFirstPartyData) {
            final ExtImpContext context = extImpContext(imp);

            // copy OPENRTB.site.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
            final ObjectNode siteExt = site != null ? site.getExt() : null;
            if (siteExt != null) {
                populateObjectNode(inventoryNode, getDataNode(siteExt));
            }

            // copy OPENRTB.app.ext.data.* to every impression – XAPI.imp[].ext.rp.target.*
            final ObjectNode appExt = app != null ? app.getExt() : null;
            if (appExt != null) {
                populateObjectNode(inventoryNode, getDataNode(appExt));
            }

            // copy OPENRTB.imp[].ext.context.data.* to XAPI.imp[].ext.rp.target.*
            final ObjectNode contextDataNode = context != null ? context.getData() : null;
            if (contextDataNode != null) {
                inventoryNode.setAll(contextDataNode);

                // copy OPENRTB.imp[].ext.context.data.adslot to XAPI.imp[].ext.rp.target.dfp_ad_unit_code without
                // leading slash
                final JsonNode adSlotNode = contextDataNode.get("adslot");
                if (adSlotNode != null && adSlotNode.isTextual()) {
                    final String adSlot = adSlotNode.textValue();
                    final String adUnitCode = adSlot.indexOf('/') == 0 ? adSlot.substring(1) : adSlot;
                    inventoryNode.put("dfp_ad_unit_code", adUnitCode);
                }
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

    private static ExtImpContext extImpContext(Imp imp) {
        final JsonNode context = imp.getExt().get("context");
        if (context == null || context.isNull()) {
            return null;
        }
        try {
            return Json.mapper.convertValue(context, ExtImpContext.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static JsonNode getDataNode(ObjectNode extObjectNode) {
        return extObjectNode.get(DATA_NODE_NAME);
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

    private static Video makeVideo(Video video, RubiconVideoParams rubiconVideoParams) {
        return rubiconVideoParams == null ? video : video.toBuilder()
                .ext(Json.mapper.valueToTree(
                        RubiconVideoExt.of(rubiconVideoParams.getSkip(), rubiconVideoParams.getSkipdelay(),
                                RubiconVideoExtRp.of(rubiconVideoParams.getSizeId()))))
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

    private static Banner makeBanner(Banner banner, List<Format> overriddenSizes) {
        final List<Format> sizes = ObjectUtils.defaultIfNull(overriddenSizes, banner.getFormat());
        if (CollectionUtils.isEmpty(sizes)) {
            throw new PreBidException("rubicon imps must have at least one imp.format element");
        }

        return banner.toBuilder()
                .format(sizes)
                .ext(Json.mapper.valueToTree(makeBannerExt(sizes)))
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

    private static User makeUser(User user, ExtImpRubicon rubiconImpExt) {
        final User result;

        final ExtUser extUser = user != null ? extUser(user.getExt()) : null;
        final Map<String, List<ExtUserEid>> sourceToUserEidExt = extUser != null
                ? specialExtUserEids(extUser.getEids())
                : null;
        final List<ExtUserTpIdRubicon> userExtTpIds = sourceToUserEidExt != null
                ? extractExtUserTpIds(sourceToUserEidExt)
                : null;
        final RubiconUserExtRp userExtRp = rubiconUserExtRp(user, rubiconImpExt, sourceToUserEidExt);
        final ObjectNode userExtData = extUser != null ? extUser.getData() : null;

        if (userExtRp != null || userExtTpIds != null || userExtData != null) {
            final RubiconUserExt.RubiconUserExtBuilder userExtBuilder = RubiconUserExt.builder();
            if (extUser != null) {
                userExtBuilder
                        .consent(extUser.getConsent())
                        .digitrust(extUser.getDigitrust())
                        .eids(extUser.getEids());
            }

            final RubiconUserExt rubiconUserExt = userExtBuilder
                    .rp(userExtRp)
                    .tpid(userExtTpIds)
                    .build();
            final ObjectNode rubiconUserExtNode = Json.mapper.valueToTree(rubiconUserExt);

            if (userExtData != null) {
                final ObjectNode userExtRpNode = userExtRp != null
                        ? Json.mapper.valueToTree(userExtRp)
                        : Json.mapper.createObjectNode();

                userExtRpNode.setAll(userExtData);

                rubiconUserExtNode.set("rp", userExtRpNode);
            }

            final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();
            result = userBuilder
                    .ext(rubiconUserExtNode)
                    .build();
        } else {
            result = user;
        }

        return result;
    }

    /**
     * Extracts {@link ExtUser} from request.user.ext or returns null if not presents.
     */
    private static ExtUser extUser(ObjectNode extNode) {
        try {
            return extNode != null ? Json.mapper.treeToValue(extNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(String.format("Error decoding bidRequest.user.ext: %s", e.getMessage()), e);
        }
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

    private static RubiconUserExtRp rubiconUserExtRp(User user, ExtImpRubicon rubiconImpExt,
                                                     Map<String, List<ExtUserEid>> sourceToUserEidExt) {
        final ObjectNode impExtVisitor = rubiconImpExt.getVisitor();
        final ObjectNode visitor = impExtVisitor != null && impExtVisitor.size() != 0 ? impExtVisitor : null;

        final boolean hasUser = user != null;
        final String gender = hasUser ? user.getGender() : null;
        final Integer yob = hasUser ? user.getYob() : null;
        final Geo geo = hasUser ? user.getGeo() : null;

        final JsonNode target = rubiconUserExtRpTarget(sourceToUserEidExt, visitor);

        return target != null || gender != null || yob != null || geo != null
                ? RubiconUserExtRp.of(target, gender, yob, geo)
                : null;
    }

    private static JsonNode rubiconUserExtRpTarget(Map<String, List<ExtUserEid>> sourceToUserEidExt,
                                                   ObjectNode visitor) {
        if (sourceToUserEidExt == null || CollectionUtils.isEmpty(sourceToUserEidExt.get(LIVEINTENT_EID))) {
            return visitor;
        }
        final ObjectNode ext = sourceToUserEidExt.get(LIVEINTENT_EID).get(0).getExt();
        final JsonNode segment = ext != null ? ext.get("segments") : null;

        if (segment == null) {
            return visitor;
        }
        final ObjectNode result = visitor != null ? visitor : Json.mapper.createObjectNode();

        return result.set("LIseg", segment);
    }

    private static Device makeDevice(Device device) {
        return device == null ? null : device.toBuilder()
                .ext(Json.mapper.valueToTree(RubiconDeviceExt.of(RubiconDeviceExtRp.of(device.getPxratio()))))
                .build();
    }

    private static Site makeSite(Site site, String impLanguage, ExtImpRubicon rubiconImpExt) {
        if (site == null && StringUtils.isBlank(impLanguage)) {
            return null;
        }

        return site == null
                ? Site.builder().content(makeSiteContent(null, impLanguage)).build()
                : site.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .content(makeSiteContent(site.getContent(), impLanguage))
                .ext(Json.mapper.valueToTree(makeSiteExt(site, rubiconImpExt)))
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

    private static Publisher makePublisher(ExtImpRubicon rubiconImpExt) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconImpExt)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(ExtImpRubicon rubiconImpExt) {
        return RubiconPubExt.of(RubiconPubExtRp.of(rubiconImpExt.getAccountId()));
    }

    private static RubiconSiteExt makeSiteExt(Site site, ExtImpRubicon rubiconImpExt) {
        ExtSite extSite = null;
        if (site != null) {
            try {
                extSite = Json.mapper.convertValue(site.getExt(), ExtSite.class);
            } catch (IllegalArgumentException e) {
                throw new PreBidException(e.getMessage(), e.getCause());
            }
        }
        final Integer siteExtAmp = extSite != null ? extSite.getAmp() : null;
        return RubiconSiteExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId()), siteExtAmp);
    }

    private static App makeApp(App app, ExtImpRubicon rubiconImpExt) {
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .ext(Json.mapper.valueToTree(makeAppExt(rubiconImpExt)))
                .build();
    }

    private static RubiconAppExt makeAppExt(ExtImpRubicon rubiconImpExt) {
        return RubiconAppExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId()));
    }

    private static Source makeSource(Source source, String pchain) {
        if (StringUtils.isNotEmpty(pchain)) {
            final Source.SourceBuilder builder = source != null ? source.toBuilder() : Source.builder();
            return builder.pchain(pchain).build();
        }
        return source;
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(bid -> bid.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .map(bid -> updateBid(bid, bidResponse))
                .map(bid -> BidderBid.of(bid, bidType(bidRequest), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static Bid updateBid(Bid bid, BidResponse bidResponse) {
        // Since Rubicon XAPI returns only one bid per response
        // copy bidResponse.bidid to openrtb_response.seatbid.bid.bidid
        if (Objects.equals(bid.getId(), "0")) {
            bid.setId(bidResponse.getBidid());
        }
        return bid;
    }

    private static BidType bidType(BidRequest bidRequest) {
        return isVideo(bidRequest.getImp().get(0)) ? BidType.video : BidType.banner;
    }
}
