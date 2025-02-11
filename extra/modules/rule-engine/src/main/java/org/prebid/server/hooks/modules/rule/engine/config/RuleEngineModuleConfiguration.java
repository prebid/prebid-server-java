package org.prebid.server.hooks.modules.rule.engine.config;

import org.prebid.server.hooks.modules.rule.engine.v1.RuleEngineModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "hooks." + RuleEngineModule.CODE, name = "enabled", havingValue = "true")
public class RuleEngineModuleConfiguration {

    @Bean
    RuleEngineModule ruleEngineModule() {
        return new RuleEngineModule();
    }
}
