package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Reason;
import org.prebid.server.hooks.modules.optable.targeting.model.Status;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AuctionResponseValidatorTest {

    private AuctionResponseValidator target;

    @BeforeEach
    public void setUp() {
        target = new AuctionResponseValidator();
    }

    @Test
    public void shouldReturnNobidStatusWhenBidResponseIsEmpty() {
        // given
        final BidResponse bidResponse = BidResponse.builder().build();

        // when
        final EnrichmentStatus result = target.checkEnrichmentPossibility(bidResponse, givenTargeting());

        // then
        assertThat(result).isNotNull()
                .returns(Status.FAIL, EnrichmentStatus::status)
                .returns(Reason.NOBID, EnrichmentStatus::reason);
    }

    @Test
    public void shouldReturnNoKeywordsStatusWhenTargetingHasNoIds() {
        // given
        final BidResponse bidResponse = BidResponse.builder().build();

        // when
        final EnrichmentStatus result = target.checkEnrichmentPossibility(bidResponse, givenTargeting());

        // then
        assertThat(result).isNotNull()
                .returns(Status.FAIL, EnrichmentStatus::status)
                .returns(Reason.NOBID, EnrichmentStatus::reason);
    }

    @Test
    public void shouldReturnSuccessStatus() {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder()
                                .bid(List.of(Bid.builder().build()))
                        .build()))
                .build();

        // when
        final EnrichmentStatus result = target.checkEnrichmentPossibility(bidResponse, givenTargeting());

        // then
        assertThat(result).isNotNull()
                .returns(Status.SUCCESS, EnrichmentStatus::status)
                .returns(Reason.NONE, EnrichmentStatus::reason);
    }

    protected List<Audience> givenTargeting() {
        return List.of(new Audience("provider", List.of(new AudienceId("id")),
                "keyspace", 1));
    }
}
