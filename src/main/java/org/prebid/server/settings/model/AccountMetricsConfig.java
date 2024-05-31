package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

@Value(staticConstructor = "of")
public class AccountMetricsConfig {

    @JsonAlias("verbosity-level")
    AccountMetricsVerbosityLevel verbosityLevel;
}
