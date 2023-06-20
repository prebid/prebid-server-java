package org.prebid.server.settings.model.activity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Value;
import org.prebid.server.json.deserializer.AccountActivityRulesConfigDeserializer;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountActivityConfiguration {

    @JsonProperty("default")
    Boolean allow;

    @JsonDeserialize(using = AccountActivityRulesConfigDeserializer.class)
    List<AccountActivityRuleConfig> rules;
}
