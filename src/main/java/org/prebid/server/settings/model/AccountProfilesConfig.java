package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountProfilesConfig {

    Integer limit;

    // TODO: need confirmation to move property
    // TODO: Decide
    //      - skip all profiles on any invalid
    //      - skip only invalid
    // TODO: metrics
    @JsonAlias("fail-on-unknown")
    Boolean failOnUnknown;
}
