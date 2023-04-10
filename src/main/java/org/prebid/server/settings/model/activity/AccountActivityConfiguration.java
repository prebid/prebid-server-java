package org.prebid.server.settings.model.activity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountActivityConfiguration {

    @JsonProperty("default")
    Boolean allow;

    List<AccountActivityRuleConfig> rules;
}
