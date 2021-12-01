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
import org.apache.http.HttpStatus;
import org.prebid.server.exception.PreBidException;
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

public class PriceFloorFetcher {

    private static final Logger logger = LoggerFactory.getLogger(org.prebid.server.floors.PriceFloorFetcher.class);

    private static final int BYTES_IN_KBYTES = 1024;
    private static final String MAX_AGE = "max-age";
    private static final String EQUAL_SIGN = "=";

    private final long defaultTimeoutMs;

    private final ApplicationSettings applicationSettings;
    private final Vertx vertx;
    private final TimeoutFactory timeoutFactory;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;

    private final Set<String> fetchInProgress;
    private final Map<String, AccountFetchContext> rulesCache;

    public PriceFloorFetcher(long defaultTimeoutMs,
                             ApplicationSettings applicationSettings,
                             Vertx vertx,
                             TimeoutFactory timeoutFactory, HttpClient httpClient,
                             JacksonMapper mapper) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.applicationSettings = applicationSettings;
        this.vertx = Objects.requireNonNull(vertx);
        this.timeoutFactory = timeoutFactory;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.fetchInProgress = new ConcurrentHashSet<>();
        this.rulesCache = Caffeine.newBuilder()
                .<String, AccountFetchContext>build()
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

        return fetchPriceFloorRules(getFetchConfig(account), account.getId());
    }

    private Future<PriceFloorRules> fetchPriceFloorRules(
            AccountPriceFloorsFetchConfig fetchConfig,
            String accountId) {

        final Boolean fetchEnabled = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getEnabled);
        final String fetchUrl = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getUrl);

        if (BooleanUtils.isFalse(fetchEnabled)
                || StringUtils.isBlank(fetchUrl)
                || fetchInProgress.contains(accountId)) {

            return Future.succeededFuture();
        }

        final Long timeout = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getTimeout);
        final Long maxFetchFileSizeKb =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxFileSize);

        fetchInProgress.add(accountId);
        return httpClient.get(fetchConfig.getUrl(), timeout, kBytesToBytes(maxFetchFileSizeKb))
                .map(httpClientResponse -> parseFloorResponse(fetchConfig, accountId, httpClientResponse))
                .map(cacheInfo -> updateCache(cacheInfo, fetchConfig, accountId))
                .recover(throwable -> recoverFromFailedFetching(accountId, throwable))
                .map(priceFloorRules -> createPeriodicTimer(priceFloorRules, fetchConfig, accountId));
    }

    private ResponseCacheInfo parseFloorResponse(AccountPriceFloorsFetchConfig fetchConfig,
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
                final PriceFloorRules priceFloorRules = mapper.decodeValue(body, PriceFloorRules.class);
                validatePriceFloorRules(priceFloorRules, fetchConfig);

                return ResponseCacheInfo.of(priceFloorRules, cacheTtlFromResponse(httpClientResponse));
            } catch (DecodeException e) {
                throw new PreBidException(String.format("Failed to parse price floor response for account %s,"
                        + " provider respond with body %s", accountId, body));
            }
        } else {
            throw new PreBidException(String.format("Failed to parse price floor response for account %s, "
                    + "response body can't be empty or null", accountId));
        }
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

    private void validateModelGroup(PriceFloorModelGroup modelGroup, Integer maxRules) {
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
                    logger.warn("Can't parse Cache Control header '{}'", cacheMaxAge);
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
                AccountFetchContext.of(cacheInfo.getRules(), maxAgeTimerId, LocalDateTime.now());
        rulesCache.put(accountId, fetchContext);
        fetchInProgress.remove(accountId);

        return fetchContext.rules;
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
                ObjectUtil.getIfNotNull(rulesCache.get(accountId), AccountFetchContext::getMaxAgeTimerId);
        if (previousTimerId != null) {
            vertx.cancelTimer(previousTimerId);
        }

        return vertx.setTimer(TimeUnit.SECONDS.toMillis(cacheTtl), id -> rulesCache.remove(accountId));
    }

    private AccountPriceFloorsFetchConfig getFetchConfig(Account account) {
        final AccountPriceFloorsConfig priceFloorsConfig =
                ObjectUtil.getIfNotNull(account.getAuction(), AccountAuctionConfig::getPriceFloors);

        return ObjectUtil.getIfNotNull(priceFloorsConfig, AccountPriceFloorsConfig::getFetch);
    }

    private void periodicFetch(String accountId) {

        accountById(accountId).compose(account -> fetchPriceFloorRules(getFetchConfig(account), accountId));
    }

    private Future<PriceFloorRules> recoverFromFailedFetching(String accountId, Throwable throwable) {
        if (throwable instanceof TimeoutException || throwable instanceof ConnectTimeoutException) {
            logger.warn("Fetch price floor request timeout for account {0} exceeded.", accountId);
        } else if (throwable instanceof PreBidException) {
            logger.warn("Failed to fetch price floor from provider for account = {0} with a reason {1}: ",
                    accountId, throwable.getMessage());
        }
        fetchInProgress.remove(accountId);
        final AccountFetchContext fetchContext = rulesCache.get(accountId);
        if (fetchContext != null) {
            rulesCache.put(accountId, fetchContext.with(LocalDateTime.now()));
        } else {
            rulesCache.put(accountId, AccountFetchContext.of(null, null, LocalDateTime.now()));
        }

        return Future.succeededFuture();
    }

    private PriceFloorRules createPeriodicTimer(PriceFloorRules priceFloorRules,
                                                AccountPriceFloorsFetchConfig fetchConfig,
                                                String accountId) {
        final long periodicTime = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getPeriodSec);
        vertx.setTimer(TimeUnit.SECONDS.toMillis(periodicTime), id -> periodicFetch(accountId));

        return priceFloorRules;
    }

    private Future<Account> accountById(String accountId) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture(Account.empty(accountId))
                : applicationSettings.getAccountById(accountId, timeoutFactory.create(defaultTimeoutMs))
                .otherwise(Account.empty(accountId));
    }

    private static long kBytesToBytes(long kBytes) {
        return kBytes * BYTES_IN_KBYTES;
    }

    @Value(staticConstructor = "of")
    private static class AccountFetchContext {

        PriceFloorRules rules;

        Long maxAgeTimerId;

        LocalDateTime lastFetchTime;

        public AccountFetchContext with(LocalDateTime lastFetchTime) {
            return AccountFetchContext.of(this.rules, this.maxAgeTimerId, lastFetchTime);
        }
    }

    @Value(staticConstructor = "of")
    private static class ResponseCacheInfo {

        PriceFloorRules rules;

        Long cacheTtl;
    }
}
