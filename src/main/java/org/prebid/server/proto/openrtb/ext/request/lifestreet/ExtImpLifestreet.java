package org.prebid.server.proto.openrtb.ext.request.lifestreet;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpLifestreet {

    String slotTag;
}
