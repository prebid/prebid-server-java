package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedBids;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class ResponseUpdaterTest {

    @Test
    public void shouldRemoveSpecifiedBids() {
        // given
        final ResponseUpdater updater = ResponseUpdater.create(BlockedBids.of(new HashSet<>(asList(1, 2, 4))));
        final List<BidderBid> bids = asList(
            bid("bid1", BidType.banner, "USD"),
            bid("bid2", BidType.video, "USD"),
            bid("bid3", BidType.audio, "EUR"),
            bid("bid4", BidType.xNative, "JPY"),
            bid("bid5", BidType.video, "UAH"));

        // when and then
        assertThat(updater.update(bids)).isEqualTo(asList(
            bid("bid1", BidType.banner, "USD"),
            bid("bid4", BidType.xNative, "JPY")));
    }

    private static BidderBid bid(String id, BidType type, String currency) {
        return BidderBid.of(Bid.builder().id(id).build(), type, currency);
    }
}
