package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountPrivacyConfig {

    @JsonProperty("enforce-ccpa")
    Boolean enforceCcpa;

    AccountGdprConfig gdpr;

    AccountCcpaConfig ccpa;
}
