package org.prebid.server.spring.config.bidder.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Data
@Validated
public class BidderConfigurationProperties {

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Autowired
    DefaultBidderConfigurationProperties defaultProperties;

    private Boolean enabled;

    private OrtbVersion ortbVersion;

    @NotBlank
    private String endpoint;

    private Boolean pbsEnforcesCcpa;

    private Boolean modifyingVastXmlAllowed;

    private List<String> deprecatedNames;

    private Map<String, Object> aliases;

    private Debug debug;

    @NotNull
    private MetaInfo metaInfo;

    private UsersyncConfigurationProperties usersync;

    private CompressionType endpointCompression;

    private Ortb ortb;

    private final Class<? extends BidderConfigurationProperties> selfClass;

    public BidderConfigurationProperties() {
        selfClass = this.getClass();
    }

    @PostConstruct
    private void init() {
        enabled = ObjectUtils.defaultIfNull(enabled, defaultProperties.getEnabled());
        ortbVersion = ObjectUtils.defaultIfNull(ortbVersion, defaultProperties.getOrtbVersion());
        pbsEnforcesCcpa = ObjectUtils.defaultIfNull(pbsEnforcesCcpa, defaultProperties.getPbsEnforcesCcpa());
        modifyingVastXmlAllowed = ObjectUtils.defaultIfNull(
                modifyingVastXmlAllowed, defaultProperties.getModifyingVastXmlAllowed());
        debug = ObjectUtils.defaultIfNull(debug, defaultProperties.getDebug());
        aliases = ObjectUtils.defaultIfNull(aliases, defaultProperties.getAliases());
        deprecatedNames = ObjectUtils.defaultIfNull(deprecatedNames, defaultProperties.getDeprecatedNames());
        endpointCompression = ObjectUtils.defaultIfNull(
                endpointCompression, defaultProperties.getEndpointCompression());
        ortb = ortb != null && ortb.getMultiFormatSupported() != null
                ? ortb
                : defaultProperties.getOrtb();

        if (usersync != null && usersync.getEnabled() == null) {
            usersync.setEnabled(true);
        }
    }
}
