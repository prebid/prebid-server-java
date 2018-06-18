package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.prebid.server.proto.response.AmpResponse;

/**
 * A hook that is pulled prior sending the AMP RTC response back to the client.
 * It allows companies that host Prebid Server to add custom key values in the AMP RTC response
 */
public interface AmpResponsePostProcessor {

    /**
     * This method is called prior sending the response back to the client
     *
     * @param bidRequest  original auction request
     * @param bidResponse auction result
     * @param ampResponse AMP RTC response
     * @param queryParams request's query params
     * @return a {@link Future} with (possibly modified) amp response result
     */
    Future<AmpResponse> postProcess(BidRequest bidRequest, BidResponse bidResponse, AmpResponse ampResponse,
                                    MultiMap queryParams);

    /**
     * Returns {@link NoOpAmpResponsePostProcessor} instance that just does nothing.
     */
    static AmpResponsePostProcessor noOp() {
        return new NoOpAmpResponsePostProcessor();
    }

    /**
     * Well, dump stub that gives back unaltered AMP response.
     */
    class NoOpAmpResponsePostProcessor implements AmpResponsePostProcessor {

        @Override
        public Future<AmpResponse> postProcess(BidRequest bidRequest, BidResponse bidResponse, AmpResponse ampResponse,
                                               MultiMap queryParams) {
            return Future.succeededFuture(ampResponse);
        }
    }
}
