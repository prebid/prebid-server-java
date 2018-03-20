package org.prebid.server.bidder.sovrn.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpSovrn {

    String tagid;

    Float bidfloor;
}
