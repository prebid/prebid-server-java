package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AccountCcpaConfig {

    @JsonProperty("enabled")
    Boolean enabled;

    @JsonProperty("integration-enabled")
    EnabledForRequestType enabledForRequestType;
}
