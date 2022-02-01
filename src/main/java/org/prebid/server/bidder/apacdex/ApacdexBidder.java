package org.prebid.server.bidder.apacdex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.apacdex.proto.ExtImpApacdex;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ApacdexBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpApacdex>> APACDEX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ApacdexBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> imps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpApacdex extImpApacdex = parseImpExt(imp);
                imps.add(modifyImp(imp, extImpApacdex));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.withValue(makeHttpRequest(request, imps));
    }

    private Imp modifyImp(Imp imp, ExtImpApacdex extImpApacdex) {
        return imp.toBuilder()
                .ext(mapper.mapper().valueToTree(extImpApacdex))
                .build();
    }

    private ExtImpApacdex parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), APACDEX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest, List<Imp> imps) {
        final BidRequest modifyBidRequest = bidRequest.toBuilder().imp(imps).build();

        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers)
                .payload(modifyBidRequest)
                .body(mapper.encodeToBytes(modifyBidRequest))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;

        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        return Result.of(extractBids(bidResponse, errors), errors);
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> constructBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid constructBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final JsonNode extNode = bid.getExt();
        final JsonNode typeNode = isNotEmptyOrMissedNode(extNode) ? extNode.at("/prebid/type") : null;

        try {
            return BidderBid.of(bid, getBidType(typeNode, bid.getImpid()), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidType getBidType(JsonNode typeNode, String impid) {
        try {
            return mapper.mapper().convertValue(typeNode, BidType.class);
        } catch (IllegalArgumentException ignore) {
            throw new PreBidException(String.format("Failed to parse bid media type for impression %s", impid));
        }
    }

    private static boolean isNotEmptyOrMissedNode(JsonNode node) {
        return node != null && !node.isEmpty();
    }
}
