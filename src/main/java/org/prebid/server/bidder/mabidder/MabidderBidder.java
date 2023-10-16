package org.prebid.server.bidder.mabidder;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.mabidder.response.MabidderBidResponse;
import org.prebid.server.bidder.mabidder.response.MabidderResponse;
import org.prebid.server.bidder.mabidder.response.Meta;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Objects;

public class MabidderBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MabidderBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        return Result.withValue(BidderUtil.defaultRequest(request, endpointUrl, mapper));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<MabidderBidResponse> bidResponses = mapper.decodeValue(
                            httpCall.getResponse().getBody(),
                            MabidderResponse.class)
                    .getBidResponses();
            return Result.withValues(extractBids(bidResponses));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(List<MabidderBidResponse> bidResponses) {
        return bidResponses
                .stream()
                .map(bidResponse -> BidderBid.of(makeBid(bidResponse), BidType.banner, bidResponse.getCurrency()))
                .toList();
    }

    private static Bid makeBid(MabidderBidResponse bidResponse) {
        return Bid.builder()
                .id(bidResponse.getRequestId())
                .impid(bidResponse.getRequestId())
                .price(bidResponse.getCpm())
                .adm(bidResponse.getAd())
                .w(bidResponse.getWidth())
                .h(bidResponse.getHeight())
                .crid(bidResponse.getCreativeId())
                .dealid(bidResponse.getDealId())
                .adomain(ObjectUtil.getIfNotNull(bidResponse.getMeta(), Meta::getAdDomains))
                .build();
    }

}
