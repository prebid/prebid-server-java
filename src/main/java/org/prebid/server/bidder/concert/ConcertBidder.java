package org.prebid.server.bidder.concert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.concert.ExtImpConcert;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ConcertBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConcert>> CONCERT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String ADAPTER_VERSION = "1.0.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ConcertBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        try {
            final ExtImpConcert extImpConcert = parseImpExt(request.getImp().getFirst());
            return Result.withValue(BidderUtil.defaultRequest(
                    updateBidRequest(request, extImpConcert),
                    endpointUrl,
                    mapper));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private ExtImpConcert parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), CONCERT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("get bidder ext: bidder ext: " + e.getMessage());
        }
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, ExtImpConcert extImpConcert) {
        return bidRequest.toBuilder()
                .ext(updateExtRequest(bidRequest.getExt(), extImpConcert))
                .build();
    }

    private ExtRequest updateExtRequest(ExtRequest extRequest, ExtImpConcert extImpConcert) {
        final ExtRequest newExtRequest = extRequest != null
                ? copyExtRequest(extRequest)
                : ExtRequest.of(null);

        final String partnerId = extImpConcert.getPartnerId();
        newExtRequest.addProperty("adapterVersion", TextNode.valueOf(ADAPTER_VERSION));
        if (partnerId != null) {
            newExtRequest.addProperty("partnerId", TextNode.valueOf(partnerId));
        }

        return newExtRequest;
    }

    private ExtRequest copyExtRequest(ExtRequest extRequest) {
        try {
            return mapper.mapper().treeToValue(mapper.mapper().valueToTree(extRequest), ExtRequest.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = parseBidResponse(httpCall.getResponse());
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidResponse parseBidResponse(HttpResponse response) {
        try {
            return mapper.decodeValue(response.getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("no bids returned");
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
        final BidType bidType;
        try {
            bidType = resolveBidType(bid.getMtype(), bid.getImpid());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.of(bid, bidType, currency);
    }

    private static BidType resolveBidType(Integer mType, String impId) {
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> throw new PreBidException("native media types are not yet supported");
            default -> throw new PreBidException("Failed to parse media type for bid: \"%s\"".formatted(impId));
        };
    }
}
