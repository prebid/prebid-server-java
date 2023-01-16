package org.prebid.server.proto.openrtb.ext.response.seatnonbid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SeatNonBid {

    String seat;

    @JsonProperty("nonbid")
    List<NonBid> nonBid;
}
