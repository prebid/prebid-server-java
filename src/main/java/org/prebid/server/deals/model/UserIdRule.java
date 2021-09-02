package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class UserIdRule {

    @NonNull
    String type;

    @NonNull
    String source;

    @NonNull
    String location;
}
