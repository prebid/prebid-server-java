package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class RocCurve {

    String pbuid;

    Thresholds thresholds;
}
