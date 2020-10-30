package org.prebid.server.bidder.pubnative;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubnative.ExtImpPubnative;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class PubnativeBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpPubnative>> PUBNATIVE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpPubnative>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PubnativeBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        if (device == null || StringUtils.isBlank(device.getOs())) {
            return Result.emptyWithError(BidderError.badInput("Impression is missing device OS information"));
        }

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validateImp(imp);
                final ExtImpPubnative extImpPubnative = parseImpExt(imp.getExt());
                final BidRequest outgoingRequest = modifyRequest(bidRequest, imp);
                httpRequests.add(createHttpRequest(outgoingRequest, extImpPubnative));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(httpRequests, errors);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
            throw new PreBidException("Pubnative only supports banner, video or native ads.");
        }
    }

    private ExtImpPubnative parseImpExt(ObjectNode impExt) {
        try {
            return mapper.mapper().convertValue(impExt, PUBNATIVE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidRequest modifyRequest(BidRequest bidRequest, Imp imp) {
        Imp outgoingImp = imp;
        final Banner banner = imp.getBanner();
        if (banner != null) {
            final Integer bannerHeight = banner.getH();
            final Integer bannerWidth = banner.getW();
            if (bannerWidth == null || bannerWidth == 0 || bannerHeight == null || bannerHeight == 0) {
                final List<Format> bannerFormats = banner.getFormat();
                if (CollectionUtils.isEmpty(bannerFormats)) {
                    throw new PreBidException("Size information missing for banner");
                }

                final Format firstFormat = bannerFormats.get(0);
                final Banner modifiedBanner = banner.toBuilder()
                        .h(firstFormat.getH())
                        .w(firstFormat.getW())
                        .build();
                outgoingImp = imp.toBuilder().banner(modifiedBanner).build();
            }
        }

        return bidRequest.toBuilder()
                .imp(Collections.singletonList(outgoingImp))
                .build();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest outgoingRequest, ExtImpPubnative impExt) {
        final String requestUri = String.format("%s?apptoken=%s&zoneid=%s", endpointUrl, impExt.getAppAuthToken(),
                impExt.getZoneId());

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(requestUri)
                .headers(HttpUtil.headers())
                .body(mapper.encode(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final HttpResponse httpResponse = httpCall.getResponse();
        if (httpResponse.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpResponse.getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse.getSeatbid(), bidRequest.getImp(), bidResponse.getCur());
    }

    private static List<BidderBid> bidsFromResponse(List<SeatBid> seatbid, List<Imp> imps, String currency) {
        return seatbid.stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, resolveBidType(bid.getImpid(), imps), currency))
                .collect(Collectors.toList());
    }

    private static BidType resolveBidType(String impid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impid)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
