package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    Boolean enabled;

    @JsonProperty("channel_enabled")
    @JsonAlias("channel-enabled")
    EnabledForRequestType enabledForRequestType;
}
