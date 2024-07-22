package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountSettings {

    @JsonAlias("geo-lookup")
    Boolean geoLookup;
}
