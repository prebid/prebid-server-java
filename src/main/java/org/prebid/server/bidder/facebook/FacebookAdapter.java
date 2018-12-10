package org.prebid.server.bidder.facebook;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Adapter;
import org.prebid.server.bidder.OpenrtbAdapter;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.facebook.model.NormalizedFacebookParams;
import org.prebid.server.bidder.facebook.proto.FacebookExt;
import org.prebid.server.bidder.facebook.proto.FacebookParams;
import org.prebid.server.bidder.model.AdUnitBidWithParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facebook {@link Adapter} implementation.
 */
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
    private final ObjectNode platformJson;

    public FacebookAdapter(Usersyncer usersyncer, String endpointUrl, String nonSecureEndpointUrl, String platformId) {
        super(usersyncer);
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.nonSecureEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(nonSecureEndpointUrl));
        platformJson = createPlatformJson(Objects.requireNonNull(platformId));
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

    @Override
    public List<AdapterHttpRequest<BidRequest>> makeHttpRequests(AdapterRequest adapterRequest,
                                                                 PreBidRequestContext preBidRequestContext) {
        final List<AdUnitBid> adUnitBids = adapterRequest.getAdUnitBids();

        validateAdUnitBidsMediaTypes(adUnitBids, ALLOWED_MEDIA_TYPES);
        validateAdUnitBidsBannerMediaType(adUnitBids);

        final List<BidRequest> requests = createAdUnitBidsWithParams(adUnitBids).stream()
                .filter(FacebookAdapter::containsAnyAllowedMediaType)
                .map(adUnitBidWithParams -> createBidRequest(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
        if (requests.isEmpty()) {
            throw new PreBidException("Invalid ad unit/imp");
        }

        return requests.stream()
                .map(bidRequest -> AdapterHttpRequest.of(HttpMethod.POST, endpointUrl(), bidRequest, headers()))
                .collect(Collectors.toList());
    }

    private static void validateAdUnitBidsBannerMediaType(List<AdUnitBid> adUnitBids) {
        adUnitBids.stream()
                .filter(adUnitBid -> adUnitBid.getMediaTypes().contains(MediaType.banner))
                .forEach(FacebookAdapter::validateBannerMediaType);
    }

    private static void validateBannerMediaType(AdUnitBid adUnitBid) {
        // if instl = 0 and type is banner, do not send non supported size
        if (Objects.equals(adUnitBid.getInstl(), 0)
                && !ALLOWED_BANNER_HEIGHTS.contains(adUnitBid.getSizes().get(0).getH())) {
            throw new PreBidException("Facebook do not support banner height other than 50, 90 and 250");
        }
    }

    private static List<AdUnitBidWithParams<NormalizedFacebookParams>> createAdUnitBidsWithParams(
            List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static NormalizedFacebookParams parseAndValidateParams(AdUnitBid adUnitBid) {
        final ObjectNode paramsNode = adUnitBid.getParams();
        if (paramsNode == null) {
            throw new PreBidException("Facebook params section is missing");
        }

        final FacebookParams params;
        try {
            params = Json.mapper.convertValue(paramsNode, FacebookParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        final String placementId = params.getPlacementId();
        if (StringUtils.isEmpty(placementId)) {
            throw new PreBidException("Missing placementId param");
        }

        final String[] placementIdSplit = placementId.split("_");
        if (placementIdSplit.length != 2) {
            throw new PreBidException(String.format("Invalid placementId param '%s'", placementId));
        }

        return NormalizedFacebookParams.of(placementId, placementIdSplit[0]);
    }

    private static boolean containsAnyAllowedMediaType(
            AdUnitBidWithParams<NormalizedFacebookParams> adUnitBidWithParams) {
        return CollectionUtils.containsAny(adUnitBidWithParams.getAdUnitBid().getMediaTypes(), ALLOWED_MEDIA_TYPES);
    }

    private BidRequest createBidRequest(AdUnitBidWithParams<NormalizedFacebookParams> adUnitBidWithParams,
                                        PreBidRequestContext preBidRequestContext) {
        final Imp imp = makeImp(adUnitBidWithParams, preBidRequestContext);

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return BidRequest.builder()
                .id(preBidRequest.getTid())
                .at(1)
                .tmax(preBidRequest.getTimeoutMillis())
                .imp(Collections.singletonList(imp))
                .app(makeApp(preBidRequestContext, adUnitBidWithParams.getParams()))
                .site(makeSite(preBidRequestContext, adUnitBidWithParams.getParams()))
                .device(deviceBuilder(preBidRequestContext).build())
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .regs(preBidRequest.getRegs())
                .ext(platformJson)
                .build();
    }

    private static Imp makeImp(AdUnitBidWithParams<NormalizedFacebookParams> adUnitBidWithParams,
                               PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.getAdUnitBid();

        final Imp.ImpBuilder impBuilder = Imp.builder()
                .id(adUnitBid.getAdUnitCode())
                .instl(adUnitBid.getInstl())
                .secure(preBidRequestContext.getSecure())
                .tagid(adUnitBidWithParams.getParams().getPlacementId());

        final Set<MediaType> mediaTypes = allowedMediaTypes(adUnitBid, ALLOWED_MEDIA_TYPES);
        if (mediaTypes.contains(MediaType.video)) {
            impBuilder.video(videoBuilder(adUnitBid).build());
        }
        if (mediaTypes.contains(MediaType.banner)) {
            impBuilder.banner(makeBanner(adUnitBid));
        }

        return impBuilder.build();
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        final Format format = adUnitBid.getSizes().get(0);
        final Integer w = format.getW();
        final Integer h = format.getH();

        final Integer width;
        final Integer height;
        if (Objects.equals(adUnitBid.getInstl(), 1)) {
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

    private static App makeApp(PreBidRequestContext preBidRequestContext, NormalizedFacebookParams params) {
        final App app = preBidRequestContext.getPreBidRequest().getApp();
        return app == null ? null : app.toBuilder()
                .publisher(Publisher.builder().id(params.getPubId()).build())
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, NormalizedFacebookParams params) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder
                .publisher(Publisher.builder().id(params.getPubId()).build())
                .build();
    }

    private String endpointUrl() {
        // 50% of traffic to non-secure endpoint
        return RANDOM.nextBoolean() ? endpointUrl : nonSecureEndpointUrl;
    }

    @Override
    public List<Bid.BidBuilder> extractBids(AdapterRequest adapterRequest,
                                            ExchangeCall<BidRequest, BidResponse> exchangeCall) {
        return responseBidStream(exchangeCall.getResponse())
                .map(bid -> toBidBuilder(bid, adapterRequest))
                .limit(1) // one bid per request/response
                .collect(Collectors.toList());
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdapterRequest adapterRequest) {
        final AdUnitBid adUnitBid = lookupBid(adapterRequest.getAdUnitBids(), bid.getImpid());
        final Format format = adUnitBid.getSizes().get(0);
        return Bid.builder()
                .bidder(adUnitBid.getBidderCode())
                .bidId(adUnitBid.getBidId())
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .width(format.getW())
                .height(format.getH())
                // sets creative type, since FB doesn't return any from server. Only banner type is supported by FB.
                .mediaType(MediaType.banner);
    }

}
