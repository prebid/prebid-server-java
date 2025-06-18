package org.prebid.server.hooks.modules.rule.engine.config;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Vertx;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.modules.rule.engine.core.cache.RuleRegistry;
import org.prebid.server.hooks.modules.rule.engine.core.config.AccountConfigParser;
import org.prebid.server.hooks.modules.rule.engine.core.config.StageConfigParser;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestMatchingRule;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestResultContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestSchemaContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestSpecification;
import org.prebid.server.hooks.modules.rule.engine.v1.RuleEngineModule;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadLocalRandom;

@Configuration
@ConditionalOnProperty(prefix = "hooks." + RuleEngineModule.CODE, name = "enabled", havingValue = "true")
public class RuleEngineModuleConfiguration {

    @Bean
    RuleEngineModule ruleEngineModule(RuleRegistry ruleRegistry) {
        return new RuleEngineModule(ruleRegistry);
    }

    @Bean
    StageConfigParser<RequestSchemaContext, BidRequest, RequestResultContext> processedAuctionRequestStageParser(
            BidderCatalog bidderCatalog,
            @Value("${datacenter-region:#{null}}") String datacenterRegion) {

        return new StageConfigParser<>(
                () -> ThreadLocalRandom.current().nextLong(),
                Stage.processed_auction_request,
                new RequestSpecification(ObjectMapperProvider.mapper(), bidderCatalog),
                (schema, ruleTree, modelVersion, analyticsKey) ->
                        new RequestMatchingRule(schema, ruleTree, modelVersion, analyticsKey, datacenterRegion));
    }

    @Bean
    AccountConfigParser accountConfigParser(
            StageConfigParser<
                    RequestSchemaContext, BidRequest, RequestResultContext> processedAuctionRequestStageParser) {

        return new AccountConfigParser(ObjectMapperProvider.mapper(), processedAuctionRequestStageParser);
    }

    @Bean
    RuleRegistry ruleRegistry(
            @Value("${hooks.rule-engine.rule-cache-expire-after-minutes}") long cacheExpireAfterMinutes,
            @Value("${hooks.rule-engine.rule-cache-max-size}") long cacheMaxSize,
            AccountConfigParser accountConfigParser,
            Vertx vertx) {

        return new RuleRegistry(cacheExpireAfterMinutes, cacheMaxSize, accountConfigParser, vertx);
    }
}
