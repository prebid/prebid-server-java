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
        assertThat(StageWithHookType.forStage(Stage.ENTRYPOINT)).isSameAs(StageWithHookType.ENTRYPOINT);
    }

    @Test
    public void forStageShouldReturnRawAuctionRequest() {
        assertThat(StageWithHookType.forStage(Stage.RAW_AUCTION_REQUEST))
                .isSameAs(StageWithHookType.RAW_AUCTION_REQUEST);
    }

    @Test
    public void forStageShouldReturnProcessedAuctionRequest() {
        assertThat(StageWithHookType.forStage(Stage.PROCESSED_AUCTION_REQUEST))
                .isSameAs(StageWithHookType.PROCESSED_AUCTION_REQUEST);
    }

    @Test
    public void forStageShouldReturnBidderRequest() {
        assertThat(StageWithHookType.forStage(Stage.BIDDER_REQUEST))
                .isSameAs(StageWithHookType.BIDDER_REQUEST);
    }

    @Test
    public void forStageShouldReturnRawBidderResponse() {
        assertThat(StageWithHookType.forStage(Stage.RAW_BIDDER_RESPONSE))
                .isSameAs(StageWithHookType.RAW_BIDDER_RESPONSE);
    }

    @Test
    public void forStageShouldReturnProcessedBidderResponse() {
        assertThat(StageWithHookType.forStage(Stage.PROCESSED_BIDDER_RESPONSE))
                .isSameAs(StageWithHookType.PROCESSED_BIDDER_RESPONSE);
    }

    @Test
    public void forStageShouldReturnAuctionResponse() {
        assertThat(StageWithHookType.forStage(Stage.AUCTION_RESPONSE))
                .isSameAs(StageWithHookType.AUCTION_RESPONSE);
    }
}
