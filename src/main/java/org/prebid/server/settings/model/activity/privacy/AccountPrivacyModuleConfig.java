package org.prebid.server.settings.model.activity.privacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModuleQualifier;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "code",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(
                value = AccountUSNatModuleConfig.class,
                name = PrivacyModuleQualifier.Names.US_NAT)})
public sealed interface AccountPrivacyModuleConfig permits AccountUSNatModuleConfig {

    PrivacyModuleQualifier getCode();

    @JsonProperty
    Boolean enabled();
}
