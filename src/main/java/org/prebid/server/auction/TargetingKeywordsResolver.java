package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;

import java.util.Collections;
import java.util.Map;

public interface TargetingKeywordsResolver {

    Map<String, String> resolve(Bid bid);

    static TargetingKeywordsResolver create(BidRequest bidRequest) {
        return noOp();
    }

    static TargetingKeywordsResolver noOp() {
        return new NoOpTargetingKeywordsResolver();
    }

    class NoOpTargetingKeywordsResolver implements TargetingKeywordsResolver {

        @Override
        public Map<String, String> resolve(Bid bid) {
            return Collections.emptyMap();
        }
    }
}
