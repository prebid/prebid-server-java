package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor(staticName = "of")
public class BidResponseResolver {

    private BidResponse bidResponse;
    private ObjectMapper mapper;

    public BidResponse applyTargeting(List<Audience> targeting) {
        if (CollectionUtils.isEmpty(targeting)) {
            return bidResponse;
        }

        final ObjectNode node = targetingToObjectNode(targeting);
        if (node == null) {
            return bidResponse;
        }

        final List<SeatBid> seatBids = Optional.ofNullable(bidResponse.getSeatbid())
                .orElse(Collections.emptyList())
                .stream().map(seatBid -> {
                    final List<Bid> bids = Optional.ofNullable(seatBid.getBid())
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(bid -> applyTargeting(bid, node))
                            .toList();

                    return seatBid.toBuilder().bid(bids).build();
                }).toList();

        return bidResponse.toBuilder()
                .seatbid(seatBids)
                .build();
    }

    private Bid applyTargeting(Bid bid, ObjectNode node) {
        final ObjectNode extNode = getOrCreateNode(bid.getExt());
        final ObjectNode prebidNode = getOrCreateNode((ObjectNode) extNode.get("prebid"));
        final ObjectNode targetingNode = getOrCreateNode((ObjectNode) prebidNode.get("targeting"));
        final JsonNode mergedTargetingNode = ExtMerger.mergeExt(targetingNode, node);

        prebidNode.set("targeting", mergedTargetingNode);
        extNode.set("prebid", prebidNode);

        return bid.toBuilder().ext(extNode).build();
    }

    private ObjectNode getOrCreateNode(ObjectNode node) {
        return Optional.ofNullable(node).orElseGet(() -> mapper.createObjectNode());
    }

    private ObjectNode targetingToObjectNode(List<Audience> targeting) {
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
