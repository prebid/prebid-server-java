package org.prebid.server.bidder.openx.proto;

import com.iab.openrtb.response.SeatBid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OpenxBidResponse {

    String id;

    List<SeatBid> seatbid;

    String cur;

    Integer nbr;

    OpenxBidResponseExt ext;
}
