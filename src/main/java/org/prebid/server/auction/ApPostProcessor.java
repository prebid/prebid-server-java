package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.response.AmpResponse;
import org.prebid.server.settings.model.Account;

import javax.management.ObjectName;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ApPostProcessor implements BidResponsePostProcessor {

    public ApPostProcessor()
    {
    System.out.println("in constructor");
    }

    @Override
    public Future<BidResponse> postProcess(HttpRequestContext httpRequest, UidsCookie uidsCookie,
                                           BidRequest bidRequest, BidResponse bidResponse, Account account) {

        List<SeatBid> seatBids = bidResponse.getSeatbid();
        if(!seatBids.isEmpty()) {
            seatBids.forEach(seatBid -> {
                try {
                    modifyBidTargeting(seatBid);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }

            });
        }


        return Future.succeededFuture(bidResponse);
    }


    public SeatBid modifyBidTargeting(SeatBid ogseatBid) throws JsonProcessingException {
        ogseatBid.getBid().forEach(bid -> {
            JsonObject newPrebid = JsonObject.mapFrom(bid.getExt().get("prebid"));
            JsonObject targeting = newPrebid.getJsonObject("targeting");
            targeting.put("ap_hb_pb",targeting.getValue("hb_pb"));
            targeting.remove("hb_pb");
            targeting.put("ap_hb_bidder",targeting.getValue("hb_bidder"));
            targeting.remove("hb_bidder");
            targeting.put("ap_hb_size",targeting.getValue("hb_size"));
            targeting.remove("hb_size");

            JsonNode newPrebidNode = null;
            try {
                newPrebidNode = new ObjectMapper().readTree(newPrebid.toString());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            bid.getExt().replace("prebid",newPrebidNode);


        });

        return ogseatBid;
    }


}
