package org.rtb.vexing.adapter.facebook;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.json.Json;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.OpenrtbAdapter;
import org.rtb.vexing.adapter.facebook.model.FacebookExt;
import org.rtb.vexing.adapter.facebook.model.FacebookParams;
import org.rtb.vexing.adapter.model.ExchangeCall;
import org.rtb.vexing.adapter.model.HttpRequest;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FacebookAdapter extends OpenrtbAdapter {

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private static final Set<Integer> ALLOWED_BANNER_HEIGHTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(50, 90, 250)));

    private static final Integer LEGACY_BANNER_WIDTH = 320;
    private static final Integer LEGACY_BANNER_HEIGHT = 50;

    private static final Random RANDOM = new Random();

    private final String endpointUrl;
    private final String nonSecureEndpointUrl;
    private final UsersyncInfo usersyncInfo;
    private final ObjectNode platformJson;

    public FacebookAdapter(String endpointUrl, String nonSecureEndpointUrl, String usersyncUrl, String platformId) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));
        this.nonSecureEndpointUrl = validateUrl(Objects.requireNonNull(nonSecureEndpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
        platformJson = createPlatformJson(Objects.requireNonNull(platformId));
    }

    private static UsersyncInfo createUsersyncInfo(String usersyncUrl) {
        return UsersyncInfo.builder()
                .url(usersyncUrl)
                .type("redirect")
                .supportCORS(false)
                .build();
    }

    private static ObjectNode createPlatformJson(String platformId) {
        final Integer platformIdAsInt;
        try {
            platformIdAsInt = Integer.valueOf(platformId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Platform ID is not valid number: '%s'", platformId), e);
        }
        return Json.mapper.valueToTree(FacebookExt.builder().platformid(platformIdAsInt).build());
    }

    @Override
    public String code() {
        return "audienceNetwork";
    }

    @Override
    public String cookieFamily() {
        return "audienceNetwork";
    }

    @Override
    public UsersyncInfo usersyncInfo() {
        return usersyncInfo;
    }

    @Override
    public List<HttpRequest> makeHttpRequests(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);
        validateAdUnitBidsBannerMediaType(bidder.adUnitBids);

        return createAdUnitBidsWithParams(bidder.adUnitBids).stream()
                .flatMap(adUnitBidWithParams -> createBidRequests(adUnitBidWithParams, preBidRequestContext))
                .map(bidRequest -> HttpRequest.of(endpointUrl(), headers(), bidRequest))
                .collect(Collectors.toList());
    }

    private static void validateAdUnitBidsBannerMediaType(List<AdUnitBid> adUnitBids) {
        adUnitBids.stream()
                .filter(adUnitBid -> adUnitBid.mediaTypes.contains(MediaType.banner))
                .forEach(FacebookAdapter::validateBannerMediaType);
    }

    private static void validateBannerMediaType(AdUnitBid adUnitBid) {
        // if instl = 0 and type is banner, do not send non supported size
        if (Objects.equals(adUnitBid.instl, 0)
                && !ALLOWED_BANNER_HEIGHTS.contains(adUnitBid.sizes.get(0).getH())) {
            throw new PreBidException("Facebook do not support banner height other than 50, 90 and 250");
        }
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static Params parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Facebook params section is missing");
        }

        final FacebookParams params;
        try {
            params = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, FacebookParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (StringUtils.isEmpty(params.placementId)) {
            throw new PreBidException("Missing placementId param");
        }

        final String[] splitted = params.placementId.split("_");
        if (splitted.length != 2) {
            throw new PreBidException(String.format("Invalid placementId param '%s'", params.placementId));
        }

        return Params.of(params.placementId, splitted[0]);
    }

    private Stream<BidRequest> createBidRequests(AdUnitBidWithParams adUnitBidWithParams,
                                                 PreBidRequestContext preBidRequestContext) {
        final List<Imp> imps = makeImps(adUnitBidWithParams, preBidRequestContext);
        validateImps(imps);

        return imps.stream()
                .map(imp -> BidRequest.builder()
                        .id(preBidRequestContext.preBidRequest.tid)
                        .at(1)
                        .tmax(preBidRequestContext.preBidRequest.timeoutMillis)
                        .imp(Collections.singletonList(imp))
                        .app(makeApp(preBidRequestContext, adUnitBidWithParams.params))
                        .site(makeSite(preBidRequestContext, adUnitBidWithParams.params))
                        .device(deviceBuilder(preBidRequestContext).build())
                        .user(makeUser(preBidRequestContext))
                        .source(makeSource(preBidRequestContext))
                        .ext(platformJson)
                        .build());
    }

    private static List<Imp> makeImps(AdUnitBidWithParams adUnitBidWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;

        return allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(adUnitBidWithParams.params.placementId)
                        .build())
                .collect(Collectors.toList());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(videoBuilder(adUnitBid).build());
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        final Integer w = adUnitBid.sizes.get(0).getW();
        final Integer h = adUnitBid.sizes.get(0).getH();

        final Integer width;
        final Integer height;
        if (Objects.equals(adUnitBid.instl, 1)) {
            // if instl = 1 sent in, pass size (0,0) to facebook
            width = 0;
            height = 0;
        } else if (Objects.equals(w, LEGACY_BANNER_WIDTH) && Objects.equals(h, LEGACY_BANNER_HEIGHT)) {
            // do not send legacy 320x50 size to facebook, instead use 0x50
            width = 0;
            height = h;
        } else {
            width = w;
            height = h;
        }

        return bannerBuilder(adUnitBid)
                .w(width)
                .h(height)
                .build();
    }

    private static App makeApp(PreBidRequestContext preBidRequestContext, Params params) {
        final App app = preBidRequestContext.preBidRequest.app;
        return app == null ? null : app.toBuilder()
                .publisher(Publisher.builder().id(params.pubId).build())
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, Params params) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder
                .publisher(Publisher.builder().id(params.pubId).build())
                .build();
    }

    private String endpointUrl() {
        // 50% of traffic to non-secure endpoint
        return RANDOM.nextBoolean() ? endpointUrl : nonSecureEndpointUrl;
    }

    @Override
    public List<Bid.BidBuilder> extractBids(Bidder bidder, ExchangeCall exchangeCall) {
        return responseBidStream(exchangeCall.bidResponse)
                .map(bid -> toBidBuilder(bid, bidder))
                .limit(1) // one bid per request/response
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, Bidder bidder) {
        final AdUnitBid adUnitBid = lookupBid(bidder.adUnitBids, bid.getImpid());
        return Bid.builder()
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId)
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .width(adUnitBid.sizes.get(0).getW())
                .height(adUnitBid.sizes.get(0).getH())
                // sets creative type, since FB doesn't return any from server. Only banner type is supported by FB.
                .mediaType(MediaType.banner);
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class Params {

        String placementId;

        String pubId;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        Params params;
    }
}
