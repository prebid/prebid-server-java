package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.merger.ExtMerger;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidResponseEnricher implements PayloadUpdate<AuctionResponsePayload> {

    ObjectMapper mapper;
    List<Audience> targeting;

    @Override
    public AuctionResponsePayload apply(AuctionResponsePayload payload) {
        return AuctionResponsePayloadImpl.of(enrichBidResponse(mapper, payload.bidResponse(), targeting));
    }

    private static BidResponse enrichBidResponse(ObjectMapper mapper,
                                                 BidResponse bidResponse,
                                                 List<Audience> targeting) {

        if (bidResponse == null || CollectionUtils.isEmpty(targeting)) {
            return bidResponse;
        }

        final ObjectNode node = targetingToObjectNode(mapper, targeting);
        if (node == null) {
            return bidResponse;
        }

        final List<SeatBid> seatBids = Optional.ofNullable(bidResponse.getSeatbid())
                .orElse(Collections.emptyList())
                .stream().map(seatBid -> {
                    final List<Bid> bids = Optional.ofNullable(seatBid.getBid())
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(bid -> applyTargeting(mapper, bid, node))
                            .toList();

                    return seatBid.toBuilder().bid(bids).build();
                }).toList();

        return bidResponse.toBuilder()
                .seatbid(seatBids)
                .build();
    }

    private static Bid applyTargeting(ObjectMapper mapper, Bid bid, ObjectNode node) {
        final ObjectNode extNode = getOrCreateNode(mapper, bid.getExt());
        final ObjectNode prebidNode = getOrCreateNode(mapper, (ObjectNode) extNode.get("prebid"));
        final ObjectNode targetingNode = getOrCreateNode(mapper, (ObjectNode) prebidNode.get("targeting"));
        final JsonNode mergedTargetingNode = ExtMerger.mergeExt(targetingNode, node);

        prebidNode.set("targeting", mergedTargetingNode);
        extNode.set("prebid", prebidNode);

        return bid.toBuilder().ext(extNode).build();
    }

    private static ObjectNode getOrCreateNode(ObjectMapper mapper, ObjectNode node) {
        return Optional.ofNullable(node).orElseGet(mapper::createObjectNode);
    }

    private static ObjectNode targetingToObjectNode(ObjectMapper mapper, List<Audience> targeting) {
        final ObjectNode node = mapper.createObjectNode();

        for (Audience audience: targeting) {
            final List<AudienceId> ids = audience.getIds();
            if (CollectionUtils.isNotEmpty(ids)) {
                final List<String> strIds = ids.stream().map(AudienceId::getId).toList();
                node.putIfAbsent(audience.getKeyspace(), TextNode.valueOf(String.join(",", strIds)));
            }
        }

        return node;
    }
}
