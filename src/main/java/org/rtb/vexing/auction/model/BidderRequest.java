package org.rtb.vexing.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Structure to pass {@link BidRequest} along with the bidder name
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class BidderRequest {

    String bidder;

    BidRequest bidRequest;
}
