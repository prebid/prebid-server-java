package org.prebid.server.deals.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class UserDetails {

    private static final UserDetails EMPTY = UserDetails.of(null, null);

    List<UserData> userData;

    List<String> fcapIds;

    public static UserDetails empty() {
        return EMPTY;
    }
}
