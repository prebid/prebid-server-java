package org.prebid.server.bidder.smarthub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.smarthub.ExtImpSmarthub;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SmarthubBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmarthub>> SMARTHUB_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmarthub>>() {
            };

    private final String endpointTemplate;
    private final JacksonMapper mapper;

    public SmarthubBidder(String endpointTemplate, JacksonMapper mapper) {
        this.endpointTemplate = HttpUtil.validateUrl(Objects.requireNonNull(endpointTemplate));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);
        final ExtImpSmarthub extImpSmarthub;
        try {
            extImpSmarthub = mapper.mapper().convertValue(firstImp.getExt(), SMARTHUB_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .body(mapper.encode(request))
                .uri(buildEndpointUrl(extImpSmarthub))
                .payload(request)
                .headers(resolveHeaders())
                .build());
    }

    private MultiMap resolveHeaders() {
        return HttpUtil.headers().add("Prebid-Adapter-Ver", "1.0.0");
    }

    private String buildEndpointUrl(ExtImpSmarthub extImpSmarthub) {
        return endpointTemplate.replace("{{Host}}", extImpSmarthub.getPartnerName())
                .replace("{{AccountID}}", extImpSmarthub.getSeat())
                .replace("{{SourceId}}", extImpSmarthub.getToken());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        final List<SeatBid> seatBid = bidResponse != null ? bidResponse.getSeatbid() : null;
        final SeatBid firstSeatBid = CollectionUtils.isNotEmpty(seatBid) ? seatBid.get(0) : null;
        final List<Bid> bids = firstSeatBid != null ? firstSeatBid.getBid() : null;
        final Bid firstBid = CollectionUtils.isNotEmpty(bids) ? bids.get(0) : null;

        if (firstBid == null) {
            throw new PreBidException("SeatBid[0].Bid[0] cannot be empty");
        }
        return Collections.singletonList(toBidderBid(bidResponse, firstBid));
    }

    private BidderBid toBidderBid(BidResponse bidResponse, Bid bid) {
        try {
            return BidderBid.of(bid, getBidType(bid.getExt()), bidResponse.getCur());
        } catch (IllegalArgumentException | PreBidException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidType getBidType(ObjectNode bidExt) {
        final JsonNode typeNode = bidExt != null && !bidExt.isEmpty() ? bidExt.get("mediaType") : null;
        if (typeNode == null || !typeNode.isTextual()) {
            throw new PreBidException("missing bid ext");
        }
        return mapper.mapper().convertValue(typeNode.asText(), BidType.class);
    }
}
