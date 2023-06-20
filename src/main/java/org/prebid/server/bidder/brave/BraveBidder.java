package org.prebid.server.bidder.brave;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.brave.ExtImpBrave;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class BraveBidder implements Bidder<BidRequest> {

    private static final String PUBLISHER_MACRO = "{{.PublisherID}}";

    private static final String BIDDER_CURRENCY = "USD";

    private static final TypeReference<ExtPrebid<?, ExtImpBrave>> BRAVE_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BraveBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final String url;

        try {
            final ExtImpBrave extImpBrave = parseImpExt(request.getImp().get(0));
            url = resolveEndpoint(extImpBrave.getPlacementId());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(removeFirstImpExt(request.getImp()))
                .build();

        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, url, mapper));
    }

    private ExtImpBrave parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BRAVE_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }
    }

    private static List<Imp> removeFirstImpExt(List<Imp> imps) {
        return IntStream.range(0, imps.size())
                .mapToObj(impIndex -> impIndex == 0
                        ? imps.get(impIndex).toBuilder().ext(null).build()
                        : imps.get(impIndex))
                .toList();
    }

    private String resolveEndpoint(String publisherId) {
        return endpointUrl.replace(PUBLISHER_MACRO, StringUtils.stripToEmpty(publisherId));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null) {
            throw new PreBidException("Bad Server Response");
        }
        if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .limit(1)
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), BIDDER_CURRENCY))
                .toList();
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bid.getImpid())) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
