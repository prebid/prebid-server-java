package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountDsaConfig {

    @JsonProperty("default")
    DefaultDsa defaultDsa;

    @JsonAlias("gdpr-only")
    Boolean gdprOnly;
}
