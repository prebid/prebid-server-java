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
                name = PrivacyModuleQualifier.Names.US_NAT),
        @JsonSubTypes.Type(
                value = AccountUSCustomLogicModuleConfig.class,
                name = PrivacyModuleQualifier.Names.US_CUSTOM_LOGIC)})
public sealed interface AccountPrivacyModuleConfig permits
        AccountUSNatModuleConfig,
        AccountUSCustomLogicModuleConfig {

    PrivacyModuleQualifier getCode();

    @JsonProperty("skipRate")
    Integer getSkipRate();

    @JsonProperty
    Boolean enabled();
}
