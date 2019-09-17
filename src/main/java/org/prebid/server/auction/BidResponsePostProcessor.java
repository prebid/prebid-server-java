package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.settings.model.Account;

/**
 * A hook that is pulled once auction has been held. It allows companies that host Prebid Server to define and apply
 * their custom post-processing logic to auction result.
 */
public interface BidResponsePostProcessor {

    /**
     * This method is called when auction is finished.
     *
     * @param context     represents initial web request
     * @param uidsCookie  auction request {@link Uids} container
     * @param bidRequest  original auction request
     * @param bidResponse auction result
     * @param account     {@link Account} fetched from request
     * @return a {@link Future} with (possibly modified) auction result
     */
    Future<BidResponse> postProcess(RoutingContext context, UidsCookie uidsCookie, BidRequest bidRequest,
                                    BidResponse bidResponse, Account account);

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
        public Future<BidResponse> postProcess(RoutingContext context, UidsCookie uidsCookie, BidRequest bidRequest,
                                               BidResponse bidResponse, Account account) {
            return Future.succeededFuture(bidResponse);
        }
    }
}
