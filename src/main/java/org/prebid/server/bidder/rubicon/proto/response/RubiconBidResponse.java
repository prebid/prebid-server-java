package org.prebid.server.bidder.rubicon.proto.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Builder
@Value
public class RubiconBidResponse {

    String id;

    List<RubiconSeatBid> seatbid;

    String bidid;

    String cur;

    String customdata;

    Integer nbr;

    ObjectNode ext;

    public static final Comparator<BidResponse> COMPARATOR = (left, right) -> {
        if (Objects.isNull(left)
                || left.getSeatbid().isEmpty()
                || left.getSeatbid().get(0).getBid().isEmpty()
                || Objects.isNull(right)) {
            return -1;
        }
        if (right.getSeatbid().isEmpty() || right.getSeatbid().get(0).getBid().isEmpty()) {
            return 1;
        }
        return left.getSeatbid().get(0).getBid().get(0).getPrice()
                .compareTo(right.getSeatbid().get(0).getBid().get(0).getPrice());
    };
}
