package org.prebid.server.bidder.kueezrtb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.kueezrtb.KueezRtbImpExt;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class KueezRtbBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, KueezRtbImpExt>> TYPE_REFERENCE = new TypeReference<>() { };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public KueezRtbBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final KueezRtbImpExt impExt = parseImpExt(imp);
                requests.add(makeHttpRequest(bidRequest, imp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return Result.of(requests, errors);
    }

    private KueezRtbImpExt parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, Imp imp, KueezRtbImpExt impExt) {
        final BidRequest modifiedBidRequest = bidRequest.toBuilder().imp(Collections.singletonList(imp)).build();
        final String uri = endpointUrl + HttpUtil.encodeUrl(StringUtils.defaultString(impExt.getConnectionId()).trim());

        return BidderUtil.defaultRequest(modifiedBidRequest, uri, mapper);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType mediaType = getMediaTypeForBid(bid);
            return BidderBid.of(bid, mediaType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getMediaTypeForBid(Bid bid) {
        final Integer mType = bid.getMtype();
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case null, default -> throw new PreBidException("Could not define bid type for imp: " + bid.getImpid());
        };
    }
}
