package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.response.BidResponse;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;

import static org.assertj.core.api.Assertions.assertThat;

public class Id5SignatureBidResponseEnricherTest {

    private final JacksonMapper jacksonMapper = new JacksonMapper(ObjectMapperProvider.mapper());
    private final JsonMerger jsonMerger = new JsonMerger(jacksonMapper);

    @Test
    public void shouldReturnOriginBidResponseWhenId5SignatureIsNull() {
        // given
        final BidResponse bidResponse = BidResponse.builder().build();
        final AuctionResponsePayload payload = AuctionResponsePayloadImpl.of(bidResponse);
        final Id5SignatureBidResponseEnricher enricher =
                Id5SignatureBidResponseEnricher.of(null, ObjectMapperProvider.mapper(), jsonMerger);

        // when
        final AuctionResponsePayload result = enricher.apply(payload);

        // then
        assertThat(result.bidResponse()).isSameAs(bidResponse);
    }

    @Test
    public void shouldReturnOriginBidResponseWhenId5SignatureIsEmpty() {
        // given
        final BidResponse bidResponse = BidResponse.builder().build();
        final AuctionResponsePayload payload = AuctionResponsePayloadImpl.of(bidResponse);
        final Id5SignatureBidResponseEnricher enricher =
                Id5SignatureBidResponseEnricher.of("", ObjectMapperProvider.mapper(), jsonMerger);

        // when
        final AuctionResponsePayload result = enricher.apply(payload);

        // then
        assertThat(result.bidResponse()).isSameAs(bidResponse);
    }

    @Test
    public void shouldAddId5SignatureToPassthroughWhenExtIsAbsent() {
        // given
        final BidResponse bidResponse = BidResponse.builder().build();
        final AuctionResponsePayload payload = AuctionResponsePayloadImpl.of(bidResponse);
        final Id5SignatureBidResponseEnricher enricher =
                Id5SignatureBidResponseEnricher.of("signature", ObjectMapperProvider.mapper(), jsonMerger);

        // when
        final AuctionResponsePayload result = enricher.apply(payload);

        // then
        final BidResponse enriched = result.bidResponse();
        assertThat(enriched.getExt()).isNotNull();
        assertThat(enriched.getExt().getPrebid()).isNotNull();
        final JsonNode passthrough = enriched.getExt().getPrebid().getPassthrough();
        assertThat(passthrough).isNotNull();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo("signature");
    }

    @Test
    public void shouldMergeId5SignatureWithExistingPassthrough() {
        // given
        final ObjectNode existingPassthrough = ObjectMapperProvider.mapper().createObjectNode();
        existingPassthrough.set("other", ObjectMapperProvider.mapper().createObjectNode()
                .set("value", TextNode.valueOf("otherValue")));
        existingPassthrough.set("optable", ObjectMapperProvider.mapper().createObjectNode()
                .set("existing", TextNode.valueOf("preserved")));

        final ExtBidResponse ext = ExtBidResponse.builder()
                .prebid(ExtBidResponsePrebid.builder()
                        .passthrough(existingPassthrough)
                        .build())
                .build();
        final BidResponse bidResponse = BidResponse.builder().ext(ext).build();
        final AuctionResponsePayload payload = AuctionResponsePayloadImpl.of(bidResponse);
        final Id5SignatureBidResponseEnricher enricher =
                Id5SignatureBidResponseEnricher.of("signature", ObjectMapperProvider.mapper(), jsonMerger);

        // when
        final AuctionResponsePayload result = enricher.apply(payload);

        // then
        final JsonNode passthrough = result.bidResponse().getExt().getPrebid().getPassthrough();
        assertThat(passthrough.get("optable").get("id5_signature").asText()).isEqualTo("signature");
        assertThat(passthrough.get("optable").get("existing").asText()).isEqualTo("preserved");
        assertThat(passthrough.get("other").get("value").asText()).isEqualTo("otherValue");
    }

    @Test
    public void shouldPreserveExistingPrebidFieldsWhenAddingPassthrough() {
        // given
        final ExtBidResponse ext = ExtBidResponse.builder()
                .prebid(ExtBidResponsePrebid.builder().build())
                .build();
        final BidResponse bidResponse = BidResponse.builder().ext(ext).build();
        final AuctionResponsePayload payload = AuctionResponsePayloadImpl.of(bidResponse);
        final Id5SignatureBidResponseEnricher enricher =
                Id5SignatureBidResponseEnricher.of("signature", ObjectMapperProvider.mapper(), jsonMerger);

        // when
        final AuctionResponsePayload result = enricher.apply(payload);

        // then
        final ExtBidResponsePrebid prebid = result.bidResponse().getExt().getPrebid();
        assertThat(prebid).isNotNull();
        assertThat(prebid.getPassthrough().get("optable").get("id5_signature").asText())
                .isEqualTo("signature");
    }
}
