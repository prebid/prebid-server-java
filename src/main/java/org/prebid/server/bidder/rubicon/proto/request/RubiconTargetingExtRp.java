package org.prebid.server.bidder.rubicon.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconTargetingExtRp {

    List<RubiconTargeting> targeting;
}
