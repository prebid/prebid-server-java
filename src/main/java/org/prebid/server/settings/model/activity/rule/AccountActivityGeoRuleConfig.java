package org.prebid.server.settings.model.activity.rule;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.activity.ComponentType;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountActivityGeoRuleConfig implements AccountActivityRuleConfig {

    Condition condition;

    Boolean allow;

    @Value(staticConstructor = "of")
    public static class Condition {

        @JsonProperty("componentType")
        List<ComponentType> componentTypes;

        @JsonProperty("componentName")
        List<String> componentNames;

        @JsonProperty("gppSid")
        List<Integer> sids;

        @JsonProperty("geo")
        List<String> geoCodes;

        String gpc;
    }
}
