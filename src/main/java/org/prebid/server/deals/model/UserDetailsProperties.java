package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class UserDetailsProperties {

    @NonNull
    String userDetailsEndpoint;

    @NonNull
    String winEventEndpoint;

    long timeout;

    @NonNull
    List<UserIdRule> userIds;
}
