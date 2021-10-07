package org.prebid.server.settings.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class AccountPrivacyConfig {

    AccountGdprConfig gdpr;

    AccountCcpaConfig ccpa;
}
