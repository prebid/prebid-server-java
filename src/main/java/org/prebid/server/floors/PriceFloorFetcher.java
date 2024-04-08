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
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorDebugProperties;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceFloorFetcher {

    private static final Logger logger = LoggerFactory.getLogger(PriceFloorFetcher.class);

    private static final int ACCOUNT_FETCH_TIMEOUT_MS = 5000;
    private static final int MAXIMUM_CACHE_SIZE = 300;
    private static final int MIN_MAX_AGE_SEC_VALUE = 600;
    private static final int MAX_AGE_SEC_VALUE = Integer.MAX_VALUE;
    private static final Pattern CACHE_CONTROL_HEADER_PATTERN = Pattern.compile("^.*max-age=(\\d+).*$");

    private final ApplicationSettings applicationSettings;
    private final Metrics metrics;
    private final Vertx vertx;
    private final TimeoutFactory timeoutFactory;
    private final HttpClient httpClient;
    private final JacksonMapper mapper;
    private final PriceFloorDebugProperties debugProperties;

    private final Set<String> fetchInProgress;
    private final Map<String, AccountFetchContext> fetchedData;

    public PriceFloorFetcher(ApplicationSettings applicationSettings,
                             Metrics metrics,
                             Vertx vertx,
                             TimeoutFactory timeoutFactory,
                             HttpClient httpClient,
                             PriceFloorDebugProperties debugProperties,
                             JacksonMapper mapper) {

        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.metrics = Objects.requireNonNull(metrics);
        this.vertx = Objects.requireNonNull(vertx);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.debugProperties = debugProperties;
        this.mapper = Objects.requireNonNull(mapper);

        fetchInProgress = new ConcurrentHashSet<>();
        fetchedData = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_CACHE_SIZE)
                .<String, AccountFetchContext>build()
                .asMap();
    }

    public FetchResult fetch(Account account) {
        final AccountFetchContext accountFetchContext = fetchedData.get(account.getId());

        return accountFetchContext != null
                ? FetchResult.of(accountFetchContext.getRulesData(), accountFetchContext.getFetchStatus())
                : fetchPriceFloorData(account);
    }

    private FetchResult fetchPriceFloorData(Account account) {
        final AccountPriceFloorsFetchConfig fetchConfig = getFetchConfig(account);
        final Boolean fetchEnabled = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getEnabled);

        if (BooleanUtils.isFalse(fetchEnabled)) {
            return FetchResult.of(null, FetchStatus.none);
        }

        final String accountId = account.getId();
        final String fetchUrl = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getUrl);
        if (!isUrlValid(fetchUrl)) {
            logger.error("Malformed fetch.url: '%s', passed for account %s".formatted(fetchUrl, accountId));
            return FetchResult.of(null, FetchStatus.error);
        }
        if (!fetchInProgress.contains(accountId)) {
            fetchPriceFloorDataAsynchronous(fetchConfig, accountId);
        }

        return FetchResult.of(null, FetchStatus.inprogress);
    }

    private boolean isUrlValid(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }

        try {
            HttpUtil.validateUrl(url);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return true;
    }

    private static AccountPriceFloorsFetchConfig getFetchConfig(Account account) {
        final AccountPriceFloorsConfig priceFloorsConfig =
                ObjectUtil.getIfNotNull(account.getAuction(), AccountAuctionConfig::getPriceFloors);

        return ObjectUtil.getIfNotNull(priceFloorsConfig, AccountPriceFloorsConfig::getFetch);
    }

    private void fetchPriceFloorDataAsynchronous(AccountPriceFloorsFetchConfig fetchConfig, String accountId) {
        final Long accountTimeout = ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getTimeout);
        final Long timeout = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(debugProperties, PriceFloorDebugProperties::getMinTimeoutMs),
                ObjectUtil.getIfNotNull(debugProperties, PriceFloorDebugProperties::getMaxTimeoutMs),
                accountTimeout);
        final Long maxFetchFileSizeKb =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getMaxFileSize);
        final String fetchUrl = fetchConfig.getUrl();

        fetchInProgress.add(accountId);
        httpClient.get(fetchUrl, timeout, resolveMaxFileSize(maxFetchFileSizeKb))
                .map(httpClientResponse -> parseFloorResponse(httpClientResponse, fetchConfig, accountId))
                .recover(throwable -> recoverFromFailedFetching(throwable, fetchUrl, accountId))
                .map(cacheInfo -> updateCache(cacheInfo, fetchConfig, accountId))
                .map(priceFloorData -> createPeriodicTimerForRulesFetch(priceFloorData, fetchConfig, accountId));
    }

    private static long resolveMaxFileSize(Long maxSizeInKBytes) {
        return Objects.equals(maxSizeInKBytes, 0L) ? Long.MAX_VALUE : maxSizeInKBytes * 1024;
    }

    private ResponseCacheInfo parseFloorResponse(HttpClientResponse httpClientResponse,
                                                 AccountPriceFloorsFetchConfig fetchConfig,
                                                 String accountId) {

        final int statusCode = httpClientResponse.getStatusCode();
        if (statusCode != HttpStatus.SC_OK) {
            throw new PreBidException("Failed to request for account %s, provider respond with status %s"
                    .formatted(accountId, statusCode));
        }
        final String body = httpClientResponse.getBody();

        if (StringUtils.isBlank(body)) {
            throw new PreBidException(
                    "Failed to parse price floor response for account %s, response body can not be empty"
                            .formatted(accountId));
        }

        final PriceFloorData priceFloorData = parsePriceFloorData(body, accountId);
        PriceFloorRulesValidator.validateRulesData(priceFloorData, resolveMaxRules(fetchConfig.getMaxRules()));

        return ResponseCacheInfo.of(priceFloorData,
                FetchStatus.success,
                cacheTtlFromResponse(httpClientResponse, fetchConfig.getUrl()));
    }

    private PriceFloorData parsePriceFloorData(String body, String accountId) {
        final PriceFloorData priceFloorData;
        try {
            priceFloorData = mapper.decodeValue(body, PriceFloorData.class);
        } catch (DecodeException e) {
            throw new PreBidException("Failed to parse price floor response for account %s, cause: %s"
                    .formatted(accountId, ExceptionUtils.getMessage(e)));
        }
        return priceFloorData;
    }

    private static int resolveMaxRules(Long accountMaxRules) {
        return accountMaxRules != null && !accountMaxRules.equals(0L)
                ? Math.toIntExact(accountMaxRules)
                : Integer.MAX_VALUE;
    }

    private Long cacheTtlFromResponse(HttpClientResponse httpClientResponse, String fetchUrl) {
        final String cacheControlValue = httpClientResponse.getHeaders().get(HttpHeaders.CACHE_CONTROL);
        final Matcher cacheHeaderMatcher = StringUtils.isNotBlank(cacheControlValue)
                ? CACHE_CONTROL_HEADER_PATTERN.matcher(cacheControlValue)
                : null;

        if (cacheHeaderMatcher != null && cacheHeaderMatcher.matches()) {
            try {
                return Long.parseLong(cacheHeaderMatcher.group(1));
            } catch (NumberFormatException e) {
                logger.error("Can't parse Cache Control header '%s', fetch.url: '%s'"
                        .formatted(cacheControlValue, fetchUrl));
            }
        }

        return null;
    }

    private PriceFloorData updateCache(ResponseCacheInfo cacheInfo,
                                       AccountPriceFloorsFetchConfig fetchConfig,
                                       String accountId) {

        final long maxAgeTimerId = createMaxAgeTimer(accountId, resolveCacheTtl(cacheInfo, fetchConfig));
        final AccountFetchContext fetchContext =
                AccountFetchContext.of(cacheInfo.getRulesData(), cacheInfo.getFetchStatus(), maxAgeTimerId);

        if (cacheInfo.getFetchStatus() == FetchStatus.success || !fetchedData.containsKey(accountId)) {
            fetchedData.put(accountId, fetchContext);
        }

        fetchInProgress.remove(accountId);

        return fetchContext.getRulesData();
    }

    private static long resolveCacheTtl(ResponseCacheInfo cacheInfo, AccountPriceFloorsFetchConfig fetchConfig) {
        final Long headerCacheTtl = cacheInfo.getCacheTtl();

        return isValidMaxAge(headerCacheTtl, fetchConfig) ? headerCacheTtl : fetchConfig.getMaxAgeSec();
    }

    private static boolean isValidMaxAge(Long headerCacheTtl, AccountPriceFloorsFetchConfig fetchConfig) {
        final Long periodSec = fetchConfig.getPeriodSec();
        final long minMaxAgeValue = periodSec != null
                ? Math.max(MIN_MAX_AGE_SEC_VALUE, periodSec)
                : MIN_MAX_AGE_SEC_VALUE;

        return headerCacheTtl != null && isInMaxAgeRange(headerCacheTtl, minMaxAgeValue);
    }

    private static boolean isInMaxAgeRange(long number, long min) {
        return Math.max(min, number) == Math.min(number, MAX_AGE_SEC_VALUE);
    }

    private Long createMaxAgeTimer(String accountId, long cacheTtl) {
        final Long previousTimerId =
                ObjectUtil.getIfNotNull(fetchedData.get(accountId), AccountFetchContext::getMaxAgeTimerId);

        if (previousTimerId != null) {
            vertx.cancelTimer(previousTimerId);
        }

        final Long effectiveCacheTtl =
                ObjectUtils.defaultIfNull(
                        ObjectUtil.getIfNotNull(debugProperties, PriceFloorDebugProperties::getMinMaxAgeSec),
                        cacheTtl);

        return vertx.setTimer(TimeUnit.SECONDS.toMillis(effectiveCacheTtl), id -> fetchedData.remove(accountId));
    }

    private Future<ResponseCacheInfo> recoverFromFailedFetching(Throwable throwable,
                                                                String fetchUrl,
                                                                String accountId) {

        metrics.updatePriceFloorFetchMetric(MetricName.failure);

        final FetchStatus fetchStatus;
        if (throwable instanceof TimeoutException || throwable instanceof ConnectTimeoutException) {
            fetchStatus = FetchStatus.timeout;
            logger.error("Fetch price floor request timeout for fetch.url: '%s', account %s exceeded."
                    .formatted(fetchUrl, accountId));
        } else {
            fetchStatus = FetchStatus.error;
            logger.error(
                    "Failed to fetch price floor from provider for fetch.url: '%s', account = %s with a reason : %s "
                            .formatted(fetchUrl, accountId, throwable.getMessage()));
        }

        return Future.succeededFuture(ResponseCacheInfo.withStatus(fetchStatus));
    }

    private PriceFloorData createPeriodicTimerForRulesFetch(PriceFloorData priceFloorData,
                                                            AccountPriceFloorsFetchConfig fetchConfig,
                                                            String accountId) {
        final long accountPeriodicTimeSec =
                ObjectUtil.getIfNotNull(fetchConfig, AccountPriceFloorsFetchConfig::getPeriodSec);
        final long periodicTimeSec =
                ObjectUtils.defaultIfNull(
                        ObjectUtil.getIfNotNull(debugProperties, PriceFloorDebugProperties::getMinPeriodSec),
                        accountPeriodicTimeSec);
        vertx.setTimer(TimeUnit.SECONDS.toMillis(periodicTimeSec), ignored -> periodicFetch(accountId));

        return priceFloorData;
    }

    private void periodicFetch(String accountId) {
        accountById(accountId).map(this::fetchPriceFloorData);
    }

    private Future<Account> accountById(String accountId) {
        return applicationSettings.getAccountById(accountId, timeoutFactory.create(ACCOUNT_FETCH_TIMEOUT_MS))
                .otherwiseEmpty();
    }

    @Value(staticConstructor = "of")
    private static class AccountFetchContext {

        PriceFloorData rulesData;

        FetchStatus fetchStatus;

        Long maxAgeTimerId;
    }

    @Value(staticConstructor = "of")
    private static class ResponseCacheInfo {

        PriceFloorData rulesData;

        FetchStatus fetchStatus;

        Long cacheTtl;

        public static ResponseCacheInfo withStatus(FetchStatus status) {
            return ResponseCacheInfo.of(null, status, null);
        }
    }
}
