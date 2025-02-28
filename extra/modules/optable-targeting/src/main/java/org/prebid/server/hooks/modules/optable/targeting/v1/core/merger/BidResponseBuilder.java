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
public class BidResponseBuilder {

    private static final BaseMerger EXT_MERGER = new BaseMerger();

    private BidResponse bidResponse;

    private ObjectMapper mapper;

    public BidResponseBuilder applyTargeting(List<Audience> targeting) {
        if (CollectionUtils.isEmpty(targeting)) {
            return this;
        }

        final ObjectNode node = targetingToObjectNode(targeting);
        if (node == null) {
            return this;
        }

        final List<SeatBid> seatBids = Optional.ofNullable(bidResponse.getSeatbid())
                .orElse(Collections.emptyList())
                .stream().map(seatBid -> {
                    final List<Bid> bids = Optional.ofNullable(seatBid.getBid()).orElse(Collections.emptyList())
                            .stream().map(bid -> {
                                final ObjectNode extNode = Optional.ofNullable(bid.getExt())
                                        .orElseGet(() -> mapper.createObjectNode());
                                final ObjectNode prebidNode = Optional.ofNullable((ObjectNode) (extNode.get("prebid")))
                                        .orElseGet(() -> mapper.createObjectNode());
                                final ObjectNode targetingNode =
                                        Optional.ofNullable((ObjectNode) (prebidNode.get("targeting")))
                                                .orElseGet(() -> mapper.createObjectNode());
                                final JsonNode mergedTargetingNode = EXT_MERGER.mergeExt(targetingNode, node);

                                prebidNode.set("targeting", mergedTargetingNode);
                                extNode.set("prebid", prebidNode);

                                return bid.toBuilder().ext(extNode).build();
                            }).toList();

                    return seatBid.toBuilder().bid(bids).build();
                }).toList();

        bidResponse = bidResponse.toBuilder()
                .seatbid(seatBids)
                .build();

        return this;
    }

    private ObjectNode targetingToObjectNode(List<Audience> targeting) {
        final ObjectNode node = mapper.createObjectNode();
        targeting.forEach(it -> {
            final List<AudienceId> ids = it.getIds();
            if (CollectionUtils.isNotEmpty(ids)) {
                final List<String> strIds = ids.stream().map(AudienceId::getId).toList();
                node.putIfAbsent(it.getKeyspace(), TextNode.valueOf(String.join(",", strIds)));
            }
        });

        return node;
    }

    public BidResponse build() {
        return bidResponse;
    }
}
