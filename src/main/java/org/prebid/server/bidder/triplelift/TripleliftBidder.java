package org.prebid.server.bidder.triplelift;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.triplelift.model.TripleliftInnerExt;
import org.prebid.server.bidder.triplelift.model.TripleliftResponseExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.triplelift.ExtImpTriplelift;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TripleliftBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTriplelift>> TRIPLELIFT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TripleliftBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validImps.add(modifyImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions for triplelift"));
            return Result.withErrors(errors);
        }

        final BidRequest updatedRequest = bidRequest.toBuilder()
                .imp(validImps)
                .build();

        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(endpointUrl)
                                .body(mapper.encodeToBytes(updatedRequest))
                                .headers(HttpUtil.headers())
                                .payload(updatedRequest)
                                .build()),
                errors);
    }

    private Imp modifyImp(Imp imp) throws PreBidException {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("neither Banner nor Video object specified");
        }

        final ExtImpTriplelift impExt = parseImpExt(imp);
        return imp.toBuilder()
                .tagid(impExt.getInventoryCode())
                .bidfloor(ObjectUtils.defaultIfNull(impExt.getFloor(), imp.getBidfloor()))
                .build();
    }

    private ExtImpTriplelift parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TRIPLELIFT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBody(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                final ObjectNode bidExt = bid.getExt();
                if (bidExt == null) {
                    errors.add(BidderError.badServerResponse(String.format("Empty ext in bid %s", bid.getId())));
                    break;
                }

                try {
                    final TripleliftResponseExt tripleliftResponseExt = mapper.mapper().treeToValue(bidExt,
                            TripleliftResponseExt.class);
                    final BidType bidType = getBidType(tripleliftResponseExt);

                    bidderBids.add(BidderBid.of(bid, bidType, bidResponse.getCur()));
                } catch (JsonProcessingException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }
        return Result.of(bidderBids, errors);
    }

    private BidResponse decodeBody(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(TripleliftResponseExt tripleliftResponseExt) {
        final TripleliftInnerExt tripleliftInnerExt = tripleliftResponseExt != null
                ? tripleliftResponseExt.getTripleliftPb()
                : null;

        return tripleliftInnerExt != null && Objects.equals(tripleliftInnerExt.getFormat(), 11)
                ? BidType.video
                : BidType.banner;
    }
}
