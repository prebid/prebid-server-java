package org.prebid.server.bidder.bematterfull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.bematterfull.ExtImpBematterfull;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BematterfullBidder implements Bidder<BidRequest> {

    private static final String HOST_MACRO = "{{Host}}";
    private static final String SOURCE_ID_MACRO = "{{SourceId}}";
    private static final String EXT_PREBID = "prebid";
    private static final TypeReference<ExtPrebid<?, ExtImpBematterfull>> EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BematterfullBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpBematterfull extImp = parseImpExt(imp);
                httpRequests.add(makeHttpRequest(request, extImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (CollectionUtils.isEmpty(httpRequests)) {
            errors.add(BidderError.badInput("Adapter request is empty"));
            return Result.withErrors(errors);
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpBematterfull parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Bematterfull extension: " + e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, ExtImpBematterfull extImp) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(makeUrl(extImp))
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(bidRequest))
                .impIds(BidderUtil.impIds(bidRequest))
                .payload(bidRequest)
                .build();
    }

    private String makeUrl(ExtImpBematterfull extImp) {
        return endpointUrl
                .replace(HOST_MACRO, extImp.getEnv())
                .replace(SOURCE_ID_MACRO, extImp.getPid());
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidResponse.getSeatbid()
                .stream()
                .flatMap(seatBid -> Optional.ofNullable(seatBid.getBid()).orElse(List.of()).stream())
                .map(bid -> makeBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, String currency, List<BidderError> errors) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> (ObjectNode) ext.get(EXT_PREBID))
                .map(prebid -> parseExtBidPrebid(prebid, errors))
                .map(ExtBidPrebid::getType)
                .map(type -> BidderBid.of(bid, type, currency))
                .orElse(null);
    }

    private ExtBidPrebid parseExtBidPrebid(ObjectNode prebid, List<BidderError> errors) {
        try {
            return mapper.mapper().treeToValue(prebid, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            errors.add(BidderError.badInput("Failed to parse bid[i].ext.prebid.type: " + e.getMessage()));
            return null;
        }
    }

}
