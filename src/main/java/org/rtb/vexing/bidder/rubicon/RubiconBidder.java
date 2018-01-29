package org.rtb.vexing.bidder.rubicon;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.rtb.vexing.adapter.rubicon.RubiconSize;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRpTrack;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoExtRP;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.HttpResponse;
import org.rtb.vexing.bidder.model.Result;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.rtb.vexing.model.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.rtb.vexing.model.openrtb.ext.response.BidType;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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
public class RubiconBidder implements Bidder {

    private static final Logger logger = LoggerFactory.getLogger(RubiconBidder.class);

    private static final String APPLICATION_JSON_UTF_8 = HttpHeaderValues.APPLICATION_JSON.toString() + ";"
            + HttpHeaderValues.CHARSET.toString() + "=" + StandardCharsets.UTF_8.toString().toLowerCase();
    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";
    private static final TypeReference<ExtPrebid<?, ExtImpRubicon>> RUBICON_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpRubicon>>() {
            };

    private final String endpointUrl;
    private final MultiMap headers;

    public RubiconBidder(String endpoint, String xapiUsername, String xapiPassword) {
        endpointUrl = validateUrl(Objects.requireNonNull(endpoint));
        headers = headers(Objects.requireNonNull(xapiUsername), Objects.requireNonNull(xapiPassword));
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

        return Result.of(httpRequests, errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(bidRequest, parseResponse(httpCall.response)), Collections.emptyList());
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(e.getMessage()));
        }
    }

    @Override
    public String name() {
        return "rubicon";
    }

    @Override
    public String cookieFamilyName() {
        return "rubicon";
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
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
                    .bidder;
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
            builder.video(makeVideo(video, rubiconImpExt.video));
        } else {
            builder.banner(makeBanner(imp.getBanner()));
        }

        return builder.build();
    }

    private static RubiconImpExt makeImpExt(ExtImpRubicon rubiconImpExt) {
        return RubiconImpExt.builder()
                .rp(RubiconImpExtRp.builder()
                        .zoneId(rubiconImpExt.zoneId)
                        .target(rubiconImpExt.inventory.isNull() ? null : rubiconImpExt.inventory)
                        .track(RubiconImpExtRpTrack.builder().mint("").mintVersion("").build())
                        .build())
                .build();
    }

    private static Video makeVideo(Video video, RubiconVideoParams rubiconVideoParams) {
        return rubiconVideoParams == null ? video : video.toBuilder().ext(Json.mapper.valueToTree(
                RubiconVideoExt.builder()
                        .skip(rubiconVideoParams.skip)
                        .skipdelay(rubiconVideoParams.skipdelay)
                        .rp(RubiconVideoExtRP.builder()
                                .sizeId(rubiconVideoParams.sizeId)
                                .build())
                        .build()))
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

        return RubiconBannerExt.builder()
                .rp(RubiconBannerExtRp.builder()
                        .sizeId(primarySizeId)
                        .altSizeIds(altSizeIds)
                        .mime("text/html")
                        .build())
                .build();
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
        return user == null || rubiconImpExt.visitor.isNull() ? user : user.toBuilder().ext(Json.mapper.valueToTree(
                RubiconUserExt.builder()
                        .rp(RubiconUserExtRp.builder()
                                .target(rubiconImpExt.visitor)
                                .build())
                        .build()))
                .build();
    }

    private static Device makeDevice(Device device) {
        return device == null ? null : device.toBuilder()
                .ext(Json.mapper.valueToTree(RubiconDeviceExt.builder()
                        .rp(RubiconDeviceExtRp.builder()
                                .pixelratio(device.getPxratio())
                                .build())
                        .build()))
                .build();
    }

    private static Site makeSite(Site site, ExtImpRubicon rubiconImpExt) {
        return site == null ? null : site.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconImpExt)))
                .build();
    }

    private static RubiconSiteExt makeSiteExt(ExtImpRubicon rubiconImpExt) {
        return RubiconSiteExt.builder()
                .rp(RubiconSiteExtRp.builder().siteId(rubiconImpExt.siteId).build())
                .build();
    }

    private static Publisher makePublisher(ExtImpRubicon rubiconImpExt) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconImpExt)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(ExtImpRubicon rubiconImpExt) {
        return RubiconPubExt.builder()
                .rp(RubiconPubExtRp.builder().accountId(rubiconImpExt.accountId).build())
                .build();
    }

    private static App makeApp(App app, ExtImpRubicon rubiconImpExt) {
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconImpExt))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconImpExt)))
                .build();
    }

    private static BidResponse parseResponse(HttpResponse httpResponse) {
        final int statusCode = httpResponse.statusCode;

        if (statusCode == 204) {
            return null;
        }

        if (statusCode != 200) {
            throw new PreBidException(
                    String.format("Unexpected status code: %d. Run with request.test = 1 for more info", statusCode));
        }

        try {
            return Json.mapper.readValue(httpResponse.body, BidResponse.class);
        } catch (IOException e) {
            logger.warn("Error occurred parsing bid response", e);
            throw new PreBidException(e.getMessage());
        }
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
                .map(bid -> BidderBid.of(bid, bidType(bid, impidToBidType)))
                .collect(Collectors.toList());
    }

    private static Map<String, BidType> impidToBidType(BidRequest bidRequest) {
        return bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, imp -> imp.getVideo() != null ? BidType.video : BidType.banner));
    }

    private static BidType bidType(Bid bid, Map<String, BidType> impidToBidType) {
        return impidToBidType.getOrDefault(bid.getImpid(), BidType.banner);
    }
}
