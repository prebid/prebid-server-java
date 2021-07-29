package org.prebid.server.hooks.execution.model;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StageWithHookTypeTest {

    @Test
    public void forStageShouldSupportAllStages() {
        for (final Stage stage : Stage.values()) {
            Assertions.assertThatCode(() -> StageWithHookType.forStage(stage)).doesNotThrowAnyException();
        }
    }

    @Test
    public void forStageShouldReturnEntrypoint() {
        assertThat(StageWithHookType.forStage(Stage.entrypoint)).isSameAs(StageWithHookType.ENTRYPOINT);
    }

    @Test
    public void forStageShouldReturnRawAuctionRequest() {
        assertThat(StageWithHookType.forStage(Stage.raw_auction_request))
                .isSameAs(StageWithHookType.RAW_AUCTION_REQUEST);
    }

    @Test
    public void forStageShouldReturnProcessedAuctionRequest() {
        assertThat(StageWithHookType.forStage(Stage.processed_auction_request))
                .isSameAs(StageWithHookType.PROCESSED_AUCTION_REQUEST);
    }

    @Test
    public void forStageShouldReturnBidderRequest() {
        assertThat(StageWithHookType.forStage(Stage.bidder_request))
                .isSameAs(StageWithHookType.BIDDER_REQUEST);
    }

    @Test
    public void forStageShouldReturnRawBidderResponse() {
        assertThat(StageWithHookType.forStage(Stage.raw_bidder_response))
                .isSameAs(StageWithHookType.RAW_BIDDER_RESPONSE);
    }

    @Test
    public void forStageShouldReturnProcessedBidderResponse() {
        assertThat(StageWithHookType.forStage(Stage.processed_bidder_response))
                .isSameAs(StageWithHookType.PROCESSED_BIDDER_RESPONSE);
    }

    @Test
    public void forStageShouldReturnAuctionResponse() {
        assertThat(StageWithHookType.forStage(Stage.auction_response))
                .isSameAs(StageWithHookType.AUCTION_RESPONSE);
    }
}
