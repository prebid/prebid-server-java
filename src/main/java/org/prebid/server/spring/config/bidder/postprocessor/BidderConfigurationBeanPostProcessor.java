package org.prebid.server.spring.config.bidder.postprocessor;

import org.prebid.server.spring.config.bidder.model.CommonBidderConfigurationProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class BidderConfigurationBeanPostProcessor implements BeanPostProcessor {

    private final CommonBidderConfigurationProperties commonProperties;

    public BidderConfigurationBeanPostProcessor(CommonBidderConfigurationProperties commonProperties) {
        this.commonProperties = Objects.requireNonNull(commonProperties);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof CommonBidderConfigurationProperties) {
            final CommonBidderConfigurationProperties properties = (CommonBidderConfigurationProperties) bean;

            setProperty(properties, CommonBidderConfigurationProperties::getEnabled,
                    CommonBidderConfigurationProperties::setEnabled);
            setProperty(properties, CommonBidderConfigurationProperties::getPbsEnforcesCcpa,
                    CommonBidderConfigurationProperties::setPbsEnforcesCcpa);
            setProperty(properties, CommonBidderConfigurationProperties::getPbsEnforcesGdpr,
                    CommonBidderConfigurationProperties::setPbsEnforcesGdpr);
            setProperty(properties, CommonBidderConfigurationProperties::getModifyingVastXmlAllowed,
                    CommonBidderConfigurationProperties::setModifyingVastXmlAllowed);
            setProperty(properties, CommonBidderConfigurationProperties::getDeprecatedNames,
                    CommonBidderConfigurationProperties::setDeprecatedNames);
            setProperty(properties, CommonBidderConfigurationProperties::getAliases,
                    CommonBidderConfigurationProperties::setAliases);
        }

        return bean;
    }

    private <T, V> void setProperty(T target, Function<T, V> getter, BiConsumer<T, V> setter) {
        final V value = getter.apply(target);
        if (value == null) {
            @SuppressWarnings("unchecked") final V defaultValue = getter.apply((T) commonProperties);
            setter.accept(target, defaultValue);
        }
    }
}
