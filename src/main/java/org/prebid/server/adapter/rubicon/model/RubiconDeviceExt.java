package org.prebid.server.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconDeviceExt {

    RubiconDeviceExtRp rp;
}
