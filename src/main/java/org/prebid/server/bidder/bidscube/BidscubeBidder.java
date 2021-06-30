package org.prebid.server.bidder.bidscube;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidscube.ExtImpBidscube;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BidscubeBidder implements Bidder<BidRequest> {
    private static final TypeReference<ExtPrebid<?, ExtImpBidscube>> BIDSCUBE_IMP_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBidscube>>() {
            };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> BIDSCUBE_BID_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BidscubeBidder(JacksonMapper mapper, String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validateImp(imp);
                requests.add(createRequest(request, imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private void validateImp(Imp imp) {
        final ExtImpBidscube extImpBidscube;
        try {
            extImpBidscube = mapper.mapper().convertValue(imp.getExt(), BIDSCUBE_IMP_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("bidder parameters required");
        }

        if (StringUtils.isEmpty(extImpBidscube.getPlacementId())) {
            throw new PreBidException("bidder parameters required");
        }
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, Imp imp) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(request.toBuilder().imp(Collections.singletonList(imp)).build())
                .body(mapper.encode(request))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderBid> bids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        final BidResponse bidResponse;

        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                try {
                    final BidType bidType = mapper.mapper().convertValue(bid.getExt(), BIDSCUBE_BID_EXT_TYPE_REFERENCE)
                            .getPrebid().getType();

                    bids.add(BidderBid.of(bid, bidType == null ? BidType.banner : bidType, bidResponse.getCur()));
                } catch (IllegalArgumentException e) {
                    errors.add(BidderError.badInput(e.getMessage()));
                }
            }
        }

        return Result.of(bids, errors);
    }
}
