package org.prebid.server.settings.model.activity.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountActivityAppliedPrivacyModulesRuleConfig implements AccountActivityRuleConfig {

    @JsonProperty("privacyreg")
    List<String> appliedPrivacyModules;
}
