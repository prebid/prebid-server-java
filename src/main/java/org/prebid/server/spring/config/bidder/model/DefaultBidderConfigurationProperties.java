package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Validated
@Data
public class DefaultBidderConfigurationProperties {

    @NotNull
    private Boolean enabled;

    @NotNull
    private Boolean pbsEnforcesGdpr;

    @NotNull
    private Boolean pbsEnforcesCcpa;

    @NotNull
    private Boolean modifyingVastXmlAllowed;

    @NotNull
    private List<String> deprecatedNames;

    @NotNull
    private Map<String, Object> aliases = Collections.emptyMap();
}
