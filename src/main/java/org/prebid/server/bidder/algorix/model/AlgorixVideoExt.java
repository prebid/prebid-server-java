package org.prebid.server.bidder.algorix.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class AlgorixVideoExt {

    Integer rewarded;
}
