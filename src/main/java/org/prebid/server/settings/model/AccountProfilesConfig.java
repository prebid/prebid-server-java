package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountProfilesConfig {

    Integer limit;

    @JsonAlias("fail-on-unknown")
    Boolean failOnUnknown;
}
