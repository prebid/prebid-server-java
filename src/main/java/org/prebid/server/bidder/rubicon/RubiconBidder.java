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
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.ViewabilityVendors;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

    private static final String APPLICATION_JSON_UTF_8 = HttpHeaderValues.APPLICATION_JSON.toString() + ";"
            + HttpHeaderValues.CHARSET.toString() + "=" + StandardCharsets.UTF_8.toString().toLowerCase();

    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private static final TypeReference<ExtPrebid<?, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpRubicon>>() {
            };

    private final String endpointUrl;
    private final MultiMap headers;
    private final Set<String> supportedVendors;

    public RubiconBidder(String endpoint, String xapiUsername, String xapiPassword, MetaInfo rubiconMetaInfo) {
        endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        headers = headers(Objects.requireNonNull(xapiUsername), Objects.requireNonNull(xapiPassword));
        supportedVendors = new HashSet<>(Objects.requireNonNull(rubiconMetaInfo).info().getVendors());
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        for (final Imp imp : bidRequest.getImp()) {
            try {
                final BidRequest singleRequest = createSingleRequest(imp, bidRequest);
                final String body = Json.encode(singleRequest);
                httpRequests.add(HttpRequest.of(HttpMethod.POST, endpointUrl, body, headers, singleRequest));
            } catch (PreBidException e) {
                errors.add(e.getMessage());
            }
        }

        return Result.of(httpRequests, BidderUtil.errors(errors));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            return Result.of(
                    extractBids(httpCall.getRequest().getPayload(), BidderUtil.parseResponse(httpCall.getResponse())),
                    Collections.emptyList());
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.create(e.getMessage())));
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
                .add(HttpHeaders.AUTHORIZATION, authHeader(xapiUsername, xapiPassword))
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_UTF_8)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpHeaders.USER_AGENT, PREBID_SERVER_USER_AGENT);
    }

    private static String authHeader(String xapiUsername, String xapiPassword) {
        return "Basic " + Base64.getEncoder().encodeToString((xapiUsername + ':' + xapiPassword).getBytes());
    }

    private BidRequest createSingleRequest(Imp imp, BidRequest bidRequest) {
        final ExtImpRubicon rubiconImpExt = parseRubiconExt(imp);

        return bidRequest.toBuilder()
                .user(makeUser(bidRequest.getUser(), rubiconImpExt))
                .device(makeDevice(bidRequest.getDevice()))
                .site(makeSite(bidRequest.getSite(), rubiconImpExt))
                .app(makeApp(bidRequest.getApp(), rubiconImpExt))
                .imp(Collections.singletonList(makeImp(imp, rubiconImpExt)))
                .build();
    }

    private static ExtImpRubicon parseRubiconExt(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpRubicon>>convertValue(imp.getExt(), RUBICON_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            logger.warn("Error occurred parsing rubicon parameters", e);
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private Imp makeImp(Imp imp, ExtImpRubicon rubiconImpExt) {
        final Imp.ImpBuilder builder = imp.toBuilder()
                .metric(makeMetrics(imp))
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconImpExt, imp)));

        final Video video = imp.getVideo();
        if (video != null) {
            builder.video(makeVideo(video, rubiconImpExt.getVideo()));
        } else {
            builder.banner(makeBanner(imp.getBanner()));
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
        return supportedVendors.contains(metric.getVendor()) && metric.getType().equals("viewability");
    }

    private RubiconImpExt makeImpExt(ExtImpRubicon rubiconImpExt, Imp imp) {
        return RubiconImpExt.of(RubiconImpExtRp.of(rubiconImpExt.getZoneId(), makeInventory(rubiconImpExt),
                RubiconImpExtRpTrack.of("", "")), mapVendorsNamesToUrls(imp.getMetric()));
    }

    private static JsonNode makeInventory(ExtImpRubicon rubiconImpExt) {
        final JsonNode inventory = rubiconImpExt.getInventory();
        return inventory.isNull() ? null : inventory;
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

    private static Video makeVideo(Video video, RubiconVideoParams rubiconVideoParams) {
        return rubiconVideoParams == null ? video : video.toBuilder()
                .ext(Json.mapper.valueToTree(
                        RubiconVideoExt.of(rubiconVideoParams.getSkip(), rubiconVideoParams.getSkipdelay(),
                                RubiconVideoExtRp.of(rubiconVideoParams.getSizeId()))))
                .build();
    }

    private static Banner makeBanner(Banner banner) {
        return banner.toBuilder()
                .ext(Json.mapper.valueToTree(makeBannerExt(banner.getFormat())))
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
        User result = user;

        final JsonNode visitor = rubiconImpExt.getVisitor();
        final RubiconUserExtRp userExtRp = user != null && !visitor.isNull()
                ? RubiconUserExtRp.of(visitor)
                : null;

        final ExtUser extUser = user != null ? getExtUser(user.getExt()) : null;

        final ExtUserDigiTrust userExtDt = extUser != null ? extUser.getDigitrust() : null;

        final String consent = extUser != null ? extUser.getConsent() : null;

        if (userExtRp != null || userExtDt != null || consent != null) {
            result = user.toBuilder()
                    .ext(Json.mapper.valueToTree(RubiconUserExt.of(userExtRp, consent, userExtDt)))
                    .build();
        }

        return result;
    }

    private static ExtRegs getExtRegs(ObjectNode extNode) {
        try {
            return extNode != null ? Json.mapper.treeToValue(extNode, ExtRegs.class) : null;
        } catch (JsonProcessingException e) {
            logger.warn("Error occurred while parsing bidrequest.regs.ext", e);
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static ExtUser getExtUser(ObjectNode extNode) {
        try {
            return extNode != null ? Json.mapper.treeToValue(extNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            logger.warn("Error occurred while parsing bidrequest.user.ext", e);
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
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconImpExt)))
                .build();
    }

    private static RubiconSiteExt makeSiteExt(ExtImpRubicon rubiconImpExt) {
        return RubiconSiteExt.of(RubiconSiteExtRp.of(rubiconImpExt.getSiteId()));
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
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconImpExt)))
                .build();
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
                .map(bid -> BidderBid.of(bid, bidType(bidRequest), DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType bidType(BidRequest bidRequest) {
        return bidRequest.getImp().get(0).getVideo() != null ? BidType.video : BidType.banner;
    }
}
