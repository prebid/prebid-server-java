package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.execution.Timeout;

import java.util.Collections;
import java.util.Objects;

public class DisabledBidderRequester implements BidderRequester {

    private String errorMessage;

    public DisabledBidderRequester(String errorMessage) {
        this.errorMessage = Objects.requireNonNull(errorMessage);
    }

    @Override
    public Future<BidderSeatBid> requestBids(BidRequest bidRequest, Timeout timeout) {
        return Future.succeededFuture(BidderSeatBid.of(Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(BidderError.create(errorMessage))));
    }
}
