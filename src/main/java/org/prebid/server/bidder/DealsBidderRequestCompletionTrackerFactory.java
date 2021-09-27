package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.BidderBid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DealsBidderRequestCompletionTrackerFactory implements BidderRequestCompletionTrackerFactory {

    public BidderRequestCompletionTracker create(BidRequest bidRequest) {
        final Map<String, String> impToTopDealMap = new HashMap<>();
        for (final Imp imp : bidRequest.getImp()) {
            final Pmp pmp = imp.getPmp();
            final List<Deal> deals = pmp != null ? pmp.getDeals() : null;
            final Deal topDeal = CollectionUtils.isNotEmpty(deals) ? deals.get(0) : null;

            impToTopDealMap.put(imp.getId(), topDeal != null ? topDeal.getId() : null);
        }

        return !impToTopDealMap.containsValue(null)
                ? new TopDealsReceivedTracker(impToTopDealMap)
                : new NeverCompletedTracker();
    }

    private static class NeverCompletedTracker implements BidderRequestCompletionTracker {

        @Override
        public Future<Void> future() {
            return Future.failedFuture("No deals to wait for");
        }

        @Override
        public void processBids(List<BidderBid> bids) {
            // no need to process bid when no deals to wait for
        }
    }

    private static class TopDealsReceivedTracker implements BidderRequestCompletionTracker {

        private final Map<String, String> impToTopDealMap;

        private final Promise<Void> completionPromise;

        private TopDealsReceivedTracker(Map<String, String> impToTopDealMap) {
            this.impToTopDealMap = new HashMap<>(impToTopDealMap);
            this.completionPromise = Promise.promise();
        }

        @Override
        public Future<Void> future() {
            return completionPromise.future();
        }

        @Override
        public void processBids(List<BidderBid> bids) {
            if (completionPromise.future().isComplete()) {
                return;
            }

            bids.stream()
                    .map(BidderBid::getBid)
                    .filter(Objects::nonNull)
                    .map(this::toImpIdIfTopDeal)
                    .filter(Objects::nonNull)
                    .forEach(impToTopDealMap::remove);

            if (impToTopDealMap.isEmpty()) {
                completionPromise.tryComplete();
            }
        }

        private String toImpIdIfTopDeal(Bid bid) {
            final String impId = bid.getImpid();
            final String dealId = bid.getDealid();
            if (StringUtils.isNoneBlank(impId, dealId)) {
                final String topDealForImp = impToTopDealMap.get(impId);
                if (topDealForImp != null && Objects.equals(dealId, topDealForImp)) {
                    return impId;
                }
            }

            return null;
        }
    }
}
