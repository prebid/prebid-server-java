package org.prebid.server.floors.proto;

import lombok.Value;
import org.prebid.server.floors.model.PriceFloorData;

@Value(staticConstructor = "of")
public class FetchResult {

    PriceFloorData rulesData;

    FetchStatus fetchStatus;

    String errorMessage;

    public static FetchResult none(String errorMessage) {
        return FetchResult.of(null, FetchStatus.none, errorMessage);
    }

    public static FetchResult error(String errorMessage) {
        return FetchResult.of(null, FetchStatus.error, errorMessage);
    }

    public static FetchResult inProgress() {
        return FetchResult.of(null, FetchStatus.inprogress, null);
    }
}
