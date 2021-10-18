package org.prebid.server.spring.config.bidder.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
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

    @NotBlank
    private String endpoint;

    private Boolean pbsEnforcesCcpa;

    private Boolean modifyingVastXmlAllowed;

    private List<String> deprecatedNames;

    private Map<String, Object> aliases;

    private Debug debug;

    @NotNull
    private MetaInfo metaInfo;

    @NotNull
    private UsersyncConfigurationProperties usersync;

    private Map<String, String> extraInfo;

    private final Class<? extends BidderConfigurationProperties> selfClass;

    public BidderConfigurationProperties() {
        selfClass = this.getClass();
    }

    @PostConstruct
    private void init() {
        enabled = ObjectUtils.defaultIfNull(enabled, defaultProperties.getEnabled());
        pbsEnforcesCcpa = ObjectUtils.defaultIfNull(pbsEnforcesCcpa, defaultProperties.getPbsEnforcesCcpa());
        modifyingVastXmlAllowed = ObjectUtils.defaultIfNull(modifyingVastXmlAllowed,
                defaultProperties.getModifyingVastXmlAllowed());
        debug = ObjectUtils.defaultIfNull(debug, defaultProperties.getDebug());
        aliases = ObjectUtils.defaultIfNull(aliases, defaultProperties.getAliases());
        deprecatedNames = ObjectUtils.defaultIfNull(deprecatedNames, defaultProperties.getDeprecatedNames());
        extraInfo = ObjectUtils.defaultIfNull(extraInfo, defaultProperties.getExtraInfo());
    }
}
