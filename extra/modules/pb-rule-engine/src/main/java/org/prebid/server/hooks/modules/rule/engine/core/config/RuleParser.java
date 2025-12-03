package org.prebid.server.hooks.modules.rule.engine.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.execution.retry.RetryPolicy;
import org.prebid.server.execution.retry.Retryable;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RuleParser {

    private static final Logger logger = LoggerFactory.getLogger(RuleParser.class);

    private final AccountConfigParser parser;
    private final Vertx vertx;
    private final Clock clock;

    private final RetryPolicy retryPolicy;

    private final Map<String, ParsingAttempt> accountIdToParsingAttempt;
    private final Map<String, PerStageRule> accountIdToRules;

    public RuleParser(long cacheExpireAfterMinutes,
                      long cacheMaxSize,
                      RetryPolicy retryPolicy,
                      AccountConfigParser parser,
                      Vertx vertx,
                      Clock clock) {

        this.parser = Objects.requireNonNull(parser);
        this.vertx = Objects.requireNonNull(vertx);
        this.clock = Objects.requireNonNull(clock);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);

        this.accountIdToParsingAttempt = Caffeine.newBuilder()
                .expireAfterWrite(cacheExpireAfterMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .<String, ParsingAttempt>build()
                .asMap();

        this.accountIdToRules = Caffeine.newBuilder()
                .expireAfterWrite(cacheExpireAfterMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaxSize)
                .<String, PerStageRule>build()
                .asMap();
    }

    public Future<PerStageRule> parseForAccount(String accountId, ObjectNode config) {
        final PerStageRule cachedRule = accountIdToRules.get(accountId);

        if (cachedRule != null && cachedRule.timestamp().compareTo(getConfigTimestamp(config)) >= 0) {
            return Future.succeededFuture(cachedRule);
        }

        parseConfig(accountId, config);
        return Future.succeededFuture(ObjectUtils.defaultIfNull(cachedRule, PerStageRule.noOp()));
    }

    private Instant getConfigTimestamp(ObjectNode config) {
        try {
            return Optional.of(config)
                    .map(node -> node.get("timestamp"))
                    .filter(JsonNode::isTextual)
                    .map(JsonNode::asText)
                    .map(Instant::parse)
                    .orElse(Instant.EPOCH);
        } catch (DateTimeParseException exception) {
            return Instant.EPOCH;
        }
    }

    private void parseConfig(String accountId, ObjectNode config) {
        final Instant now = clock.instant();
        final ParsingAttempt attempt = accountIdToParsingAttempt.compute(
                accountId, (ignored, previousAttempt) -> tryRegisteringNewAttempt(previousAttempt, now));

        // reference equality used on purpose - if references are equal - then we should parse
        if (attempt.timestamp() == now) {
            logger.info("Parsing rule for account {}", accountId);
            vertx.executeBlocking(() -> parser.parse(config))
                    .onSuccess(result -> succeedParsingAttempt(accountId, result))
                    .onFailure(error -> failParsingAttempt(accountId, attempt, error));
        }
    }

    private ParsingAttempt tryRegisteringNewAttempt(ParsingAttempt previousAttempt, Instant currentAttemptStart) {
        if (previousAttempt == null) {
            return new ParsingAttempt.InProgress(currentAttemptStart, retryPolicy);
        }

        if (previousAttempt instanceof ParsingAttempt.InProgress) {
            return previousAttempt;
        }

        if (previousAttempt.retryPolicy() instanceof Retryable previousAttemptRetryPolicy) {
            final Instant previouslyDecidedToRetryAfter = previousAttempt.timestamp().plus(
                    Duration.ofMillis(previousAttemptRetryPolicy.delay()));

            return previouslyDecidedToRetryAfter.isBefore(currentAttemptStart)
                    ? new ParsingAttempt.InProgress(currentAttemptStart, previousAttemptRetryPolicy.next())
                    : previousAttempt;
        }

        return previousAttempt;
    }

    private void succeedParsingAttempt(String accountId, PerStageRule result) {
        accountIdToRules.put(accountId, result);
        accountIdToParsingAttempt.remove(accountId);

        logger.debug("Successfully parsed rule-engine config for account {}", accountId);
    }

    private void failParsingAttempt(String accountId, ParsingAttempt attempt, Throwable cause) {
        accountIdToParsingAttempt.put(accountId, ((ParsingAttempt.InProgress) attempt).failed());

        logger.error(
                "Failed to parse rule-engine config for account %s: %s".formatted(accountId, cause.getMessage()),
                cause);
    }

    private sealed interface ParsingAttempt {

        Instant timestamp();

        RetryPolicy retryPolicy();

        record Failed(Instant timestamp, RetryPolicy retryPolicy) implements ParsingAttempt {
        }

        record InProgress(Instant timestamp, RetryPolicy retryPolicy) implements ParsingAttempt {

            public Failed failed() {
                return new Failed(timestamp, retryPolicy);
            }
        }
    }
}
