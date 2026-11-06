package org.prebid.server.bidder.peak226.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value(staticConstructor = "of")
public class Peak226Response {

    @JsonProperty("cur")
    String cur;

    @JsonProperty("seatbid")
    List<Peak226SeatBid> seatbid;

}
