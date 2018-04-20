package org.prebid.server.bidder.sovrn.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class SovrnParams {

    String tagId;

    Float bidfloor;
}
