package org.prebid.server.floors.proto;

import lombok.Value;
import org.prebid.server.floors.model.PriceFloorRules;

@Value(staticConstructor = "of")
public class FetchResult {

    PriceFloorRules rules;

    FetchStatus fetchStatus;
}
