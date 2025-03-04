package org.prebid.server.settings.model.activity.privacy;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountUSNatModuleConfig implements AccountPrivacyModuleConfig {

    @Accessors(fluent = true)
    Boolean enabled;

    int skipRate;

    Config config;

    @Override
    public PrivacyModuleQualifier getCode() {
        return PrivacyModuleQualifier.US_NAT;
    }

    @Value(staticConstructor = "of")
    public static class Config {

        @JsonProperty("skip_sids")
        @JsonAlias({"skipSids", "skip-sids"})
        List<Integer> skipSids;

        @JsonProperty("allow_personal_data_consent_2")
        @JsonAlias({"allowPersonalDataConsent2", "allow-personal-data-consent-2"})
        boolean allowPersonalDataConsent2;
    }
}
