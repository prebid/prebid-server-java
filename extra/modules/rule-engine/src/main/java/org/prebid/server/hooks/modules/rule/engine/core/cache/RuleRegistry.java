package org.prebid.server.hooks.modules.rule.engine.core.cache;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.rule.engine.core.config.AccountConfigParser;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class RuleRegistry {

    private final AccountConfigParser parser;
    private final Vertx vertx;


    private final ConcurrentMap<String, Future<PerStageRule>> accountIdToRules;

    public RuleRegistry(long cacheExpireAfterMinutes,
                        long cacheMaxSize,
                        AccountConfigParser parser,
                        Vertx vertx) {

        this.parser = Objects.requireNonNull(parser);
        this.vertx = Objects.requireNonNull(vertx);

        this.accountIdToRules = Caffeine.newBuilder()
                .expireAfterAccess(cacheExpireAfterMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .<String, Future<PerStageRule>>build()
                .asMap();
    }

    public Future<PerStageRule> forAccount(String accountId, ObjectNode config) {
        return accountIdToRules.computeIfAbsent(
                accountId, (ignored) -> vertx.executeBlocking(() -> parser.parse(config)));
    }
}
