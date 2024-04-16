package org.prebid.server.bidder.medianet.model.response;

import com.iab.openrtb.response.SeatBid;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MedianetBidResponse {

    String id;

    List<SeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    MedianetBidResponseExt ext;
}
