package org.prebid.server.spring.config;

import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.activity.infrastructure.creator.ActivityRuleFactory;
import org.prebid.server.activity.infrastructure.creator.rule.ComponentRuleCreator;
import org.prebid.server.activity.infrastructure.creator.rule.GppSidRuleCreator;
import org.prebid.server.activity.infrastructure.creator.rule.RuleCreator;
import org.prebid.server.metric.Metrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ActivityInfrastructureConfiguration {

    @Bean
    GppSidRuleCreator gppSidRuleCreator() {
        return new GppSidRuleCreator();
    }

    @Bean
    ComponentRuleCreator componentRuleCreator() {
        return new ComponentRuleCreator();
    }

    @Bean
    ActivityRuleFactory activityRuleFactory(List<RuleCreator<?>> ruleCreators) {
        return new ActivityRuleFactory(ruleCreators);
    }

    @Bean
    ActivityInfrastructureCreator activityInfrastructureCreator(ActivityRuleFactory activityRuleFactory,
                                                                Metrics metrics) {

        return new ActivityInfrastructureCreator(activityRuleFactory, metrics);
    }
}
