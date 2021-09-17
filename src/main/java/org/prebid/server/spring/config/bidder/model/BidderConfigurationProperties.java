package org.prebid.server.spring.config.bidder.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
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

    private Boolean pbsEnforcesGdpr;

    private Boolean pbsEnforcesCcpa;

    private Boolean modifyingVastXmlAllowed;

    private List<String> deprecatedNames;

    private Map<String, Object> aliases;

    private Map<String, String> extraInfo;

    @NotBlank
    private String endpoint;

    @NotNull
    private MetaInfo metaInfo;

    @NotNull
    private UsersyncConfigurationProperties usersync;

    private final Class<? extends BidderConfigurationProperties> selfClass;

    public BidderConfigurationProperties() {
        selfClass = this.getClass();
    }

    @PostConstruct
    private void init() {
        enabled = ObjectUtils.defaultIfNull(enabled, defaultProperties.getEnabled());
        pbsEnforcesGdpr = ObjectUtils.defaultIfNull(pbsEnforcesGdpr, defaultProperties.getPbsEnforcesGdpr());
        pbsEnforcesCcpa = ObjectUtils.defaultIfNull(pbsEnforcesCcpa, defaultProperties.getPbsEnforcesCcpa());
        modifyingVastXmlAllowed = ObjectUtils.defaultIfNull(modifyingVastXmlAllowed,
                defaultProperties.getModifyingVastXmlAllowed());
        deprecatedNames = ObjectUtils.defaultIfNull(deprecatedNames, defaultProperties.getDeprecatedNames());
        aliases = MapUtils.isNotEmpty(aliases) ? aliases : defaultProperties.getAliases();
        extraInfo = MapUtils.isNotEmpty(extraInfo) ? extraInfo : defaultProperties.getExtraInfo();
    }
}
