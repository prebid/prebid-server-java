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

    Integer skipRate;

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
    }
}
