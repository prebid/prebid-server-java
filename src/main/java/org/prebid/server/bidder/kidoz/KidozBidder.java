package org.prebid.server.bidder.kidoz;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.kidoz.ExtImpKidoz;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class KidozBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpKidoz>> KIDOZ_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpKidoz>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public KidozBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> result = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final Imp validImp = validateImp(imp);
                final ExtImpKidoz extImpKidoz = parseAndValidateImpExt(imp);
                result.add(createSingleRequest(validImp, request, endpointUrl));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(result, errors);
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request, String url) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        final MultiMap headers = HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .headers(headers)
                .body(mapper.encodeToBytes(outgoingRequest))
                .payload(outgoingRequest)
                .build();
    }

    private Imp validateImp(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("Kidoz only supports banner or video ads");
        }

        final Banner banner = imp.getBanner();
        if (banner != null) {
            if (CollectionUtils.isEmpty(banner.getFormat())) {
                throw new PreBidException("banner format required");
            }
        }

        return imp;
    }

    private ExtImpKidoz parseAndValidateImpExt(Imp imp) {
        final ExtImpKidoz extImpKidoz;
        try {
            extImpKidoz = mapper.mapper().convertValue(imp.getExt(), KIDOZ_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        if (extImpKidoz == null) {
            throw new PreBidException("impression extensions required");
        }

        if (StringUtils.isBlank(extImpKidoz.getAccessToken())) {
            throw new PreBidException("Kidoz access_token required");
        }

        if (StringUtils.isBlank(extImpKidoz.getPublisherID())) {
            throw new PreBidException("Kidoz publisher_id required");
        }

        return extImpKidoz;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(httpCall.getRequest().getPayload(), bidResponse);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static Result<List<BidderBid>> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidFromResponse(bidRequest.getImp(), bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return Result.of(bidderBids, errors);
    }

    private static BidderBid bidFromResponse(List<Imp> imps, Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid.getImpid(), imps);
            return BidderBid.of(bid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }
}
