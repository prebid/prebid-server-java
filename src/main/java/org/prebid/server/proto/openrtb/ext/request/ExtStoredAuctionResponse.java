package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.SeatBid;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtStoredAuctionResponse {

    String id;

    @JsonProperty("seatbidarr")
    List<SeatBid> seatBids;

    @JsonProperty("seatbidobj")
    SeatBid seatBid;
}
