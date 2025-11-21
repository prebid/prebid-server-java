package org.prebid.server.bidder.unruly.proto;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.unruly.ExtImpUnruly;

@Value(staticConstructor = "of")
public class UnrulyExtPrebid {

    ExtImpUnruly bidder;

    String gpid;
}
