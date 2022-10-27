package org.prebid.server.analytics.reporter.feedback;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedbackAnalyticsReporter  implements AnalyticsReporter {
    @Override
    public <T> Future<Void> processEvent(T event) {

        if (event instanceof AuctionEvent auctionEvent) {

             List<Imp> bidRequests = auctionEvent.getAuctionContext().getBidRequest().getImp();

            Map<String, String> requestProperties = new HashMap<>();
            try {
                bidRequests.forEach(bid -> {
                    requestProperties.put(bid.getId(), String.valueOf(bid.getExt().get("context").get("data").get("adslot")));
                });

                auctionEvent.getBidResponse().getSeatbid().forEach(seatBid -> {
                    Bid bid = seatBid.getBid().get(0);
                    String impId = bid.getImpid();
                    String networkAdUnit = requestProperties.get(impId);
                    Feedback fb = new Feedback(networkAdUnit, seatBid.getSeat(), seatBid.getBid().get(0).getPrice());
                    fb.sendFeedback();
                });
            }
            catch (Exception ex)
            {
                ex.getMessage();
            }


        }


        //add dat
        return Future.succeededFuture();
    }

    @Override
    public int vendorId() {
        return 0;
    }

    @Override
    public String name() {
        return "Feedback Analytics Reporter!";

    }
}
