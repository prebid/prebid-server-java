package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.json.JsonMerger;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class BidResponseEnricher implements PayloadUpdate<AuctionResponsePayload> {

    private final List<Audience> targeting;
    private final ObjectMapper mapper;
    private final JsonMerger jsonMerger;

    private BidResponseEnricher(List<Audience> targeting, ObjectMapper mapper, JsonMerger jsonMerger) {
        this.targeting = targeting;
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public static BidResponseEnricher of(List<Audience> targeting, ObjectMapper mapper, JsonMerger jsonMerger) {
        return new BidResponseEnricher(targeting, mapper, jsonMerger);
    }

    @Override
    public AuctionResponsePayload apply(AuctionResponsePayload payload) {
        return AuctionResponsePayloadImpl.of(enrichBidResponse(payload.bidResponse(), targeting));
    }

    private BidResponse enrichBidResponse(BidResponse bidResponse, List<Audience> targeting) {
        if (CollectionUtils.isEmpty(targeting)) {
            return bidResponse;
        }

        final ObjectNode node = targetingToObjectNode(targeting);
        if (node.isEmpty()) {
            return bidResponse;
        }

        final List<SeatBid> seatBids = CollectionUtils.emptyIfNull(bidResponse.getSeatbid()).stream()
                .map(seatBid -> seatBid.toBuilder()
                        .bid(CollectionUtils.emptyIfNull(seatBid.getBid()).stream()
                                .map(bid -> applyTargeting(bid, node))
                                .toList())
                        .build())
                .toList();

        return bidResponse.toBuilder()
                .seatbid(seatBids)
                .build();
    }

    private ObjectNode targetingToObjectNode(List<Audience> targeting) {
        final ObjectNode node = mapper.createObjectNode();

        for (Audience audience : targeting) {
            final List<AudienceId> ids = audience.getIds();
            if (CollectionUtils.isEmpty(ids)) {
                continue;
            }

            final String joinedIds = ids.stream()
                    .map(AudienceId::getId)
                    .collect(Collectors.joining(","));
            node.putIfAbsent(audience.getKeyspace(), TextNode.valueOf(joinedIds));
        }

        return node;
    }

    private Bid applyTargeting(Bid bid, ObjectNode node) {
        final ObjectNode ext = Optional.ofNullable(bid.getExt())
                .map(ObjectNode::deepCopy)
                .orElseGet(mapper::createObjectNode);

        final ObjectNode prebid = newNodeIfNull(ext.get("prebid"));
        final ObjectNode targeting;
        try {
            targeting = (ObjectNode) jsonMerger.merge(node, newNodeIfNull(prebid.get("targeting")));
        } catch (InvalidRequestException e) {
            return bid;
        }

        prebid.set("targeting", targeting);
        ext.set("prebid", prebid);

        return bid.toBuilder().ext(ext).build();
    }

    private ObjectNode newNodeIfNull(JsonNode node) {
        return node == null || !node.isObject()
                ? mapper.createObjectNode()
                : (ObjectNode) node;
    }
}
