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
import org.prebid.server.bidder.BidderName;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.model.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.model.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.model.RubiconDeviceExt;
import org.prebid.server.bidder.rubicon.model.RubiconDeviceExtRp;
import org.prebid.server.bidder.rubicon.model.RubiconImpExt;
import org.prebid.server.bidder.rubicon.model.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.model.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.model.RubiconPubExt;
import org.prebid.server.bidder.rubicon.model.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.model.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.model.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.model.RubiconUserExt;
import org.prebid.server.bidder.rubicon.model.RubiconUserExtRp;
import org.prebid.server.bidder.rubicon.model.RubiconVideoExt;
import org.prebid.server.bidder.rubicon.model.RubiconVideoExtRp;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <a href="https://rubiconproject.com">Rubicon Project</a> {@link Bidder} implementation.
 * <p>
 * Maintainer email: <a href="mailto:header-bidding@rubiconproject.com">header-bidding@rubiconproject.com</a>
 */
public class RubiconBidder extends OpenrtbBidder {

    private static final Logger logger = LoggerFactory.getLogger(RubiconBidder.class);

    private static final String NAME = BidderName.rubicon.name();

    private static final String APPLICATION_JSON_UTF_8 = HttpHeaderValues.APPLICATION_JSON.toString() + ";"
            + HttpHeaderValues.CHARSET.toString() + "=" + StandardCharsets.UTF_8.toString().toLowerCase();

    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private static final TypeReference<ExtPrebid<?, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpRubicon>>() {
            };

    private final String endpointUrl;
    private final MultiMap headers;

    public RubiconBidder(String endpoint, String xapiUsername, String xapiPassword) {
        endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpoint));
        headers = headers(Objects.requireNonNull(xapiUsername), Objects.requireNonNull(xapiPassword));
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Result<List<HttpRequest>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest> httpRequests = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        for (final Imp imp : bidRequest.getImp()) {
            try {
                final String body = Json.encode(createSingleRequest(imp, bidRequest));
                httpRequests.add(HttpRequest.of(HttpMethod.POST, endpointUrl, body, headers));
            } catch (PreBidException e) {
                errors.add(e.getMessage());
            }
        }

        return Result.of(httpRequests, errors(errors));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(bidRequest, parseResponse(httpCall.getResponse())), Collections.emptyList());
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(BidderError.create(e.getMessage())));
        }
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

    private static BidRequest createSingleRequest(Imp imp, BidRequest bidRequest) {
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

    private static Imp makeImp(Imp imp, ExtImpRubicon rubiconImpExt) {
        final Imp.ImpBuilder builder = imp.toBuilder()
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconImpExt)));

        final Video video = imp.getVideo();
        if (video != null) {
            builder.video(makeVideo(video, rubiconImpExt.getVideo()));
        } else {
            builder.banner(makeBanner(imp.getBanner()));
        }

        return builder.build();
    }

    private static RubiconImpExt makeImpExt(ExtImpRubicon rubiconImpExt) {
        return RubiconImpExt.of(RubiconImpExtRp.of(rubiconImpExt.getZoneId(), makeInventory(rubiconImpExt),
                RubiconImpExtRpTrack.of("", "")));
    }

    private static JsonNode makeInventory(ExtImpRubicon rubiconImpExt) {
        final JsonNode inventory = rubiconImpExt.getInventory();
        return inventory.isNull() ? null : inventory;
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

        final ExtUserDigiTrust userExtDt = user != null && user.getExt() != null
                ? getExtUserDigiTrustFromUserExt(user.getExt())
                : null;

        if (userExtRp != null || userExtDt != null) {
            result = user.toBuilder()
                    .ext(Json.mapper.valueToTree(RubiconUserExt.of(userExtRp, userExtDt)))
                    .build();
        }

        return result;
    }

    private static ExtUserDigiTrust getExtUserDigiTrustFromUserExt(ObjectNode extNode) {
        try {
            final ExtUser extUser = Json.mapper.treeToValue(extNode, ExtUser.class);
            return extUser != null ? extUser.getDigitrust() : null;
        } catch (JsonProcessingException e) {
            logger.warn("Error occurred while parsing bidrequest.user.ext", e);
            throw new PreBidException(e.getMessage());
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
        final Map<String, BidType> impidToBidType = impidToBidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(bid -> bid.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .map(bid -> BidderBid.of(bid, bidType(bid, impidToBidType)))
                .collect(Collectors.toList());
    }
}
