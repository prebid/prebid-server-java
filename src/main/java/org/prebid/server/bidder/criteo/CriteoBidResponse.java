package org.prebid.server.bidder.criteo;

import com.iab.openrtb.response.SeatBid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class CriteoBidResponse {

    String id;

    List<SeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    CriteoExtBidResponse ext;

}
