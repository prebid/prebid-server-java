package org.prebid.server.bidder;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class BidderInstanceDebugDeps {
    
    Boolean enabled;
}
