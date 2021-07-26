package org.prebid.server.spring.config.bidder.postprocessor;

import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.CommonBidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.util.BidderConfigurationMerger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

public class BidderConfigurationBeanPostProcessor implements BeanPostProcessor {

    private final CommonBidderConfigurationProperties commonBidderConfigurationProperties;

    public BidderConfigurationBeanPostProcessor(
            CommonBidderConfigurationProperties commonBidderConfigurationProperties) {
        this.commonBidderConfigurationProperties = commonBidderConfigurationProperties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (BidderConfigurationProperties.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                field.set(bean, BidderConfigurationMerger.mergeConfigurationWithCommon(
                        (BidderConfigurationProperties) field.get(bean), commonBidderConfigurationProperties));
            }
        });
        return bean;
    }
}
