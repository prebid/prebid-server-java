package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.BidResponse;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;

import java.util.Objects;

public class Id5SignatureBidResponseEnricher implements PayloadUpdate<AuctionResponsePayload> {

    private final String id5Signature;
    private final ObjectMapper mapper;
    private final JsonMerger jsonMerger;

    private Id5SignatureBidResponseEnricher(String id5Signature, ObjectMapper mapper, JsonMerger jsonMerger) {
        this.id5Signature = id5Signature;
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public static Id5SignatureBidResponseEnricher of(String id5Signature, ObjectMapper mapper, JsonMerger jsonMerger) {
        return new Id5SignatureBidResponseEnricher(id5Signature, mapper, jsonMerger);
    }

    @Override
    public AuctionResponsePayload apply(AuctionResponsePayload payload) {
        return AuctionResponsePayloadImpl.of(enrichBidResponse(payload.bidResponse(), id5Signature));
    }

    private BidResponse enrichBidResponse(BidResponse bidResponse, String id5Signature) {
        if (StringUtils.isEmpty(id5Signature)) {
            return bidResponse;
        }
        final ObjectNode passthroughNode = id5SignatureToObjectNode(id5Signature);

        final ExtBidResponse existingExt = bidResponse.getExt();
        final ExtBidResponsePrebid existingPrebid = existingExt != null ? existingExt.getPrebid() : null;
        final JsonNode existingPassthrough = existingPrebid != null ? existingPrebid.getPassthrough() : null;

        final JsonNode mergedPassthrough = existingPassthrough != null
                ? jsonMerger.merge(passthroughNode, existingPassthrough)
                : passthroughNode;

        final ExtBidResponsePrebid modifiedPrebid = existingPrebid != null
                ? existingPrebid.toBuilder()
                .passthrough(mergedPassthrough)
                .build()
                : ExtBidResponsePrebid.builder()
                .passthrough(mergedPassthrough)
                .build();

        final ExtBidResponse modifiedExt = existingExt != null
                ? existingExt.toBuilder()
                .prebid(modifiedPrebid)
                .build()
                : ExtBidResponse.builder()
                .prebid(modifiedPrebid)
                .build();

        return bidResponse.toBuilder()
                .ext(modifiedExt)
                .build();
    }

    private ObjectNode id5SignatureToObjectNode(String id5Signature) {
        final ObjectNode node = mapper.createObjectNode();
        node.set("id5_signature", TextNode.valueOf(id5Signature));

        final ObjectNode optableNode = mapper.createObjectNode();
        optableNode.set("optable", node);

        return optableNode;
    }
}
