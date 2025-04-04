package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RubiconTargetingExtRp {

    List<RubiconTargeting> targeting;
}
