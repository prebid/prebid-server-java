package org.prebid.server.spring.config.bidder.postprocessor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.CommonBidderConfigurationProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

@Component
public class BidderConfigurationBeanPostProcessor implements BeanPostProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

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
                field.set(bean, addDefaultProperties((BidderConfigurationProperties) field.get(bean)));
            }
        });
        return bean;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BidderConfigurationProperties addDefaultProperties(BidderConfigurationProperties configProperties) {
        try {
            final JsonNode mergedNode = JsonMergePatch
                    .fromJson(MAPPER.valueToTree(configProperties))
                    .apply(MAPPER.valueToTree(commonBidderConfigurationProperties));

            return (BidderConfigurationProperties) MAPPER.treeToValue(mergedNode,
                    (Class) configProperties.getSelfClass());
        } catch (JsonPatchException | JsonProcessingException e) {
            throw new IllegalArgumentException("Exception occurred while merging common configuration", e);
        }
    }
}
