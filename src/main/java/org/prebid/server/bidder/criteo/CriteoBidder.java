package org.prebid.server.bidder.criteo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CriteoBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public CriteoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        return Result.withValue(BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper));
    }

    @Override
    @Deprecated(forRemoval = true)
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        return Result.withError(BidderError.generic("Deprecated adapter method invoked"));
    }

    @Override
    public CompositeBidderResponse makeBidderResponse(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final CriteoBidResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(), CriteoBidResponse.class);

            return CompositeBidderResponse.builder()
                    .bids(extractBids(bidResponse))
                    .igi(extractIgi(bidResponse))
                    .build();
        } catch (DecodeException | PreBidException e) {
            return CompositeBidderResponse.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(CriteoBidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(modifyBidExt(bid), getBidType(bid), bidResponse.getCur()))
                .toList();
    }

    private Bid modifyBidExt(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("prebid"))
                .map(prebid -> prebid.get("networkName"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::textValue)
                .map(networkName -> bid.toBuilder().ext(makeExt(networkName)).build())
                .orElse(bid);
    }

    private static BidType getBidType(Bid bid) {
        return Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("prebid"))
                .map(extPrebid -> extPrebid.get("type"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::textValue)
                .map(BidType::fromString)
                .orElseThrow(() -> new PreBidException(
                        "Missing ext.prebid.type in bid for impression : %s.".formatted(bid.getImpid())));
    }

    private ObjectNode makeExt(String networkName) {
        return mapper.mapper().valueToTree(ExtBidPrebid.builder()
                .meta(ExtBidPrebidMeta.builder().networkName(networkName).build())
                .build());
    }

    private static List<ExtIgi> extractIgi(CriteoBidResponse bidResponse) {
        return Optional.ofNullable(bidResponse)
                .map(CriteoBidResponse::getExt)
                .map(CriteoExtBidResponse::getIgi)
                .filter(CollectionUtils::isNotEmpty)
                .orElse(Collections.emptyList());
    }
}
