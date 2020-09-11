package org.prebid.server.bidder.rubicon.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconUserExtDataPpuid {

    String type;

    String id;
}
