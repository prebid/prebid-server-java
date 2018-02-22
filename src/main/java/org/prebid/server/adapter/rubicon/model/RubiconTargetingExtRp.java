package org.prebid.server.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconTargetingExtRp {

    List<RubiconTargeting> targeting;
}
