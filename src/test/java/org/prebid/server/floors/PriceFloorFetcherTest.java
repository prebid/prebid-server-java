package org.prebid.server.floors;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorDebugProperties;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.model.PriceFloorSchema;
import org.prebid.server.floors.proto.FetchResult;
import org.prebid.server.floors.proto.FetchStatus;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.floors.model.PriceFloorField.domain;
import static org.prebid.server.floors.model.PriceFloorField.mediaType;
import static org.prebid.server.floors.model.PriceFloorField.siteDomain;

@ExtendWith(MockitoExtension.class)
public class PriceFloorFetcherTest extends VertxTest {

    @Mock
    private ApplicationSettings applicationSettings;

    @Mock
    private PriceFloorDebugProperties debugProperties;

    @Mock
    private Metrics metrics;

    @Mock
    private HttpClient httpClient;

    @Mock
    private Vertx vertx;

    @Mock
    private TimeoutFactory timeoutFactory;

    private PriceFloorFetcher priceFloorFetcher;

    @BeforeEach
    public void setUp() {
        debugProperties = new PriceFloorDebugProperties();
        priceFloorFetcher = new PriceFloorFetcher(
                applicationSettings,
                metrics,
                vertx,
                timeoutFactory,
                httpClient,
                debugProperties,
                jacksonMapper);
    }

    @Test
    public void fetchShouldReturnPriceFloorFetchedFromProviderAndCache() {
        // given
        final Account givenAccount = givenAccount(identity());
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(givenPriceFloorData()))));
        // when
        final FetchResult fetchResult = priceFloorFetcher.fetch(givenAccount);

        // then
        assertThat(fetchResult.getFetchStatus()).isEqualTo(FetchStatus.inprogress);
        verify(httpClient).get("http://test.host.com", 1300, 10240);

        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(httpClient);

        final FetchResult priceFloorRulesCached = priceFloorFetcher.fetch(givenAccount);
        assertThat(priceFloorRulesCached)
                .isEqualTo(FetchResult.of(givenPriceFloorData(), FetchStatus.success, null));

    }

    @Test
    public void fetchShouldReturnEmptyRulesAndInProgressStatusForTheFirstInvocation() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.failedFuture(new PreBidException("failed")));

        // when
        final FetchResult fetchResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        assertThat(fetchResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
    }

    @Test
    public void fetchShouldReturnEmptyRulesAndInProgressStatusForTheFirstInvocationAndErrorStatusForSecond() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.failedFuture(new PreBidException("failed")));

        // when
        final FetchResult firstInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());

        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.error(
                "Failed to fetch price floor from provider for fetch.url: "
                        + "'http://test.host.com', account = 1001 with a reason : failed "));
    }

    @Test
    public void fetchShouldReturnEmptyRulesAndInProgressStatusForTheFirstInvocationAndTimeoutStatusForSecond() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("failed")));

        // when
        final FetchResult firstInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        assertThat(firstInvocationResult.getRulesData()).isNull();
        assertThat(firstInvocationResult.getFetchStatus()).isEqualTo(FetchStatus.inprogress);
        verify(vertx).setTimer(eq(1200000L), any());

        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.of(
                null,
                FetchStatus.timeout,
                "Fetch price floor request timeout for fetch.url: 'http://test.host.com', account 1001 exceeded."));
    }

    @Test
    public void fetchShouldCacheResponseForTimeFromResponseCacheControlHeader() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap()
                                        .add(HttpHeaders.CACHE_CONTROL, "max-age=1700"),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1700000L), any());
    }

    @Test
    public void fetchShouldNotCacheResponseFoWithTimeFromResponseCacheControlHeaderIfLessThanMinValue() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap()
                                        .add(HttpHeaders.CACHE_CONTROL, "max-age=500"),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(config -> config.periodSec(500L)));

        // then
        verify(vertx).setTimer(eq(1500000L), any());
    }

    @Test
    public void fetchShouldNotCacheResponseFoWithTimeFromResponseCacheControlHeaderIfLessThanPeriodSec() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap()
                                        .add(HttpHeaders.CACHE_CONTROL, "max-age=900"),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1500000L), any());
    }

    @Test
    public void fetchShouldCacheResponseForTimeFromResponseCacheControlHeaderToleratingOtherHeaderData() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap()
                                        .add(HttpHeaders.CACHE_CONTROL,
                                                "no-cache, no-store, max-age=1700, must-revalidate"),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1700000L), any());
    }

    @Test
    public void fetchShouldTakePrecedenceForTestingPropertyToCacheResponse() {
        // given
        debugProperties.setMinMaxAgeSec(1L);
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap()
                                        .add(HttpHeaders.CACHE_CONTROL, "max-age=700"),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1000L), any());
        verify(vertx).setTimer(eq(1200000L), any());
    }

    @Test
    public void fetchShouldTakePrecedenceForTestingPropertyToCreatePeriodicTimer() {
        // given
        debugProperties.setMinPeriodSec(1L);
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
    }

    @Test
    public void fetchShouldTakePrecedenceForTestingPropertyToChooseRequestTimeout() {
        // given
        debugProperties.setMaxTimeoutMs(1L);
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), eq(1L), anyLong());
    }

    @Test
    public void fetchShouldTakePrecedenceForMinTimeoutTestingPropertyToChooseRequestTimeout() {
        // given
        debugProperties.setMinTimeoutMs(1L);
        debugProperties.setMaxTimeoutMs(2L);
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                                jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), eq(1L), anyLong());
    }

    @Test
    public void fetchShouldSetDefaultCacheTimeWhenCacheControlHeaderCantBeParsed() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CACHE_CONTROL, "invalid"),
                        jacksonMapper.encodeToString(givenPriceFloorData()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1500000L), any());
    }

    @Test
    public void fetchShouldNotPrepareAnyRequestsWhenFetchUrlIsMalformedAndReturnErrorStatus() {
        // when
        final FetchResult fetchResult = priceFloorFetcher.fetch(givenAccount(config -> config.url("MalformedURl")));

        // then
        verifyNoInteractions(httpClient);
        assertThat(fetchResult).isEqualTo(FetchResult.error(
                "Malformed fetch.url: 'MalformedURl', passed for account 1001"));
        verifyNoInteractions(vertx);
    }

    @Test
    public void fetchShouldNotPrepareAnyRequestsWhenFetchUrlIsBlankAndReturnErrorStatus() {
        // when
        final FetchResult fetchResult = priceFloorFetcher.fetch(givenAccount(config -> config.url("   ")));

        // then
        verifyNoInteractions(httpClient);
        assertThat(fetchResult).isEqualTo(FetchResult.error("Malformed fetch.url: '   ', passed for account 1001"));
        verifyNoInteractions(vertx);
    }

    @Test
    public void fetchShouldNotPrepareAnyRequestsWhenFetchUrlIsNotProvidedAndReturnErrorStatus() {
        // when
        final FetchResult fetchResult = priceFloorFetcher.fetch(givenAccount(config -> config.url(null)));

        // then
        verifyNoInteractions(httpClient);
        assertThat(fetchResult).isEqualTo(FetchResult.error("Malformed fetch.url: 'null', passed for account 1001"));
        verifyNoInteractions(vertx);
    }

    @Test
    public void fetchShouldNotPrepareAnyRequestsWhenFetchEnabledIsFalseAndReturnNoneStatus() {
        // when
        final FetchResult fetchResult = priceFloorFetcher.fetch(givenAccount(config -> config.enabled(false)));

        // then
        verifyNoInteractions(httpClient);
        assertThat(fetchResult).isEqualTo(FetchResult.none("Fetching is disabled for account 1001"));
        verifyNoInteractions(vertx);
    }

    @Test
    public void fetchShouldReturnEmptyRulesAndErrorStatusForSecondCallAndCreatePeriodicTimerWhenResponseIsNot200Ok() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(PriceFloorRules.builder().build()))));

        // when
        final FetchResult firstInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.error(
                "Failed to fetch price floor from provider for fetch.url: 'http://test.host.com', "
                        + "account = 1001 with a reason : "
                        + "Failed to request for account 1001, provider respond with status 400 "));
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldReturnEmptyRulesWithErrorStatusAndCreatePeriodicTimerWhenResponseHasInvalidFormat() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), "{")));

        // when
        final FetchResult firstInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult.getRulesData()).isNull();
        assertThat(secondInvocationResult.getFetchStatus()).isEqualTo(FetchStatus.error);
        assertThat(secondInvocationResult.getErrorMessage()).startsWith(
                "Failed to fetch price floor from provider for fetch.url: 'http://test.host.com', "
                        + "account = 1001 with a reason : Failed to parse price floor response for account 1001, "
                        + "cause: DecodeException: Failed to decode:");
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldReturnEmptyRulesWithErrorStatusForSecondCallAndCreatePeriodicTimerWhenResponseBodyIsEmpty() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), null)));

        // when
        final FetchResult firstInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.error(
                "Failed to fetch price floor from provider for fetch.url: 'http://test.host.com', "
                        + "account = 1001 with a reason : Failed to parse price floor response for account 1001, "
                        + "response body can not be empty "));
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldReturnEmptyRulesWithErrorStatusForSecondCallAndCreatePeriodicTimerWhenCantResolveRules() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(
                        HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), null)));

        // when
        final FetchResult firstInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.error(
                "Failed to fetch price floor from provider for fetch.url: 'http://test.host.com', "
                        + "account = 1001 with a reason : Failed to parse price floor response for account 1001, "
                        + "response body can not be empty "));
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldNotCallPriceFloorProviderWhileFetchIsAlreadyInProgress() {
        // given
        final Promise<HttpClientResponse> fetchPromise = Promise.promise();
        given(httpClient.get(anyString(), anyLong(), anyLong())).willReturn(fetchPromise.future());

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));
        final FetchResult secondFetch = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        verifyNoMoreInteractions(httpClient);

        assertThat(secondFetch).isEqualTo(FetchResult.inProgress());

        fetchPromise.tryComplete(
                HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap()
                                .add(HttpHeaders.CACHE_CONTROL, "max-age==3"),
                        jacksonMapper.encodeToString(givenPriceFloorData())));

        final PriceFloorData thirdFetch = priceFloorFetcher.fetch(givenAccount(identity())).getRulesData();
        assertThat(thirdFetch).isEqualTo(givenPriceFloorData());
    }

    @Test
    public void fetchShouldReturnNullAndCreatePeriodicTimerWhenResponseExceededRulesNumber() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(PriceFloorData.builder()
                                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                        .value("video", BigDecimal.ONE).value("banner", BigDecimal.TEN)
                                        .build()))
                                .build()))));

        // when
        final FetchResult firstInvocationResult =
                priceFloorFetcher.fetch(givenAccount(account -> account.maxRules(1L)));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.error(
                "Failed to fetch price floor from provider for fetch.url: 'http://test.host.com', "
                        + "account = 1001 with a reason : "
                        + "Price floor rules number 2 exceeded its maximum number 1 "));
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldReturnNullAndCreatePeriodicTimerWhenResponseExceededDimensionsNumber() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(PriceFloorData.builder()
                                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                        .schema(PriceFloorSchema.of("|", List.of(siteDomain, domain)))
                                        .build()))
                                .build()))));

        // when
        final FetchResult firstInvocationResult =
                priceFloorFetcher.fetch(givenAccount(account -> account.maxSchemaDims(1L)));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(firstInvocationResult).isEqualTo(FetchResult.inProgress());
        verify(vertx).setTimer(eq(1200000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        final FetchResult secondInvocationResult = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(secondInvocationResult).isEqualTo(FetchResult.error(
                "Failed to fetch price floor from provider for fetch.url: 'http://test.host.com', "
                        + "account = 1001 with a reason : "
                        + "Price floor rules values can't be null or empty, but were {} "));
        verifyNoMoreInteractions(vertx);
    }

    private Account givenAccount(UnaryOperator<
            AccountPriceFloorsFetchConfig.AccountPriceFloorsFetchConfigBuilder> configCustomizer) {

        return Account.builder()
                .id("1001")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder().fetch(givenFetchConfig(configCustomizer))
                                .build())
                        .build())
                .build();
    }

    private static AccountPriceFloorsFetchConfig givenFetchConfig(
            UnaryOperator<AccountPriceFloorsFetchConfig.AccountPriceFloorsFetchConfigBuilder> configCustomizer) {

        return configCustomizer.apply(AccountPriceFloorsFetchConfig.builder()
                        .enabled(true)
                        .url("http://test.host.com")
                        .maxRules(10L)
                        .maxSchemaDims(10L)
                        .maxFileSizeKb(10L)
                        .timeoutMs(1300L)
                        .maxAgeSec(1500L)
                        .periodSec(1200L))
                .build();
    }

    private PriceFloorData givenPriceFloorData() {
        return PriceFloorData.builder()
                .currency("USD")
                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                        .modelVersion("model version 1.0")
                        .schema(PriceFloorSchema.of("|", singletonList(mediaType)))
                        .value("banner", BigDecimal.TEN)
                        .currency("EUR").build()))
                .build();
    }
}
