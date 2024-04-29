package org.prebid.server.spring.config;

import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.activity.infrastructure.creator.ActivityRuleFactory;
import org.prebid.server.activity.infrastructure.creator.privacy.PrivacyModuleCreator;
import org.prebid.server.activity.infrastructure.creator.privacy.uscustomlogic.USCustomLogicGppReaderFactory;
import org.prebid.server.activity.infrastructure.creator.privacy.uscustomlogic.USCustomLogicModuleCreator;
import org.prebid.server.activity.infrastructure.creator.privacy.usnat.USNatGppReaderFactory;
import org.prebid.server.activity.infrastructure.creator.privacy.usnat.USNatModuleCreator;
import org.prebid.server.activity.infrastructure.creator.rule.ComponentRuleCreator;
import org.prebid.server.activity.infrastructure.creator.rule.GeoRuleCreator;
import org.prebid.server.activity.infrastructure.creator.rule.PrivacyModulesRuleCreator;
import org.prebid.server.activity.infrastructure.creator.rule.RuleCreator;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonLogic;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.GdprConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ActivityInfrastructureConfiguration {

    @Configuration
    static class PrivacyModuleCreatorConfiguration {

        @Configuration
        static class USNatModuleCreatorConfiguration {

            @Bean
            USNatGppReaderFactory usNatGppReaderFactory() {
                return new USNatGppReaderFactory();
            }

            @Bean
            USNatModuleCreator usNatModuleCreator(USNatGppReaderFactory gppReaderFactory) {
                return new USNatModuleCreator(gppReaderFactory);
            }
        }

        @Configuration
        static class USCustomLogicModuleCreatorConfiguration {

            @Bean
            USCustomLogicGppReaderFactory usCustomLogicGppReaderFactory() {
                return new USCustomLogicGppReaderFactory();
            }

            @Bean
            USCustomLogicModuleCreator usCustomLogicModuleCreator(
                    USCustomLogicGppReaderFactory gppReaderFactory,
                    JsonLogic jsonLogic,
                    @Value("${settings.in-memory-cache.ttl-seconds:#{null}}") Integer ttlSeconds,
                    @Value("${settings.in-memory-cache.cache-size:#{null}}") Integer cacheSize,
                    Metrics metrics) {

                return new USCustomLogicModuleCreator(gppReaderFactory, jsonLogic, ttlSeconds, cacheSize, metrics);
            }
        }
    }

    @Configuration
    static class RuleCreatorConfiguration {

        @Bean
        ComponentRuleCreator componentRuleCreator() {
            return new ComponentRuleCreator();
        }

        @Bean
        GeoRuleCreator geoRuleCreator() {
            return new GeoRuleCreator();
        }

        @Bean
        PrivacyModulesRuleCreator privacyModulesRuleCreator(List<PrivacyModuleCreator> privacyModuleCreators) {
            return new PrivacyModulesRuleCreator(privacyModuleCreators);
        }
    }

    @Bean
    ActivityRuleFactory activityRuleFactory(List<RuleCreator<?>> ruleCreators) {
        return new ActivityRuleFactory(ruleCreators);
    }

    @Bean
    ActivityInfrastructureCreator activityInfrastructureCreator(ActivityRuleFactory activityRuleFactory,
                                                                GdprConfig gdprConfig,
                                                                Metrics metrics,
                                                                JacksonMapper jacksonMapper) {

        return new ActivityInfrastructureCreator(activityRuleFactory, gdprConfig, metrics, jacksonMapper);
    }
}
