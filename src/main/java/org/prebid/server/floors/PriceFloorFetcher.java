package org.prebid.server.floors;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class PriceFloorFetcher {

    private static final Logger logger = LoggerFactory.getLogger(org.prebid.server.floors.PriceFloorFetcher.class);

    private static final int BYTES_IN_KBYTES = 1024;

    private final long defaultTimeoutMs;
    private final int defaultMaxAgeSec;
    private final long defaultPeriodSec;
    private final int defaultMaxRules;
    private final long defaultMaxFileSize;

    private final ApplicationSettings applicationSettings;
    private final Vertx vertx;
    private final TimeoutFactory timeoutFactory;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;

    private final Set<String> fetchInProgress;
    private final Map<String, org.prebid.server.floors.PriceFloorFetcher.AccountFetchContext> rulesCache;

    public PriceFloorFetcher(long defaultTimeoutMs,
                             int defaultMaxAgeSec,
                             long defaultPeriodSec,
                             int defaultMaxRules,
                             long defaultMaxFileSizeKb,
                             ApplicationSettings applicationSettings,
                             Vertx vertx,
                             TimeoutFactory timeoutFactory, HttpClient httpClient,
                             JacksonMapper mapper) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultMaxAgeSec = defaultMaxAgeSec;
        this.defaultPeriodSec = defaultPeriodSec;
        this.defaultMaxRules = defaultMaxRules;
        this.defaultMaxFileSize = kBytesToBytes(defaultMaxFileSizeKb);
        this.applicationSettings = applicationSettings;
        this.vertx = Objects.requireNonNull(vertx);
        this.timeoutFactory = timeoutFactory;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.fetchInProgress = new ConcurrentHashSet<>();
        this.rulesCache = Caffeine.newBuilder()
                .<String, org.prebid.server.floors.PriceFloorFetcher.AccountFetchContext>build()
                .asMap();
    }

    public Future<PriceFloorRules> fetch(Account account) {
        final PriceFloorRules cachedPriceFloorRules =
                ObjectUtil.getIfNotNull(rulesCache.get(account.getId()), AccountFetchContext::getRules);

        return cachedPriceFloorRules != null
                ? Future.succeededFuture(cachedPriceFloorRules)
                : fetchPriceFloorRules(account);
    }

    private Future<PriceFloorRules> fetchPriceFloorRules(Account account) {

        final AccountPriceFloorsConfig priceFloorsConfig =
                ObjectUtil.getIfNotNull(account.getAuction(), AccountAuctionConfig::getPriceFloors);
        final AccountPriceFloorsFetchConfig fetchConfig =
                ObjectUtil.getIfNotNull(priceFloorsConfig, AccountPriceFloorsConfig::getFetch);
        final String accountId = account.getId();
        final Boolean fetchEnabled = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getEnabled);
        final String fetchUrl = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getUrl);

        if (BooleanUtils.isFalse(fetchEnabled)
                || StringUtils.isBlank(fetchUrl)
                || fetchInProgress.contains(account.getId())) {
            return Future.succeededFuture();
        }

        final Promise<PriceFloorRules> defaultPriceFloor = Promise.promise();
        final Future<PriceFloorRules> fetchedPriceFloor =
                prepareFetchRequest(fetchConfig, accountId, timeoutHandler(accountId, defaultPriceFloor))
                        .recover(throwable -> recoverFromFailedFetching(accountId, throwable));

        return CompositeFuture.any(fetchedPriceFloor, defaultPriceFloor.future())
                .map(compositeFuture -> (PriceFloorRules) compositeFuture.list().stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null))
                .recover(throwable -> recoverFromFailedFetching(accountId, throwable));
    }

    private Future<PriceFloorRules> prepareFetchRequest(AccountPriceFloorsFetchConfig fetchConfig,
                                                        String accountId,
                                                        Consumer<Promise<HttpClientResponse>> timeoutHandler) {

        final Long accountTimeout = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getTimeout);
        final Long accountMaxFetchFileSize =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxFileSize);
        final long timeout = ObjectUtils.defaultIfNull(accountTimeout, defaultTimeoutMs);
        final long maxFetchFileSize = ObjectUtils.defaultIfNull(accountMaxFetchFileSize, defaultMaxFileSize);

        fetchInProgress.add(accountId);
        return httpClient.get(fetchConfig.getUrl(), timeout, maxFetchFileSize, timeoutHandler)
                .map(httpClientResponse -> parseFloorResponse(fetchConfig, accountId, httpClientResponse))
                .map(priceFloorRules -> updateCache(accountId, priceFloorRules));
    }

    private Future<PriceFloorRules> recoverFromFailedFetching(String accountId, Throwable throwable) {
        logger.warn("Failed to fetch price floor from provider for account = {0} with a reason {1}: ",
                accountId, throwable.getMessage());
        fetchInProgress.remove(accountId);
        return Future.succeededFuture();
    }

    private AccountFetchContext parseFloorResponse(AccountPriceFloorsFetchConfig fetchConfig,
                                                   String accountId,
                                                   HttpClientResponse httpClientResponse) {
        final int statusCode = httpClientResponse.getStatusCode();
        if (statusCode != HttpStatus.SC_OK) {
            throw new PreBidException(String.format("Failed to request and cache price floor for account %s,"
                    + " provider respond with status %s", accountId, statusCode));
        }
        final String body = httpClientResponse.getBody();
        if (StringUtils.isNotBlank(body)) {
            try {
                final long cacheTtl = getCacheTimeToLive(fetchConfig);
                final PriceFloorRules priceFloorRules = mapper.decodeValue(body, PriceFloorRules.class);
                validatePriceFloorRules(priceFloorRules, fetchConfig);
                long timerId = vertx.setTimer(TimeUnit.SECONDS.toMillis(cacheTtl), id -> rulesCache.remove(accountId));
                return AccountFetchContext.of(priceFloorRules, timerId, LocalDateTime.now());
            } catch (DecodeException e) {
                throw new PreBidException(String.format("Failed to parse price floor response for account %s,"
                        + " provider respond with body %s", accountId, body));
            }
        } else {
            throw new PreBidException(String.format("Failed to parse price floor response for account %s, "
                    + "response body can't be empty or null", accountId));
        }
    }

    private long getCacheTimeToLive(AccountPriceFloorsFetchConfig fetchConfig) {
        final Integer accountMaxAgeSec =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxAgeSec);

        return ObjectUtils.defaultIfNull(accountMaxAgeSec, defaultMaxAgeSec);
    }

    private void validatePriceFloorRules(PriceFloorRules priceFloorRules, AccountPriceFloorsFetchConfig fetchConfig) {
        final PriceFloorData data = priceFloorRules.getData();

        if (data == null) {
            throw new PreBidException("Price floor rules data must be present");
        }

        final Integer accountMaxRules =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxRules);

        CollectionUtils.emptyIfNull(data.getModelGroups())
                .forEach(modelGroup -> validateModelGroup(modelGroup, accountMaxRules));
    }

    private void validateModelGroup(PriceFloorModelGroup modelGroup, Integer accountMaxRules) {
        final Map<String, BigDecimal> values = modelGroup.getValues();
        if (MapUtils.isEmpty(values)) {
            throw new PreBidException(String.format("Price floor rules values can't be null or empty, but were %s",
                    values));
        }

        final int maxRules = ObjectUtils.defaultIfNull(accountMaxRules, defaultMaxRules);
        if (values.size() > maxRules) {
            throw new PreBidException(String.format("Price floor rules number %s exceeded its maximum number %s",
                    values.size(), maxRules));
        }
    }

    private PriceFloorRules updateCache(String accountId, AccountFetchContext fetchContext) {
        rulesCache.put(accountId, fetchContext);
        fetchInProgress.remove(accountId);
        return fetchContext.rules;
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture(Account.empty(accountId))
                : applicationSettings.getAccountById(accountId, timeout)
                .otherwise(Account.empty(accountId));
    }

    private Consumer<Promise<HttpClientResponse>> timeoutHandler(String accountId,
                                                                 Promise<PriceFloorRules> defaultResult) {

        return clientResponsePromise -> {
            if (!clientResponsePromise.future().isComplete()) {
                logger.warn("Time to wait for price floor from provider for account %s exceeded, continue auction"
                        + " with default price floor rules", accountId);
                defaultResult.tryComplete(null);
                vertx.setTimer(defaultTimeoutMs, ignored -> waitForFetchComplete(accountId, clientResponsePromise));
            }
        };
    }

    private void waitForFetchComplete(String accountId, Promise<HttpClientResponse> httpClientResponsePromise) {
        if (!httpClientResponsePromise.future().isComplete()) {
            fetchInProgress.remove(accountId);
            logger.warn("Fetch price floor request timeout for account %s exceeded.");
            httpClientResponsePromise.tryFail(new TimeoutException(
                    String.format("Timeout period of %dms has been exceeded", defaultTimeoutMs)));
        }
    }

    private static long kBytesToBytes(long kBytes) {
        return kBytes * BYTES_IN_KBYTES;
    }

    @Value(staticConstructor = "of")
    private static class AccountFetchContext {

        PriceFloorRules rules;

        long maxAgeTimerId;

        LocalDateTime lastFetchTime;
    }
}
