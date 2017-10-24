package org.rtb.vexing.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderDebug;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class BidResult {

    Bid.BidBuilder bidBuilder;

    BidderDebug bidderDebug;
}
