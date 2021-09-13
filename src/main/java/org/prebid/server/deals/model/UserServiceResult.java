package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.cache.model.DebugHttpCall;

@AllArgsConstructor(staticName = "of")
@Value
public class UserServiceResult {

    UserDetails userDetails;

    DebugHttpCall cacheHttpCall;

    public static UserServiceResult empty() {
        return UserServiceResult.of(UserDetails.empty(), DebugHttpCall.empty());
    }
}
