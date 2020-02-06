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
import java.util.Map;
import java.util.Objects;

public class TripleliftBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final TypeReference<ExtPrebid<?, ExtImpTriplelift>> TRIPLELIFT_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpTriplelift>>() {
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
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest updatedRequest = bidRequest.toBuilder()
                .imp(validImps)
                .build();

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(mapper.encode(updatedRequest))
                        .headers(HttpUtil.headers())
                        .payload(updatedRequest)
                        .build()),
                errors);
    }

    private Imp modifyImp(Imp imp) throws PreBidException {
        if (imp.getBanner() == null && imp.getVideo() == null) {
            throw new PreBidException("neither Banner nor Video object specified");
        }

        final ExtImpTriplelift impExt = parseExtImpTriplelift(imp);
        final Imp.ImpBuilder impBuilder = imp.toBuilder().tagid(impExt.getInventoryCode());
        if (impExt.getFloor() != null) {
            impBuilder.bidfloor(impExt.getFloor());
        }

        return impBuilder.build();
    }

    private ExtImpTriplelift parseExtImpTriplelift(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(),
                    TRIPLELIFT_EXT_TYPE_REFERENCE).getBidder();
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
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                final ObjectNode ext = bid.getExt();
                if (ext == null) {
                    errors.add(BidderError.badServerResponse(String.format("Empty ext in bid %s", bid.getId())));
                    break;
                }
                try {
                    final TripleliftResponseExt tripleliftResponseExt = mapper.mapper().treeToValue(ext,
                            TripleliftResponseExt.class);
                    final BidderBid bidderBid = BidderBid.of(bid, getBidType(tripleliftResponseExt),
                            DEFAULT_BID_CURRENCY);
                    bidderBids.add(bidderBid);
                } catch (JsonProcessingException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                }
            }
        }
        return Result.of(bidderBids, errors);
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
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

        if (tripleliftInnerExt != null && tripleliftInnerExt.getFormat() == 11) {
            return BidType.video;
        }
        return BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

