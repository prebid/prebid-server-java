package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.proto.Uids;

/**
 * A hook that is pulled once auction has been held. It allows companies that host Prebid Server to define and apply
 * their custom post-processing logic to auction result.
 */
public interface BidResponsePostProcessor {

    /**
     * This method is called when auction is finished.
     *
     * @param bidRequest original auction request
     * @param uidsCookie auction request {@link Uids} container
     * @param bidResponse auction result
     * @return a {@link Future} with (possibly modified) auction result
     */
    Future<BidResponse> postProcess(BidRequest bidRequest, UidsCookie uidsCookie, BidResponse bidResponse);

    /**
     * Returns {@link NoOpBidResponsePostProcessor} instance that just does nothing to original auction result.
     */
    static BidResponsePostProcessor noOp() {
        return new NoOpBidResponsePostProcessor();
    }

    /**
     * Well, dump stub that gives back unaltered auction result.
     */
    class NoOpBidResponsePostProcessor implements BidResponsePostProcessor {
        @Override
        public Future<BidResponse> postProcess(BidRequest bidRequest, UidsCookie uidsCookie, BidResponse bidResponse) {
            return Future.succeededFuture(bidResponse);
        }
    }
}
