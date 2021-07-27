package org.prebid.server.spring.config.bidder.postprocessor;

import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.CommonBidderConfigurationProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;

public class BidderConfigurationBeanPostProcessor implements BeanPostProcessor {

    private final CommonBidderConfigurationProperties commonProperties;

    public BidderConfigurationBeanPostProcessor(CommonBidderConfigurationProperties commonProperties) {
        this.commonProperties = Objects.requireNonNull(commonProperties);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BidderConfigurationProperties) {
            final BidderConfigurationProperties properties = (BidderConfigurationProperties) bean;

            properties.setEnabled(ObjectUtils.defaultIfNull(properties.getEnabled(), commonProperties.getEnabled()));
            properties.setPbsEnforcesGdpr(ObjectUtils.defaultIfNull(properties.getPbsEnforcesGdpr(),
                    commonProperties.getPbsEnforcesGdpr()));
            properties.setPbsEnforcesCcpa(ObjectUtils.defaultIfNull(properties.getPbsEnforcesCcpa(),
                    commonProperties.getPbsEnforcesCcpa()));
            properties.setModifyingVastXmlAllowed(ObjectUtils.defaultIfNull(properties.getModifyingVastXmlAllowed(),
                    commonProperties.getModifyingVastXmlAllowed()));
            properties.setDeprecatedNames(ObjectUtils.defaultIfNull(properties.getDeprecatedNames(),
                    commonProperties.getDeprecatedNames()));
            properties.setAliases(ObjectUtils.defaultIfNull(properties.getAliases(), commonProperties.getAliases()));
        }

        return bean;
    }
}
