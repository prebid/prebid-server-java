package org.prebid.server.bidder.bidscube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BidscubeBidder implements Bidder<BidRequest> {

    private static final Set<String> POSSIBLE_BID_TYPES = Set.of("banner", "video", "native");

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
                requests.add(createRequest(request, imp, getBidderNode(imp)));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private static ObjectNode getBidderNode(Imp imp) {
        final JsonNode impExtNode = imp.getExt();
        final JsonNode bidderExtNode = isNotEmptyOrMissedNode(impExtNode) ? impExtNode.get("bidder") : null;
        if (!isNotEmptyOrMissedNode(bidderExtNode)) {
            throw new PreBidException("Missing required bidder parameters");
        }
        return bidderExtNode.deepCopy();
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, Imp imp, ObjectNode bidderNode) {
        final Imp internalImp = imp.toBuilder().ext(bidderNode).build();
        final BidRequest internalRequest = request.toBuilder().imp(Collections.singletonList(internalImp)).build();
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(internalRequest)
                .body(mapper.encodeToBytes(internalRequest))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> constructBidderBid(bid, bidResponse, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BidderBid constructBidderBid(Bid bid, BidResponse bidResponse, List<BidderError> errors) {
        final JsonNode extNode = bid.getExt();
        final JsonNode typeNode = isNotEmptyOrMissedNode(extNode) ? extNode.at("/prebid/type") : null;

        if (typeNode == null || !typeNode.isTextual()) {
            errors.add(BidderError.badInput("Unable to read bid.ext.prebid.type"));
            return null;
        }

        return BidderBid.of(bid, resolveBidType(typeNode), bidResponse.getCur());
    }

    private static boolean isNotEmptyOrMissedNode(JsonNode node) {
        return node != null && !node.isEmpty();
    }

    private BidType resolveBidType(JsonNode bidType) {
        return !POSSIBLE_BID_TYPES.contains(bidType.asText())
                ? BidType.banner
                : mapper.mapper().convertValue(bidType, BidType.class);
    }
}
