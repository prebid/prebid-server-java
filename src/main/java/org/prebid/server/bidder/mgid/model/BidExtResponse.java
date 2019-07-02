package org.prebid.server.bidder.mgid.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class BidExtResponse {

    String crtype;
}
