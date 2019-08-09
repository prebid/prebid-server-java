package org.prebid.server.bidder.facebook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.facebook.proto.FacebookExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.facebook.ExtImpFacebook;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Facebook {@link Bidder} implementation.
 */
public class FacebookBidder implements Bidder<BidRequest> {

    private static final Random RANDOM = new Random();

    private static final TypeReference<ExtPrebid<?, ExtImpFacebook>> FACEBOOK_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpFacebook>>() {
            };

    private static final Integer LEGACY_BANNER_WIDTH = 320;
    private static final Integer LEGACY_BANNER_HEIGHT = 50;

    private final String endpointUrl;
    private final String nonSecureEndpointUrl;
    private final ObjectNode platformJson;

    public FacebookBidder(String endpointUrl, String nonSecureEndpointUrl, String platformId) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.nonSecureEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(nonSecureEndpointUrl));
        platformJson = createPlatformJson(Objects.requireNonNull(platformId));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> processedImps = new ArrayList<>();
        final String placementId;
        String pubId = null;

        try {
            placementId = parseExtImpFacebook(bidRequest.getImp().get(0)).getPlacementId();
            pubId = extractPubId(placementId);

            for (final Imp imp : bidRequest.getImp()) {
                processedImps.add(makeImp(imp, placementId));
            }
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(processedImps)
                .site(makeSite(bidRequest, pubId))
                .app(makeApp(bidRequest, pubId))
                .build();
        final String body = Json.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl())
                        .body(body)
                        .headers(HttpUtil.headers())
                        .payload(outgoingRequest)
                        .build()),
                errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    private static ObjectNode createPlatformJson(String platformId) {
        final Integer platformIdAsInt;
        try {
            platformIdAsInt = Integer.valueOf(platformId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Platform ID is not valid number: '%s'", platformId), e);
        }
        return Json.mapper.valueToTree(FacebookExt.of(platformIdAsInt));
    }

    private ExtImpFacebook parseExtImpFacebook(Imp imp) {
        if (imp.getExt() == null) {
            throw new PreBidException("audienceNetwork parameters section is missing");
        }

        try {
            return Json.mapper.<ExtPrebid<?, ExtImpFacebook>>convertValue(imp.getExt(), FACEBOOK_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String extractPubId(String placementId) {
        if (StringUtils.isBlank(placementId)) {
            throw new PreBidException("Missing placementId param");
        }

        final String[] placementIdSplit = placementId.split("_");
        if (placementIdSplit.length != 2) {
            throw new PreBidException(String.format("Invalid placementId param '%s'", placementId));
        }
        return placementIdSplit[0];
    }

    private Imp makeImp(Imp imp, String placementId) {
        validateMediaImpMediaTypes(imp);
        return imp.toBuilder()
                .tagid(placementId)
                .banner(makeBanner(imp))
                .ext(platformJson)
                .build();
    }

    private static Site makeSite(BidRequest bidRequest, String pubId) {
        final Site site = bidRequest.getSite();
        if (site == null) {
            return null;
        }

        final Publisher publisher = site.getPublisher();
        if (publisher != null && StringUtils.isNotBlank(publisher.getId())) {
            return site;
        }

        return site.toBuilder()
                .publisher(makePublisher(publisher, pubId))
                .build();
    }

    private static App makeApp(BidRequest bidRequest, String pubId) {
        final App app = bidRequest.getApp();
        if (app == null) {
            return null;
        }

        final Publisher publisher = app.getPublisher();
        if (publisher != null && StringUtils.isNotBlank(publisher.getId())) {
            return app;
        }

        return app.toBuilder()
                .publisher(makePublisher(publisher, pubId))
                .build();
    }

    private static Publisher makePublisher(Publisher publisher, String pubId) {
        final Publisher.PublisherBuilder publisherBuilder = publisher == null
                ? Publisher.builder()
                : publisher.toBuilder();
        return publisherBuilder
                .id(pubId)
                .build();
    }

    private static void validateMediaImpMediaTypes(Imp imp) {
        if (imp.getXNative() != null || imp.getAudio() != null) {
            throw new PreBidException(
                    String.format("audienceNetwork doesn't support native or audio Imps. Ignoring Imp ID=%s",
                            imp.getId()));
        }

        final Video video = imp.getVideo();
        if (video != null && CollectionUtils.isEmpty(video.getMimes())) {
            throw new PreBidException("audienceNetwork doesn't support video type with no video data");
        }
    }

    private static Banner makeBanner(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner == null) {
            return null;
        }

        final Integer h = banner.getH();
        final Integer w = banner.getW();

        if (Objects.equals(w, LEGACY_BANNER_WIDTH) && Objects.equals(h, LEGACY_BANNER_HEIGHT)) {
            // do not send legacy 320x50 size to facebook, instead use 0x50
            return banner.toBuilder().w(0).build();
        }
        return banner;
    }

    private String endpointUrl() {
        // 50% of traffic to non-secure endpoint
        return RANDOM.nextBoolean() ? endpointUrl : nonSecureEndpointUrl;
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, null))
                .collect(Collectors.toList());
    }
}
