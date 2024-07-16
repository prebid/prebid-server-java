package org.prebid.server.settings.model.activity.rule;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.activity.ComponentType;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountActivityConditionsRuleConfig implements AccountActivityRuleConfig {

    Condition condition;

    Boolean allow;

    @Value(staticConstructor = "of")
    public static class Condition {

        @JsonProperty("component_type")
        @JsonAlias({"componentType", "component-type"})
        List<ComponentType> componentTypes;

        @JsonProperty("component_name")
        @JsonAlias({"componentName", "component-name"})
        List<String> componentNames;

        @JsonProperty("gpp_sid")
        @JsonAlias({"gppSid", "gpp-sid"})
        List<Integer> sids;

        @JsonProperty("geo")
        List<String> geoCodes;

        String gpc;
    }
}
