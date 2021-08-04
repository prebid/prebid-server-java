package org.prebid.server.spring.config.bidder.model;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.exception.PreBidException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Validated
@Data
public class CommonBidderConfigurationProperties implements InitializingBean {

    private Boolean enabled;

    private Boolean pbsEnforcesGdpr;

    private Boolean pbsEnforcesCcpa;

    private Boolean modifyingVastXmlAllowed;

    private List<String> deprecatedNames;

    private Map<String, Object> aliases = Collections.emptyMap();

    /**
     * Since this class is
     * inherited by {@see org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties}
     * it is not possible to use {@see javax.validation.constraints.NotNull} constraint for its fields.
     * Thus, manual fields checking is applied.
     * <p>
     * Note: Please, don't forget to enlist new fields in verification list.
     */
    @Override
    public void afterPropertiesSet() {
        if (getClass().isAssignableFrom(CommonBidderConfigurationProperties.class) && !ObjectUtils.allNotNull(
                enabled,
                pbsEnforcesGdpr,
                pbsEnforcesCcpa,
                modifyingVastXmlAllowed,
                deprecatedNames,
                aliases)) {

            throw new PreBidException("Adapter default properties missing or partially configured");
        }
    }
}
