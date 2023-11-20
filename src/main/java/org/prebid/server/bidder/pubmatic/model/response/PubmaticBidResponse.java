package org.prebid.server.bidder.pubmatic.model.response;

import com.iab.openrtb.response.SeatBid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class PubmaticBidResponse {

    String id;

    List<SeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    PubmaticExtBidResponse ext;

}
