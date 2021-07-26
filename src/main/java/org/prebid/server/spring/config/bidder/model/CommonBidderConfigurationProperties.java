package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Validated
@Data
public class CommonBidderConfigurationProperties {

    private Boolean enabled;

    private Boolean pbsEnforcesGdpr;

    private Boolean pbsEnforcesCcpa;

    private Boolean modifyingVastXmlAllowed;

    private List<String> deprecatedNames;

    private Map<String, Object> aliases = Collections.emptyMap();
}
