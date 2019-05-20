package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.ViewabilityVendors;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.proto.RubiconAppExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExt;
import org.prebid.server.bidder.rubicon.proto.RubiconDeviceExtRp;
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
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTpId;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconImpExtContext;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
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

    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";
    private static final String DEFAULT_BID_CURRENCY = "USD";

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

        for (final Imp imp : bidRequest.getImp()) {
            try {
                final BidRequest singleRequest = createSingleRequest(imp, bidRequest);
                final String body = Json.encode(singleRequest);
                httpRequests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
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
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
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

    private BidRequest createSingleRequest(Imp imp, BidRequest bidRequest) {
        final ExtImpRubicon rubiconImpExt = parseRubiconExt(imp);

        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(makeImp(imp, rubiconImpExt, site, app)))
                .user(makeUser(bidRequest.getUser(), rubiconImpExt))
                .device(makeDevice(bidRequest.getDevice()))
                .site(makeSite(site, rubiconImpExt))
                .app(makeApp(app, rubiconImpExt))
                .cur(null) // suppress currencies
                .build();
    }

    private static ExtImpRubicon parseRubiconExt(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpRubicon>>convertValue(imp.getExt(), RUBICON_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp makeImp(Imp imp, ExtImpRubicon rubiconImpExt, Site site, App app) {
        final Imp.ImpBuilder builder = imp.toBuilder()
                .metric(makeMetrics(imp))
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconImpExt, imp, site, app)));

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

    private RubiconImpExt makeImpExt(ExtImpRubicon rubiconImpExt, Imp imp, Site site, App app) {
        return RubiconImpExt.of(RubiconImpExtRp.of(rubiconImpExt.getZoneId(), makeTarget(rubiconImpExt, site, app),
                RubiconImpExtRpTrack.of("", "")), mapVendorsNamesToUrls(imp.getMetric()));
    }

    private static JsonNode makeTarget(ExtImpRubicon rubiconImpExt, Site site, App app) {
        final JsonNode inventory = rubiconImpExt.getInventory();
        if (inventory == null || inventory.isNull()) {
            return null;
        }

        final RubiconImpExtContext context = rubiconImpExt.getContext();
        final ObjectNode contextDataNode = context != null ? context.getData() : null;

        final ObjectNode inventoryNode = (ObjectNode) inventory;
        if (contextDataNode != null && !contextDataNode.isNull()) {
            inventoryNode.setAll(contextDataNode);
        }

        if (site != null) {
            final String search = site.getSearch();
            if (search != null) {
                inventoryNode.put("search", search);
            }
            final ObjectNode siteExt = site.getExt();
            if (siteExt != null && !siteExt.isNull()) {
                return resolveJsonNode(inventoryNode, siteExt.get("data"));
            }
        }

        if (app != null && app.getExt() != null && !app.getExt().isNull()) {
            return resolveJsonNode(inventoryNode, app.getExt().get("data"));
        }
        return inventory;
    }

    private static JsonNode resolveJsonNode(ObjectNode baseNode, JsonNode data) {
        return data != null && !data.isNull() ? baseNode.setAll((ObjectNode) data) : baseNode;
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
        final boolean hasUser = user != null;
        final String gender = hasUser ? user.getGender() : null;
        final Integer yob = hasUser ? user.getYob() : null;
        final Geo geo = hasUser ? user.getGeo() : null;
        final JsonNode visitor = rubiconImpExt.getVisitor();
        final boolean hasAnyNotNull = gender != null || yob != null || geo != null || !visitor.isNull();

        final RubiconUserExtRp userExtRp = hasAnyNotNull
                ? RubiconUserExtRp.of(!visitor.isNull() ? visitor : null, gender, yob, geo) : null;

        final ExtUser extUser = hasUser ? getExtUser(user.getExt()) : null;
        final ExtUserDigiTrust userExtDt = extUser != null ? extUser.getDigitrust() : null;
        final String consent = extUser != null ? extUser.getConsent() : null;
        final List<ExtUserTpId> tpid = extUser != null ? extUser.getTpid() : null;

        final ObjectNode data = extUser != null ? extUser.getData() : null;

        final ObjectNode userExt = Json.mapper.valueToTree(RubiconUserExt.of(userExtRp, consent, userExtDt, tpid));

        if (userExtRp != null) {
            final JsonNode userExtRpNode = resolveJsonNode(Json.mapper.valueToTree(userExtRp), data);
            userExt.set("rp", userExtRpNode);
        }

        if (userExtRp != null || userExtDt != null || consent != null || tpid != null) {
            final User.UserBuilder userBuilder = hasUser ? user.toBuilder() : User.builder();
            return userBuilder
                    .ext(userExt)
                    .build();
        }
        return user;
    }

    private static ExtUser getExtUser(ObjectNode extNode) {
        try {
            return extNode != null ? Json.mapper.treeToValue(extNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Device makeDevice(Device device) {
        return device == null ? null : device.toBuilder()
                .ext(Json.mapper.valueToTree(RubiconDeviceExt.of(RubiconDeviceExtRp.of(device.getPxratio()))))
                .build();
    }

    private static Site makeSite(Site site, ExtImpRubicon rubiconImpExt) {
        return site == null ? null : site.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .ext(Json.mapper.valueToTree(makeSiteExt(site, rubiconImpExt)))
                .build();
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

    private static Publisher makePublisher(ExtImpRubicon rubiconImpExt) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconImpExt)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(ExtImpRubicon rubiconImpExt) {
        return RubiconPubExt.of(RubiconPubExtRp.of(rubiconImpExt.getAccountId()));
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
