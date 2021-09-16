package org.prebid.server.bidder.smaato.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class SmaatoBidExt {

    Integer duration;
}
