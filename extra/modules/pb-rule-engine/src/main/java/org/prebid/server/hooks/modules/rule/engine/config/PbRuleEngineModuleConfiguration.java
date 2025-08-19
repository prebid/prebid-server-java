package org.prebid.server.hooks.modules.rule.engine.config;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Vertx;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.execution.retry.ExponentialBackoffRetryPolicy;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.config.AccountConfigParser;
import org.prebid.server.hooks.modules.rule.engine.core.config.RuleParser;
import org.prebid.server.hooks.modules.rule.engine.core.config.StageConfigParser;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestConditionalRuleFactory;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestStageSpecification;
import org.prebid.server.hooks.modules.rule.engine.v1.PbRuleEngineModule;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

@Configuration
@ConditionalOnProperty(prefix = "hooks." + PbRuleEngineModule.CODE, name = "enabled", havingValue = "true")
public class PbRuleEngineModuleConfiguration {

    @Bean
    PbRuleEngineModule ruleEngineModule(RuleParser ruleParser,
                                        @Value("${datacenter-region:#{null}}") String datacenter) {

        return new PbRuleEngineModule(ruleParser, datacenter);
    }

    @Bean
    StageConfigParser<BidRequest, RequestRuleContext> processedAuctionRequestStageParser(
            BidderCatalog bidderCatalog) {
        final RandomGenerator randomGenerator = () -> ThreadLocalRandom.current().nextLong();

        return new StageConfigParser<>(
                randomGenerator,
                Stage.processed_auction_request,
                new RequestStageSpecification(ObjectMapperProvider.mapper(), bidderCatalog, randomGenerator),
                new RequestConditionalRuleFactory());
    }

    @Bean
    AccountConfigParser accountConfigParser(
            StageConfigParser<BidRequest, RequestRuleContext> processedAuctionRequestStageParser) {

        return new AccountConfigParser(ObjectMapperProvider.mapper(), processedAuctionRequestStageParser);
    }

    @Bean
    RuleParser ruleParser(
            @Value("${hooks.pb-rule-engine.rule-cache.expire-after-minutes}") long cacheExpireAfterMinutes,
            @Value("${hooks.pb-rule-engine.rule-cache.max-size}") long cacheMaxSize,
            @Value("${hooks.pb-rule-engine.rule-parsing.retry-initial-delay-millis}") long delay,
            @Value("${hooks.pb-rule-engine.rule-parsing.retry-max-delay-millis}") long maxDelay,
            @Value("${hooks.pb-rule-engine.rule-parsing.retry-exponential-factor}") double factor,
            @Value("${hooks.pb-rule-engine.rule-parsing.retry-exponential-jitter}") double jitter,
            AccountConfigParser accountConfigParser,
            Vertx vertx,
            Clock clock) {

        return new RuleParser(
                cacheExpireAfterMinutes,
                cacheMaxSize,
                ExponentialBackoffRetryPolicy.of(delay, maxDelay, factor, jitter),
                accountConfigParser,
                vertx,
                clock);
    }
}
