package org.prebid.server.cache;

import com.iab.openrtb.response.Bid;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class CacheBid {

    Bid bid;

    Integer ttl;
}
