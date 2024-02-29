package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.activity.Activity;
import org.prebid.server.settings.model.activity.AccountActivityConfiguration;
import org.prebid.server.settings.model.activity.privacy.AccountPrivacyModuleConfig;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class AccountPrivacyConfig {

    AccountGdprConfig gdpr;

    AccountCcpaConfig ccpa;

    AccountDsaConfig dsa;

    @JsonProperty("allowactivities")
    Map<Activity, AccountActivityConfiguration> activities;

    List<AccountPrivacyModuleConfig> modules;
}
