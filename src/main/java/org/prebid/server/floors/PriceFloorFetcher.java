package org.prebid.server.floors;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PriceFloorFetcher {

    private static final Logger logger = LoggerFactory.getLogger(PriceFloorFetcher.class);
    private static final ConditionalLogger samplingLogger = new ConditionalLogger(logger);
    private static final int ACCOUNT_FETCH_TIMEOUT = 10;

    private final ApplicationSettings applicationSettings;
    private final Metrics metrics;
    private final Vertx vertx;
    private final TimeoutFactory timeoutFactory;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;

    private final Set<String> fetchInProgress;
    private final Map<String, AccountFetchContext> fetchedData;

    public PriceFloorFetcher(ApplicationSettings applicationSettings,
                             Metrics metrics,
                             Vertx vertx,
                             TimeoutFactory timeoutFactory,
                             HttpClient httpClient,
                             JacksonMapper mapper) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.metrics = Objects.requireNonNull(metrics);
        this.vertx = Objects.requireNonNull(vertx);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);

        fetchInProgress = new ConcurrentHashSet<>();
        fetchedData = Caffeine.newBuilder()
                .<String, AccountFetchContext>build()
                .asMap();
    }

    public Future<PriceFloorRules> fetch(Account account) {
        final PriceFloorRules cachedPriceFloorRules =
                ObjectUtil.getIfNotNull(fetchedData.get(account.getId()), AccountFetchContext::getRules);

        return cachedPriceFloorRules != null
                ? Future.succeededFuture(cachedPriceFloorRules)
                : fetchPriceFloorRules(account);
    }

    private Future<PriceFloorRules> fetchPriceFloorRules(Account account) {
        if (account == null) {
            return Future.succeededFuture();
        }

        final AccountPriceFloorsFetchConfig fetchConfig = getFetchConfig(account);
        final Boolean fetchEnabled = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getEnabled);
        final String fetchUrl = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getUrl);
        final String accountId = account.getId();

        if (shouldFetchRules(fetchEnabled, fetchUrl, accountId)) {
            fetchPriceFloorRulesAsynchronous(fetchConfig, accountId);
        }

        return Future.succeededFuture();
    }

    private boolean shouldFetchRules(Boolean fetchEnabled, String fetchUrl, String accountId) {
        if (BooleanUtils.isFalse(fetchEnabled)
                || StringUtils.isBlank(fetchUrl)
                || fetchInProgress.contains(accountId)) {

            return false;
        }

        try {
            HttpUtil.validateUrl(fetchUrl);
        } catch (IllegalArgumentException e) {
            samplingLogger.warn(String.format("Malformed fetch.url passed for account %s", accountId), 0.01);
            return false;
        }

        return true;
    }

    private static AccountPriceFloorsFetchConfig getFetchConfig(Account account) {
        final AccountPriceFloorsConfig priceFloorsConfig =
                ObjectUtil.getIfNotNull(account.getAuction(), AccountAuctionConfig::getPriceFloors);

        return ObjectUtil.getIfNotNull(priceFloorsConfig, AccountPriceFloorsConfig::getFetch);
    }

    private void fetchPriceFloorRulesAsynchronous(AccountPriceFloorsFetchConfig fetchConfig, String accountId) {
        final Long timeout = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getTimeout);
        final Long maxFetchFileSizeKb =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxFileSize);

        fetchInProgress.add(accountId);
        httpClient.get(fetchConfig.getUrl(), timeout, resolveMaxFileSize(maxFetchFileSizeKb))
                .map(httpClientResponse -> parseFloorResponse(httpClientResponse, fetchConfig, accountId))
                .map(cacheInfo -> updateCache(cacheInfo, fetchConfig, accountId))
                .recover(throwable -> recoverFromFailedFetching(throwable, accountId))
                .map(priceFloorRules -> createPeriodicTimerForRulesFetch(priceFloorRules, fetchConfig, accountId));
    }

    private static long resolveMaxFileSize(Long maxSizeInKBytes) {
        return Objects.equals(maxSizeInKBytes, 0L) ? Long.MAX_VALUE : maxSizeInKBytes * 1024;
    }

    private ResponseCacheInfo parseFloorResponse(HttpClientResponse httpClientResponse,
                                                 AccountPriceFloorsFetchConfig fetchConfig,
                                                 String accountId) {
        final int statusCode = httpClientResponse.getStatusCode();
        if (statusCode != HttpStatus.SC_OK) {
            throw new PreBidException(String.format("Failed to request for account %s,"
                    + " provider respond with status %s", accountId, statusCode));
        }
        final String body = httpClientResponse.getBody();

        if (StringUtils.isBlank(body)) {
            throw new PreBidException(String.format("Failed to parse price floor response for account %s, "
                    + "response body can not be empty", accountId));
        }

        final PriceFloorRules priceFloorRules;
        try {
            priceFloorRules = mapper.decodeValue(body, PriceFloorRules.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Failed to parse price floor response for account %s, cause: %s",
                    accountId, ExceptionUtils.getMessage(e)));
        }
        validatePriceFloorRules(priceFloorRules, fetchConfig);

        return ResponseCacheInfo.of(priceFloorRules, cacheTtlFromResponse(httpClientResponse));
    }

    private void validatePriceFloorRules(PriceFloorRules priceFloorRules, AccountPriceFloorsFetchConfig fetchConfig) {
        final PriceFloorData data = priceFloorRules.getData();

        if (data == null) {
            throw new PreBidException("Price floor rules data must be present");
        }

        if (CollectionUtils.isEmpty(data.getModelGroups())) {
            throw new PreBidException("Price floor rules should contain at least one model group");
        }

        final Integer maxRules = resolveMaxRules(fetchConfig.getMaxRules());

        CollectionUtils.emptyIfNull(data.getModelGroups()).stream()
                .filter(Objects::nonNull)
                .forEach(modelGroup -> validateModelGroup(modelGroup, maxRules));
    }

    private static int resolveMaxRules(Integer accountMaxRules) {
        return Objects.equals(accountMaxRules, 0L) ? Integer.MAX_VALUE : accountMaxRules;
    }

    private static void validateModelGroup(PriceFloorModelGroup modelGroup, Integer maxRules) {
        final Map<String, BigDecimal> values = modelGroup.getValues();
        if (MapUtils.isEmpty(values)) {
            throw new PreBidException(String.format("Price floor rules values can't be null or empty, but were %s",
                    values));
        }

        if (values.size() > maxRules) {
            throw new PreBidException(String.format("Price floor rules number %s exceeded its maximum number %s",
                    values.size(), maxRules));
        }
    }

    private Long cacheTtlFromResponse(HttpClientResponse httpClientResponse) {
        final String cacheMaxAge = httpClientResponse.getHeaders().get(HttpHeaders.CACHE_CONTROL);
        if (StringUtils.isNotBlank(cacheMaxAge) && cacheMaxAge.contains("max-age")) {
            final String[] maxAgeRecord = cacheMaxAge.split("=");
            if (maxAgeRecord.length == 2) {
                try {
                    return Long.parseLong(maxAgeRecord[1]);
                } catch (NumberFormatException ex) {
                    samplingLogger.warn(String.format("Can't parse Cache Control header '%s'", cacheMaxAge), 0.01);
                }
            }
        }

        return null;
    }

    private PriceFloorRules updateCache(ResponseCacheInfo cacheInfo,
                                        AccountPriceFloorsFetchConfig fetchConfig,
                                        String accountId) {
        long maxAgeTimerId = createMaxAgeTimer(accountId, resolveCacheTtl(cacheInfo, fetchConfig));

        final AccountFetchContext fetchContext =
                AccountFetchContext.of(cacheInfo.getRules(), maxAgeTimerId);
        fetchedData.put(accountId, fetchContext);
        fetchInProgress.remove(accountId);

        return fetchContext.getRules();
    }

    private long resolveCacheTtl(ResponseCacheInfo cacheInfo, AccountPriceFloorsFetchConfig fetchConfig) {
        final Long responseTtl = cacheInfo.getCacheTtl();
        if (responseTtl != null) {
            return responseTtl;
        }

        return ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxAgeSec);
    }

    private Long createMaxAgeTimer(String accountId, long cacheTtl) {
        final Long previousTimerId =
                ObjectUtil.getIfNotNull(fetchedData.get(accountId), AccountFetchContext::getMaxAgeTimerId);

        if (previousTimerId != null) {
            vertx.cancelTimer(previousTimerId);
        }

        return vertx.setTimer(TimeUnit.SECONDS.toMillis(cacheTtl), id -> fetchedData.remove(accountId));
    }

    private Future<PriceFloorRules> recoverFromFailedFetching(Throwable throwable, String accountId) {
        metrics.updatePriceFloorFetchMetric(MetricName.failure);

        if (throwable instanceof TimeoutException || throwable instanceof ConnectTimeoutException) {
            samplingLogger.warn(
                    String.format("Fetch price floor request timeout for account %s exceeded.", accountId), 0.01);
        } else if (throwable instanceof PreBidException) {
            samplingLogger.warn(
                    String.format("Failed to fetch price floor from provider for account = %s with a reason : %s ",
                            accountId, throwable.getMessage()), 0.01);
        }
        fetchInProgress.remove(accountId);

        return Future.succeededFuture();
    }

    private PriceFloorRules createPeriodicTimerForRulesFetch(PriceFloorRules priceFloorRules,
                                                             AccountPriceFloorsFetchConfig fetchConfig,
                                                             String accountId) {
        final long periodicTime = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getPeriodSec);
        vertx.setTimer(TimeUnit.SECONDS.toMillis(periodicTime), id -> periodicFetch(accountId));

        return priceFloorRules;
    }

    private void periodicFetch(String accountId) {

        accountById(accountId).compose(this::fetchPriceFloorRules);
    }

    private Future<Account> accountById(String accountId) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture()
                : applicationSettings
                .getAccountById(accountId, timeoutFactory.create(TimeUnit.SECONDS.toMillis(ACCOUNT_FETCH_TIMEOUT)))
                .recover(ignored -> Future.succeededFuture());
    }

    @Value(staticConstructor = "of")
    private static class AccountFetchContext {

        PriceFloorRules rules;

        Long maxAgeTimerId;
    }

    @Value(staticConstructor = "of")
    private static class ResponseCacheInfo {

        PriceFloorRules rules;

        Long cacheTtl;
    }
}
