package org.prebid.server.bidder.somoaudience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.somoaudience.proto.SomoaudienceReqExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.somoaudience.ExtImpSomoaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SomoaudienceBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSomoaudience>> SOMOAUDIENCE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSomoaudience>>() {
            };

    private static final String CONFIG = "hb_pbs_1.0.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private final ObjectNode requestExtension;

    public SomoaudienceBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);

        this.requestExtension = mapper.mapper().valueToTree(SomoaudienceReqExt.of(CONFIG));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> bannerImps = new ArrayList<>();
        final List<Imp> videoAndNativeImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            if (imp.getBanner() != null) {
                bannerImps.add(imp);
            } else if (imp.getVideo() != null || imp.getXNative() != null) {
                videoAndNativeImps.add(imp);
            } else {
                errors.add(BidderError.badInput(String.format(
                        "SomoAudience only supports banner and video imps. Ignoring imp id=%s", imp.getId())));
            }
        }
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        final HttpRequest<BidRequest> bannerRequests = makeRequest(bidRequest, bannerImps, errors);

        if (bannerRequests != null) {
            httpRequests.add(bannerRequests);
        }

        for (Imp imp : videoAndNativeImps) {
            final HttpRequest<BidRequest> videoOrNativeRequest = makeRequest(
                    bidRequest, Collections.singletonList(imp), errors);
            if (videoOrNativeRequest != null) {
                httpRequests.add(videoOrNativeRequest);
            }
        }

        return Result.of(httpRequests, errors);
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, List<Imp> imps, List<BidderError> errors) {
        final List<Imp> validImps = new ArrayList<>();
        String placementHash = null;
        for (Imp imp : imps) {
            try {
                final ExtImpSomoaudience extImpSomoaudience = parseAndValidateImpExt(imp);
                placementHash = extImpSomoaudience.getPlacementHash();
                final BigDecimal bidFloor = extImpSomoaudience.getBidFloor();
                final Imp modifiedImp = imp.toBuilder()
                        .ext(null)
                        .bidfloor(bidFloor)
                        .build();
                validImps.add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (CollectionUtils.isEmpty(validImps)) {
            return null;
        }
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();

        requestBuilder.imp(validImps);
        requestBuilder.ext(requestExtension);

        final BidRequest outgoingRequest = requestBuilder.build();
        final String body = mapper.encode(outgoingRequest);

        final MultiMap headers = basicHeaders();
        final Device requestDevice = outgoingRequest.getDevice();
        if (requestDevice != null) {
            addDeviceHeaders(headers, requestDevice);
        }
        final String url = String.format("%s?s=%s", endpointUrl, placementHash);

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .body(body)
                .headers(headers)
                .payload(outgoingRequest)
                .build();
    }

    private ExtImpSomoaudience parseAndValidateImpExt(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        if (impExt == null || impExt.size() == 0) {
            throw new PreBidException(String.format("ignoring imp id=%s, extImpBidder is empty", imp.getId()));
        }

        final ExtImpSomoaudience extImpSomoaudience;
        try {
            extImpSomoaudience = mapper.mapper().convertValue(imp.getExt(),
                    SOMOAUDIENCE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, error while decoding extImpBidder, err: %s", imp.getId(), e.getMessage()));
        }
        return extImpSomoaudience;
    }

    private static MultiMap basicHeaders() {
        return HttpUtil.headers()
                .add("x-openrtb-version", "2.5");
    }

    private static void addDeviceHeaders(MultiMap headers, Device device) {
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());

        final Integer dnt = device.getDnt();
        headers.add("DNT", dnt != null ? dnt.toString() : "0");
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List} of errors.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, bidRequest.getImp());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private static Result<List<BidderBid>> extractBids(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Result.of(Collections.emptyList(), Collections.emptyList())
                : Result.of(createBiddersBid(bidResponse, imps), Collections.emptyList());
    }

    /**
     * Extracts {@link Bid}s from response.
     */
    private static List<BidderBid> createBiddersBid(BidResponse bidResponse, List<Imp> imps) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidderType(imps, bid.getImpid()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    /**
     * Defines {@link BidType} depends on {@link Imp} with the same impId
     */
    private static BidType getBidderType(List<Imp> imps, String impId) {
        return imps.stream()
                .filter(imp -> Objects.equals(imp.getId(), impId))
                .findAny()
                .map(SomoaudienceBidder::bidTypeFromImp)
                .orElse(BidType.banner);
    }

    /**
     * Returns {@link BidType} depends on {@link Imp}s banner, video or native types.
     */
    private static BidType bidTypeFromImp(Imp imp) {
        BidType bidType = BidType.banner;
        if (imp.getBanner() == null) {
            if (imp.getVideo() != null) {
                bidType = BidType.video;
            } else if (imp.getXNative() != null) {
                bidType = BidType.xNative;
            }
        }
        return bidType;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
