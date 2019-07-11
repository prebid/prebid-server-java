package org.prebid.server.bidder.unruly.proto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.unruly.ExtImpUnruly;

@Value
@AllArgsConstructor(staticName = "of")
public class ImpExtUnruly {

    ExtImpUnruly unruly;
}
