package org.prebid.server.bidder.beachfront;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.beachfront.model.BeachfrontBannerRequest;
import org.prebid.server.bidder.beachfront.model.BeachfrontRequests;
import org.prebid.server.bidder.beachfront.model.BeachfrontResponseSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontSize;
import org.prebid.server.bidder.beachfront.model.BeachfrontSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoDevice;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoImp;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpRequest.HttpRequestBuilder;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Beachfront {@link Bidder} implementation.
 */
public class BeachfrontBidder implements Bidder<BeachfrontRequests> {

    private static final String BEACHFRONT_NAME = "BF_PREBID_S2S";
    private static final String BEACHFRONT_VERSION = "0.3.0";
    private static final String VIDEO_ENDPOINT_SUFFIX = "&prebidserver";

    private static final TypeReference<ExtPrebid<?, ExtImpBeachfront>> BEACHFRONT_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBeachfront>>() {
            };

    private final String bannerEndpointUrl;
    private final String videoEndpointUrl;

    public BeachfrontBidder(String bannerEndpointUrl, String videoEndpointUrl) {
        this.bannerEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(bannerEndpointUrl));
        this.videoEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(videoEndpointUrl));
    }

    /**
     * Creates POST HTTP requests for both, video or banner endoint, dependent on which {@link Imp} {@link BidRequest}
     * contains, to fetch bids.
     */
    @Override
    public Result<List<HttpRequest<BeachfrontRequests>>> makeHttpRequests(BidRequest request) {
        final String endpoint = getEndpoint(request.getImp());
        Result<List<HttpRequest<BeachfrontRequests>>> result;
        try {
            result = endpoint.equals(videoEndpointUrl)
                    ? makeVideoHttpRequest(request, endpoint)
                    : makeBannerHttpRequest(request, endpoint);
        } catch (InvalidRequestException ex) {
            result = Result.of(Collections.emptyList(),
                    makeBadInputErrors(ex.getMessages()));
        }
        return result;
    }

    /**
     * Creates Http request for video endpoint
     */
    private static Result<List<HttpRequest<BeachfrontRequests>>> makeVideoHttpRequest(BidRequest bidRequest,
                                                                                      String endpoint) {
        final List<String> errors = new ArrayList<>();

        final BeachfrontVideoRequest beachfrontVideoRequest = makeVideoRequest(bidRequest, errors);

        return Result.of(Collections.singletonList(
                httpRequestBuilderWithPopulatedHeadersAndMethod(bidRequest.getDevice(), HttpMethod.POST)
                        .uri(String.format("%s%s%s", endpoint, beachfrontVideoRequest.getAppId(),
                                VIDEO_ENDPOINT_SUFFIX))
                        .body(Json.encode(beachfrontVideoRequest))
                        .payload(BeachfrontRequests.of(null, beachfrontVideoRequest))
                        .build()),
                makeBadInputErrors(errors));
    }

    /**
     * Creates http request for banner endpoint
     */
    private static Result<List<HttpRequest<BeachfrontRequests>>> makeBannerHttpRequest(BidRequest bidRequest,
                                                                                       String endpoint) {
        final List<String> errors = new ArrayList<>();
        final BeachfrontBannerRequest beachfrontBannerRequest = makeBannerRequest(bidRequest, bidRequest.getImp(),
                errors);
        return Result.of(Collections.singletonList(
                httpRequestBuilderWithPopulatedHeadersAndMethod(bidRequest.getDevice(), HttpMethod.POST)
                        .uri(endpoint)
                        .body(Json.encode(beachfrontBannerRequest))
                        .payload(BeachfrontRequests.of(beachfrontBannerRequest, null))
                        .build()),
                makeBadInputErrors(errors));
    }

    private static HttpRequestBuilder<BeachfrontRequests> httpRequestBuilderWithPopulatedHeadersAndMethod(
            Device device, HttpMethod httpMethod) {
        final MultiMap headers = BidderUtil.headers();
        if (device != null) {
            addDeviceHeaders(headers, device);
        }
        return HttpRequest.<BeachfrontRequests>builder().method(httpMethod).headers(headers);
    }

    private static void addDeviceHeaders(MultiMap headers, Device device) {
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
    }

    /**
     * Creates {@link BeachfrontVideoRequest} from {@link BidRequest}, which json representation used as http body
     * request to video endpoint.
     */
    private static BeachfrontVideoRequest makeVideoRequest(BidRequest bidRequest, List<String> errors) {
        final BeachfrontVideoRequest.BeachfrontVideoRequestBuilder requestBuilder
                = BeachfrontVideoRequest.builder().cur(Collections.singletonList("USD")).isPrebid(true);

        final List<BeachfrontVideoImp> beachfrontVideoImps = new ArrayList<>();

        final ExtImpBeachfront latestExtImpBeachfront = makeVideoImpsAndGetExtImpBeachfront(bidRequest, errors,
                beachfrontVideoImps);

        populateVideoRequestSite(bidRequest.getApp(), bidRequest.getSite(), requestBuilder);

        final Device device = bidRequest.getDevice();
        final User user = bidRequest.getUser();

        return requestBuilder
                .imp(beachfrontVideoImps)
                .device(device != null
                        ? BeachfrontVideoDevice.of(device.getUa(), device.getIp(), "1")
                        : null)
                .appId(latestExtImpBeachfront.getAppId())
                .user(user != null ? makeUserForVideoRequest(user) : null)
                .build();
    }

    /**
     * Creates {@link BeachfrontVideoImp} from {@link Imp}. If imps {@link ExtImpBeachfront} can't be deserialized from
     * json or missed, {@link BeachfrontVideoImp} considered as invalid. Returns latest {@link ExtImpBeachfront} from
     * last valid imp, if no valid imps, returns null.
     */
    private static ExtImpBeachfront makeVideoImpsAndGetExtImpBeachfront(BidRequest bidRequest, List<String> errors,
                                                                        List<BeachfrontVideoImp> beachfrontVideoImps) {
        ExtImpBeachfront latestExtImpBeachfront = null;
        for (final Imp imp : bidRequest.getImp()) {
            final Video video = imp.getVideo();
            if (video != null) {
                final BeachfrontSize beachfrontSize = BeachfrontSize.of(video.getW(), video.getH());
                final ExtImpBeachfront extImpBeachfront;
                final Integer secured = imp.getSecure() != null ? imp.getSecure() : 0;

                try {
                    extImpBeachfront = getExtImpBeachfront(imp);
                } catch (PreBidException ex) {
                    errors.add(ex.getMessage());
                    beachfrontVideoImps.add(BeachfrontVideoImp.of(beachfrontSize, null, null, null, secured));
                    continue;
                }

                latestExtImpBeachfront = ObjectUtils.firstNonNull(extImpBeachfront, latestExtImpBeachfront);

                beachfrontVideoImps.add(BeachfrontVideoImp.of(beachfrontSize,
                        extImpBeachfront.getBidfloor(),
                        beachfrontVideoImps.size(), imp.getId(), secured));
            }
        }

        if (latestExtImpBeachfront == null) {
            errors.add("No valid impressions were found");
            throw new InvalidRequestException(errors);
        }

        return latestExtImpBeachfront;
    }

    /**
     * Creates {@link BeachfrontVideoRequest} domain from {@link App} or {@link Site}.
     */
    private static void populateVideoRequestSite(App app, Site site,
                                                 BeachfrontVideoRequest.BeachfrontVideoRequestBuilder
                                                         videoRequestBuilder) {
        if (app != null) {
            final String domain = app.getDomain();
            if (StringUtils.isNotEmpty(domain)) {
                videoRequestBuilder.site(Site.builder().domain(domain).page(app.getId()).build());
            }
        } else if (site != null) {
            final String page = site.getPage();
            if (StringUtils.isNotEmpty(page)) {
                final String domain = site.getDomain();
                final String resolvedDomain = StringUtils.isEmpty(domain) ? HttpUtil.getDomainFromUrl(page) : domain;

                videoRequestBuilder.site(Site.builder().domain(resolvedDomain).page(page).build());
            }
        }
    }

    /**
     * Creates {@link BeachfrontVideoRequest} user.
     */
    private static User makeUserForVideoRequest(User user) {
        final String userId = user.getId();
        final String buyerId = user.getBuyeruid();
        return User.builder()
                // Exchange-specific ID for the user. At least one of id or
                // buyeruid is recommended.
                .id(StringUtils.isNotEmpty(userId) ? userId : null)
                // Buyer-specific ID for the user as mapped by the exchange for
                // the buyer. At least one of buyeruid or id is recommended.
                .buyeruid(StringUtils.isNotEmpty(buyerId) ? buyerId : null)
                .build();
    }

    /**
     * Creates {@link BeachfrontBannerRequest} from {@link BidRequest}, which json representation used as http body
     * request to banner endpoint.
     */
    private static BeachfrontBannerRequest makeBannerRequest(BidRequest bidRequest, List<Imp> imps,
                                                             List<String> errors) {
        final BeachfrontBannerRequest.BeachfrontBannerRequestBuilder requestBuilder =
                BeachfrontBannerRequest.builder().adapterName(BEACHFRONT_NAME).adapterVersion(BEACHFRONT_VERSION);

        final List<BeachfrontSlot> beachfrontSlots = makeBeachfrontSlots(imps, errors);

        final Device device = bidRequest.getDevice();
        if (device != null) {
            populateDeviceFields(requestBuilder, bidRequest.getDevice());
        }

        populateDomainPageFieldsForBannerRequest(bidRequest.getApp(), bidRequest.getSite(),
                requestBuilder);

        final User user = bidRequest.getUser();
        if (user != null) {
            requestBuilder.user(User.builder().id(user.getId()).buyeruid(user.getBuyeruid()).build());
        }

        final Integer secure = imps.stream()
                .map(Imp::getSecure)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(null);

        return requestBuilder
                .requestId(bidRequest.getId())
                .slots(beachfrontSlots)
                .secure(secure)
                .build();
    }

    /**
     * Creates list of {@link BeachfrontSlot}s from {@link Imp}.
     * <p>
     * If Imp has audio or native type, adds error about unsupported type.
     * <p>
     * All banner imps without extension, or with extension which can't be deserialized, considering as invalid.
     * If all imps are invalid, throws {@link InvalidRequestException}.
     */
    private static List<BeachfrontSlot> makeBeachfrontSlots(List<Imp> imps, List<String> errors) {
        final List<BeachfrontSlot> beachfrontSlots = new ArrayList<>();
        boolean hasValidImp = false;
        for (final Imp imp : imps) {
            final Banner banner = imp.getBanner();
            if (imp.getAudio() != null) {
                errors.add(String.format(
                        "Beachfront doesn't support audio Imps. Ignoring Imp ID=%s", imp.getId()));
            } else if (imp.getXNative() != null) {
                errors.add(String.format(
                        "Beachfront doesn't support native Imps. Ignoring Imp ID=%s", imp.getId()));
            } else if (banner != null) {
                final List<BeachfrontSize> sizes = makeBeachfrontSizes(banner);

                final ExtImpBeachfront extImpBeachfront;

                try {
                    extImpBeachfront = getExtImpBeachfront(imp);
                } catch (PreBidException ex) {
                    errors.add(ex.getMessage());
                    beachfrontSlots.add(BeachfrontSlot.of(null, null, imp.getBidfloor(), sizes));
                    continue;
                }
                hasValidImp = true;
                beachfrontSlots.add(BeachfrontSlot.of(imp.getId(), extImpBeachfront.getAppId(),
                        imp.getBidfloor(), sizes));
            }
        }
        if (!hasValidImp) {
            errors.add("No valid impressions were found");
            throw new InvalidRequestException(errors);
        }
        return beachfrontSlots;
    }

    /**
     * Populates {@link BeachfrontBannerRequest} domain and page fields from {@link App} or {@link Site}.
     */
    private static void populateDomainPageFieldsForBannerRequest(App app, Site site,
                                                                 BeachfrontBannerRequest.BeachfrontBannerRequestBuilder
                                                                         beachfrontRequestsBuilder) {
        if (app != null) {
            beachfrontRequestsBuilder.domain(app.getDomain());
            beachfrontRequestsBuilder.page(app.getId());
        } else {
            if (site != null) {
                beachfrontRequestsBuilder.domain(HttpUtil.getDomainFromUrl(site.getPage()));
                beachfrontRequestsBuilder.page(site.getPage());
            }
        }
    }

    /**
     * Populates {@link BeachfrontBannerRequest} with fields from {@link Device} if it is not null.
     */
    private static void populateDeviceFields(BeachfrontBannerRequest.BeachfrontBannerRequestBuilder builder,
                                             Device device) {
        builder.ip(device.getIp());
        builder.deviceModel(device.getModel());
        builder.deviceOs(device.getOs());
        builder.dnt(device.getDnt());
        if (StringUtils.isNotEmpty(device.getUa())) {
            builder.ua(device.getUa());
        }
    }

    /**
     * Creates {@link BeachfrontSize} by copying from {@link Format}s.
     */
    private static List<BeachfrontSize> makeBeachfrontSizes(Banner banner) {
        final List<BeachfrontSize> beachfrontSizes = new ArrayList<>();
        if (banner.getFormat() != null) {
            for (final Format format : banner.getFormat()) {
                beachfrontSizes.add(BeachfrontSize.of(format.getW(), format.getH()));
            }
        } else {
            beachfrontSizes.add(BeachfrontSize.of(null, null));
        }
        return beachfrontSizes;
    }

    /**
     * Extracts {@link ExtImpBeachfront} from imp.ext.bidder.
     */
    private static ExtImpBeachfront getExtImpBeachfront(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        if (imp.getExt() == null) {
            throw new PreBidException("Beachfront parameters section is missing");
        }
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpBeachfront>>convertValue(impExt,
                    BEACHFRONT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, error while decoding impExt, err: %s", imp.getId(), e.getMessage()));
        }
    }

    /**
     * Determines to which endpoint send request depends on {@link Imp}s media types.
     */
    private String getEndpoint(List<Imp> imps) {
        return imps.stream().anyMatch(imp -> imp.getVideo() != null) ? videoEndpointUrl : bannerEndpointUrl;
    }

    private static List<BidderError> makeBadInputErrors(List<String> errors) {
        return errors.stream().map(BidderError::badInput).collect(Collectors.toList());
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List}, depends on response type.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BeachfrontRequests> httpCall, BidRequest bidRequest) {
        final String bodyString = httpCall.getResponse().getBody();
        if (StringUtils.isBlank(bodyString)) {
            return Result.emptyWithError(BidderError.badServerResponse("Received a null response from beachfront"));
        }

        // Beachfront sending an empty array and 200 as their "no results" response.
        if (bodyString.equals("[]")) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final String id = bidRequest.getId();
        return getBidType(bidRequest.getImp()) == BidType.video
                ? processVideoResponse(httpCall, id)
                : processBannerResponse(httpCall, id);
    }

    /**
     * Creates response for video response, by updating response {@link Bid} by data from request's
     * {@link BeachfrontVideoImp}
     */
    private Result<List<BidderBid>> processVideoResponse(HttpCall<BeachfrontRequests> httpCall, String id) {
        final List<BidderError> errors = new ArrayList<>();
        final BidResponse bidResponse;
        try {
            bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException ex) {
            errors.add(BidderError.badServerResponse(ex.getMessage()));
            return makeErrorResponse(errors);
        }

        if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final BeachfrontVideoImp videoImp = httpCall.getRequest().getPayload().getVideoRequest().getImp().get(0);

        return Result.of(bidResponse.getSeatbid().get(0).getBid().stream()
                .map(bid -> BidderBid.of(updateVideoBid(bid, videoImp, id), BidType.video, "USD"))
                .collect(Collectors.toList()), errors);
    }

    /**
     * Updates {@link Bid} with data from request's {@link BeachfrontVideoImp}.
     */
    private static Bid updateVideoBid(Bid bid, BeachfrontVideoImp imp, String id) {
        final String bidNurl = bid.getNurl();
        bid.setCrid(bidNurl != null ? extractCrid(bid.getNurl(), 2) : null);
        bid.setImpid(imp.getImpid());
        bid.setH(imp.getVideo().getH());
        bid.setW(imp.getVideo().getW());
        bid.setId(id);
        return bid;
    }

    /**
     * Creates response for banner response, by creating response {@link Bid} from {@link BeachfrontResponseSlot}.
     */
    private Result<List<BidderBid>> processBannerResponse(HttpCall<BeachfrontRequests> httpCall, String id) {
        final List<BidderError> errors = new ArrayList<>();
        final String responseBody = httpCall.getResponse().getBody();
        final List<BeachfrontResponseSlot> responseSlots;
        try {
            responseSlots = makeBeachfrontResponseSlots(responseBody);
        } catch (PreBidException ex) {
            errors.add(BidderError.badServerResponse(ex.getMessage()));
            return makeErrorResponse(errors);
        }

        return Result.of(responseSlots.stream()
                .filter(Objects::nonNull)
                .map(beachfrontResponseSlot -> makeBidFromBeachfrontSlot(beachfrontResponseSlot, id))
                .map(bid -> BidderBid.of(bid, BidType.banner, "USD"))
                .collect(Collectors.toList()), errors);
    }

    /**
     * Parses response body to list of {@link BeachfrontResponseSlot}s.
     * <p>
     * Throws {@link PreBidException} in case of failure.
     */
    private static List<BeachfrontResponseSlot> makeBeachfrontResponseSlots(String responseBody) {
        try {
            return Json.mapper.readValue(responseBody, Json.mapper.getTypeFactory()
                    .constructCollectionType(List.class, BeachfrontResponseSlot.class));
        } catch (IOException ex) {
            throw new PreBidException(ex.getMessage());
        }
    }

    /**
     * Creates {@link Bid} from {@link BeachfrontResponseSlot}.
     */
    private static Bid makeBidFromBeachfrontSlot(BeachfrontResponseSlot beachfrontResponseSlot, String id) {
        final String adm = beachfrontResponseSlot.getAdm();
        final Float price = beachfrontResponseSlot.getPrice();

        return Bid.builder()
                .crid(adm != null ? extractCrid(adm, 1) : null)
                .impid(beachfrontResponseSlot.getSlot())
                .price(price != null ? BigDecimal.valueOf(price) : null)
                .id(id)
                .adm(adm)
                .h(beachfrontResponseSlot.getH())
                .w(beachfrontResponseSlot.getW())
                .build();
    }

    /**
     * Extracts crid from " separated source by position. In case when crid was not found return null.
     */
    private static String extractCrid(String source, int position) {
        String[] crids = source.split("\"");
        return position < crids.length ? crids[position] : null;
    }

    /**
     * Creates {@link Result} with empty list of {@link BidderBid}s and bad server errors.
     */
    private static Result<List<BidderBid>> makeErrorResponse(List<BidderError> errors) {
        errors.add(BidderError.badServerResponse("Failed to process the beachfront response"));
        return Result.of(Collections.emptyList(), errors);
    }

    /**
     * Finds {@link BidType} depends on {@link BidRequest} {@link Imp}s.
     */
    private static BidType getBidType(List<Imp> imps) {
        return imps.stream().anyMatch(imp -> imp.getVideo() != null) ? BidType.video : BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
