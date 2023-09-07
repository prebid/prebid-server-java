package org.prebid.server.bidder.bluesea;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.bluesea.ExtImpBlueSea;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class BlueSeaBidder implements Bidder<BidRequest> {

    private static final Set<String> SUPPORTED_BID_TYPES_TEXTUAL = Set.of("banner", "video", "native");
    private static final TypeReference<ExtPrebid<?, ExtImpBlueSea>> BLUE_SEA_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BlueSeaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpBlueSea extImpBlueSea = parseImpExt(imp);
                requests.add(makeRequest(bidRequest, extImpBlueSea));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpBlueSea parseImpExt(Imp imp) throws PreBidException {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BLUE_SEA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> makeRequest(BidRequest bidRequest, ExtImpBlueSea extImpBlueSea) {

        return BidderUtil.defaultRequest(bidRequest, resolveUrl(extImpBlueSea), mapper);
    }

    private String resolveUrl(ExtImpBlueSea extImpBlueSea) {
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException("Invalid url: %s, error: %s".formatted(endpointUrl, e.getMessage()));
        }

        return uriBuilder
                .addParameter("pubid", extImpBlueSea.getPubId())
                .addParameter("token", extImpBlueSea.getToken())
                .toString();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bids = extractBids(bidResponse, errors);

        return Result.of(bids, errors);
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, resolveBidType(bid), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid) {
        final ObjectNode bidExt = bid.getExt();
        final JsonNode mediaTypeNode = bidExt != null ? bidExt.at("/mediatype") : null;
        final String bidTypeTextual = Optional.ofNullable(mediaTypeNode)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .orElseThrow(() ->
                        new PreBidException("Missing bid media type in bid with id: %s"
                                .formatted(bid.getId())));

        return switch (bidTypeTextual) {
            case "banner" -> BidType.banner;
            case "video" -> BidType.video;
            case "native" -> BidType.xNative;
            default -> throw new PreBidException("Unknown bid type: %s, in bid with id: %s"
                    .formatted(bidTypeTextual, bid.getId()));
        };
    }
}
