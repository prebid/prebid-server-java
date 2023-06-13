package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.activity.Activity;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;

import java.util.Map;

@Value(staticConstructor = "of")
public class AccountPrivacyConfig {

    AccountGdprConfig gdpr;

    AccountCcpaConfig ccpa;

    @JsonProperty("allowactivities")
    Map<Activity, AccountActivityConfiguration> activities;
}
