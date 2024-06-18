package org.prebid.server.proto.openrtb.ext.request;

import com.iab.openrtb.response.SeatBid;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtStoredAuctionResponse {

    String id;

    List<SeatBid> seatbid;
}
