package org.prebid.server.floors.proto;

import lombok.Value;
import org.prebid.server.floors.model.PriceFloorData;

@Value(staticConstructor = "of")
public class FetchResult {

    PriceFloorData rulesData;

    FetchStatus fetchStatus;
}
