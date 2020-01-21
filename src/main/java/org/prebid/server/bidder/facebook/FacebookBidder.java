package org.prebid.server.bidder.facebook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.facebook.proto.FacebookAdMarkup;
import org.prebid.server.bidder.facebook.proto.FacebookExt;
import org.prebid.server.bidder.facebook.proto.FacebookNative;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.facebook.ExtImpFacebook;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Facebook {@link Bidder} implementation.
 */
public class FacebookBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpFacebook>> FACEBOOK_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpFacebook>>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;
    private final String platformId;
    private final String appSecret;

    public FacebookBidder(String endpointUrl, String platformId, String appSecret) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.platformId = checkBlankString(Objects.requireNonNull(platformId), "platform-id");
        this.appSecret = checkBlankString(Objects.requireNonNull(appSecret), "app-secret");
    }

    private static String checkBlankString(String paramValue, String paramName) {
        if (StringUtils.isBlank(paramValue)) {
            throw new IllegalArgumentException(String.format("No facebook %s specified. Calls to the Audience "
                    + "Network will fail. Did you set adapters.facebook.%s in the app config?", paramName, paramName));
        }
        return paramValue;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final User user = bidRequest.getUser();
        if (user == null || StringUtils.isBlank(user.getBuyeruid())) {
            return Result.emptyWithError(BidderError.badInput("Missing bidder token in 'user.buyeruid'"));
        }

        final MultiMap headers = HttpUtil.headers()
                .add("X-Fb-Pool-Routing-Token", bidRequest.getUser().getBuyeruid());

        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                httpRequests.add(makeHttpRequest(imp, bidRequest, headers));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private HttpRequest<BidRequest> makeHttpRequest(Imp imp, BidRequest bidRequest, MultiMap headers) {
        final ExtImpFacebook resolvedImpExt = parseAndResolveExtImpFacebook(imp);
        final Imp modifiedImp = modifyImp(imp, resolvedImpExt);

        final String publisherId = resolvedImpExt.getPublisherId();
        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(modifiedImp))
                .site(makeSite(bidRequest.getSite(), publisherId))
                .app(makeApp(bidRequest.getApp(), publisherId))
                .ext(Json.mapper.valueToTree(FacebookExt.of(platformId, makeAuthId(bidRequest.getId()))))
                .build();

        final String body = Json.encode(outgoingRequest);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(body)
                .headers(headers)
                .payload(outgoingRequest)
                .build();
    }

    private static ExtImpFacebook parseAndResolveExtImpFacebook(Imp imp) {
        final ExtImpFacebook extImpFacebook;
        try {
            extImpFacebook = Json.mapper.convertValue(imp.getExt(), FACEBOOK_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final String placementId = extImpFacebook.getPlacementId();
        if (StringUtils.isBlank(placementId)) {
            throw new PreBidException("Missing placementId param");
        }

        final String[] placementSplit = placementId.split("_");
        final int splitLength = placementSplit.length;
        if (splitLength == 1) {
            if (StringUtils.isBlank(extImpFacebook.getPublisherId())) {
                throw new PreBidException("Missing publisherId param");
            }

            return extImpFacebook;

        } else if (splitLength == 2) {
            return ExtImpFacebook.of(placementSplit[1], placementSplit[0]);
        } else {
            throw new PreBidException(String.format("Invalid placementId param '%s' and publisherId param '%s'",
                    placementId, extImpFacebook.getPublisherId()));
        }
    }

    private static Imp modifyImp(Imp imp, ExtImpFacebook extImpFacebook) {
        final BidType impType = resolveImpType(imp);
        if (impType == null) {
            throw new PreBidException(String.format("imp #%s with invalid type", imp.getId()));
        }

        final boolean impInstlEqOne = Objects.equals(imp.getInstl(), 1);
        if (impInstlEqOne && impType != BidType.banner) {
            throw new PreBidException(String.format("imp #%s: interstitial imps are only supported for banner",
                    imp.getId()));
        }

        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        switch (impType) {
            case banner:
                impBuilder.banner(modifyBanner(imp, impInstlEqOne));
                break;
            case video:
                impBuilder.video(imp.getVideo().toBuilder().w(0).h(0).build());
                break;
            case xNative:
                impBuilder.xNative(modifyNative(imp.getXNative()));
                break;
            default:
                // Do nothing for Audio
                break;
        }
        return impBuilder
                .ext(null)
                .tagid(extImpFacebook.getPublisherId() + "_" + extImpFacebook.getPlacementId())
                .build();
    }

    private static BidType resolveImpType(Imp imp) {
        if (imp.getBanner() != null) {
            return BidType.banner;
        }
        if (imp.getVideo() != null) {
            return BidType.video;
        }
        if (imp.getAudio() != null) {
            return BidType.audio;
        }
        if (imp.getXNative() != null) {
            return BidType.xNative;
        }
        return null;
    }

    private static Banner modifyBanner(Imp imp, boolean impInstlEqOne) {
        final Banner banner = imp.getBanner();
        if (impInstlEqOne) {
            return banner.toBuilder().w(0).h(0).format(null).build();
        }

        if (!Objects.equals(banner.getH(), 50) && !Objects.equals(banner.getH(), 250)) {
            throw new PreBidException(String.format("imp #%s: only banner heights 50 and 250 are supported",
                    imp.getId()));
        }

        return banner.toBuilder().w(-1).format(null).build();
    }

    /**
     * Add Width and Height (not available in standard openRTB) and exclude native.request and native.ver fields
     */
    private static Native modifyNative(Native xNative) {
        return FacebookNative.builder()
                .w(-1)
                .h(-1)
                .api(xNative.getApi())
                .battr(xNative.getBattr())
                .ext(xNative.getExt())
                .build();
    }

    private static Site makeSite(Site site, String pubId) {
        if (site == null) {
            return null;
        }
        return site.toBuilder()
                .publisher(Publisher.builder().id(pubId).build())
                .build();
    }

    private static App makeApp(App app, String pubId) {
        if (app == null) {
            return null;
        }
        return app.toBuilder()
                .publisher(Publisher.builder().id(pubId).build())
                .build();
    }

    private String makeAuthId(String requestId) {
        final Mac mac = HmacUtils.getInitializedMac(HmacAlgorithms.HMAC_SHA_256, appSecret.getBytes());
        return Hex.encodeHexString(mac.doFinal(requestId != null ? requestId.getBytes() : null));
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final HttpResponse response = httpCall.getResponse();
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            final String message = response.getHeaders().get("x-fb-an-errors");
            return Result.emptyWithError(BidderError.badInput(
                    String.format("Unexpected status code %d with error message '%s'", statusCode, message)));
        }

        try {
            final BidResponse bidResponse = Json.decodeValue(response.getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse, List<Imp> imps) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> toBidderBid(bid, imps, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.of(bidderBids, errors);
    }

    private static BidderBid toBidderBid(Bid bid, List<Imp> imps, List<BidderError> errors) {
        final String bidId;
        try {
            if (StringUtils.isBlank(bid.getAdm())) {
                throw new PreBidException(String.format("Bid %s missing 'adm'", bid.getId()));
            }

            bidId = Json.decodeValue(bid.getAdm(), FacebookAdMarkup.class).getBidId();

            if (StringUtils.isBlank(bidId)) {
                throw new PreBidException(String.format("bid %s missing 'bid_id' in 'adm'", bid.getId()));
            }

            bid.setAdid(bidId);
            bid.setCrid(bidId);

            return BidderBid.of(bid, resolveBidType(bid.getImpid(), imps), DEFAULT_BID_CURRENCY);

        } catch (DecodeException | PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (impId.equals(imp.getId())) {
                final BidType bidType = resolveImpType(imp);
                if (bidType == null) {
                    throw new PreBidException("Processing an invalid impression; cannot resolve impression type");
                }
                return bidType;
            }
        }
        throw new PreBidException(String.format("Invalid bid imp ID %s does not match any imp IDs from the original "
                + "bid request", impId));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
