package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.execution.v1.auction.AuctionResponsePayloadImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.hooks.v1.auction.AuctionResponsePayload;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BidResponseEnricherTest extends BaseOptableTest {

    @Test
    public void shouldEnrichBidResponseByTargetingKeywords() {
        // given
        final AuctionResponsePayload auctionResponsePayload = AuctionResponsePayloadImpl.of(givenBidResponse());

        // when
        final AuctionResponsePayload result = BidResponseEnricher.of(givenTargeting(), mapper, jsonMerger)
                .apply(auctionResponsePayload);
        final ObjectNode targeting = (ObjectNode) result.bidResponse().getSeatbid()
                .getFirst()
                .getBid()
                .getFirst()
                .getExt()
                .get("prebid")
                .get("targeting");

        // then
        assertThat(result).isNotNull();
        assertThat(targeting.get("keyspace").asText()).isEqualTo("audienceId,audienceId2");
    }

    @Test
    public void shouldReturnOriginBidResponseWhenNoTargetingKeywords() {
        // given
        final AuctionResponsePayload auctionResponsePayload = AuctionResponsePayloadImpl.of(givenBidResponse());

        // when
        final AuctionResponsePayload result = BidResponseEnricher.of(null, mapper, jsonMerger)
                .apply(auctionResponsePayload);
        final ObjectNode targeting = (ObjectNode) result.bidResponse().getSeatbid()
                .getFirst()
                .getBid()
                .getFirst()
                .getExt()
                .get("prebid")
                .get("targeting");

        // then
        assertThat(result).isNotNull();
        assertThat(targeting.get("keyspace")).isNull();
    }

    private static List<Audience> givenTargeting() {
        return List.of(new Audience(
                "provider",
                List.of(new AudienceId("audienceId"), new AudienceId("audienceId2")),
                "keyspace",
                1));
    }
}
