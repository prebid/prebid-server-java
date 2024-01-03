package org.prebid.server.bidder.ix.model.response;

import com.iab.openrtb.response.SeatBid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class IxBidResponse {

    String id;

    List<SeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    IxExtBidResponse ext;
}
