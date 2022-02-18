package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

@Value
@AllArgsConstructor(staticName = "of")
public class AccountMetricsConfig {

    @JsonProperty("verbosity-level")
    AccountMetricsVerbosityLevel verbosityLevel;
}
