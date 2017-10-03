package org.rtb.vexing.model.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class PreBidResponse {

    String tid;

    String status;

    // FIXME
    List<Bidder> bidderStatus;

    List<Bid> bids;

    String burl;
}
