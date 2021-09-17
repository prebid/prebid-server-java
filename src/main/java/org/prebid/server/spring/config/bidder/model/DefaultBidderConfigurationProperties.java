package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

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
}
