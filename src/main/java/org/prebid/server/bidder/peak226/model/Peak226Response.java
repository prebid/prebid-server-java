package org.prebid.server.bidder.peak226.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Peak226Response {

    @JsonProperty("cur")
    String cur;

    @JsonProperty("seatbid")
    List<Peak226SeatBid> seatbid;

}
