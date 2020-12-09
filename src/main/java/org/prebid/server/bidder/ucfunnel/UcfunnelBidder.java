package org.prebid.server.bidder.ucfunnel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ucfunnel.ExtImpUcfunnel;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ucfunnel {@link Bidder} implementation.
 */
public class UcfunnelBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpUcfunnel>> UCFUNNEL_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpUcfunnel>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public UcfunnelBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.withError(BidderError.badInput("No valid impressions in the bid request"));
        }

        String partnerId = null;
        try {
            final ExtImpUcfunnel extImpUcfunnel = parseImpExt(request.getImp().get(0));
            final String adUnitId = extImpUcfunnel.getAdunitid();
            partnerId = extImpUcfunnel.getPartnerid();
            if (StringUtils.isEmpty(partnerId) || StringUtils.isEmpty(adUnitId)) {
                errors.add(BidderError.badInput("No PartnerId or AdUnitId in the bid request"));
                return Result.withErrors(errors);
            }
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final String body = mapper.encode(request);
        final String requestUrl = String.format("%s/%s/request", endpointUrl, HttpUtil.encodeUrl(partnerId));

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(requestUrl)
                        .body(body)
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .build()),
                errors);
    }

    private ExtImpUcfunnel parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), UCFUNNEL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                final BidType bidType = getBidType(bid.getImpid(), bidRequest.getImp());
                if (bidType == BidType.banner || bidType == BidType.video) {
                    final BidderBid bidderBid = BidderBid.of(bid, bidType, bidResponse.getCur());
                    bidderBids.add(bidderBid);
                }
            }
        }
        return Result.withValues(bidderBids);
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
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
        return BidType.xNative;
    }
}
