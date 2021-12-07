package org.prebid.server.floors;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorField;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.floors.model.PriceFloorSchema;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.settings.model.AccountPriceFloorsFetchConfig;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
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

public class PriceFloorFetcherTest extends VertxTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    @Mock
    private Metrics metrics;

    @Mock
    private HttpClient httpClient;

    @Mock
    private Vertx vertx;

    @Mock
    private TimeoutFactory timeoutFactory;

    private PriceFloorFetcher priceFloorFetcher;

    @Before
    public void setUp() {
        priceFloorFetcher = new PriceFloorFetcher(applicationSettings, metrics,
                vertx, timeoutFactory, httpClient, jacksonMapper);
    }

    @Test
    public void fetchShouldReturnPriceFloorFetchedFromProviderAndCache() {
        // given
        final Account givenAccount = givenAccount(identity());
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(givenPriceFloorRules()))));
        // when
        final Future<PriceFloorRules> priceFloorRules = priceFloorFetcher.fetch(givenAccount);

        // then
        verify(httpClient).get("http://test.host.com", 1300, 10240);
        assertThat(priceFloorRules.succeeded()).isTrue();

        verify(vertx).setTimer(eq(1700000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(httpClient);
        final Future<PriceFloorRules> priceFloorRulesCached = priceFloorFetcher.fetch(givenAccount);
        assertThat(priceFloorRulesCached.succeeded()).isTrue();
        assertThat(priceFloorRulesCached.result()).isEqualTo(givenPriceFloorRules());
    }

    @Test
    public void fetchShouldReturnSucceededFutureForTheFirstInvocation() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.failedFuture(new PreBidException("failed")));

        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verify(vertx).setTimer(eq(1700000L), any());
    }

    @Test
    public void fetchShouldCacheResponseForTimeFromResponseCacheControlHeader() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CACHE_CONTROL, "max-age=700"),
                        jacksonMapper.encodeToString(givenPriceFloorRules()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(700000L), any());
    }

    @Test
    public void fetchShouldSetDefaultCacheTimeWhenCacheControlHeaderCantBeParsed() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200,
                        MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CACHE_CONTROL, "invalid"),
                        jacksonMapper.encodeToString(givenPriceFloorRules()))));

        // when
        priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(vertx).setTimer(eq(1700000L), any());
    }

    @Test
    public void fetchShouldNotPrepareAnyRequestsWhenFetchUrlIsNotDefined() {
        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(config -> config.url(null)));

        // then
        verifyNoInteractions(httpClient);
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verifyNoInteractions(vertx);
    }

    @Test
    public void fetchShouldNotPrepareAnyRequestsWhenFetchUrlIsMalformed() {
        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(config -> config.url("MalformedURl")));

        // then
        verifyNoInteractions(httpClient);
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verifyNoInteractions(vertx);
    }

    @Test
    public void fetchShouldNotReturnFailedFutureAndCreatePeriodicTimerWhenResponseIsNot200Ok() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(PriceFloorRules.builder().build()))));

        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verify(vertx).setTimer(eq(1700000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldNotReturnFailedFutureAndCreatePeriodicTimerWhenResponseHasInvalidFormat() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        "{")));

        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verify(vertx).setTimer(eq(1700000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldNotReturnFailedFutureAndCreatePeriodicTimerWhenResponseBodyIsEmpty() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        null)));

        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verify(vertx).setTimer(eq(1700000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldNotReturnFailedFutureAndCreatePeriodicTimerWhenCantResolveRules() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        null)));

        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verify(vertx).setTimer(eq(1700000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(vertx);
    }

    @Test
    public void fetchShouldNotCallPriceFloorProviderWhileFetchIsAlreadyInProgress() {
        // given
        final Promise<HttpClientResponse> fetchPromise = Promise.promise();
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(fetchPromise.future());

        // when
        final Future<PriceFloorRules> firstFetch = priceFloorFetcher.fetch(givenAccount(identity()));
        final Future<PriceFloorRules> secondFetch = priceFloorFetcher.fetch(givenAccount(identity()));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        verifyNoMoreInteractions(httpClient);

        assertThat(secondFetch.succeeded()).isTrue();
        assertThat(secondFetch.result()).isNull();

        fetchPromise.tryComplete(HttpClientResponse.of(200,
                MultiMap.caseInsensitiveMultiMap().add(HttpHeaders.CACHE_CONTROL, "max-age==3"),
                jacksonMapper.encodeToString(givenPriceFloorRules())));

        assertThat(firstFetch.succeeded()).isTrue();

        final Future<PriceFloorRules> thirdFetch = priceFloorFetcher.fetch(givenAccount(identity()));
        assertThat(thirdFetch.succeeded()).isTrue();
        assertThat(thirdFetch.result()).isEqualTo(givenPriceFloorRules());
    }

    @Test
    public void fetchShouldReturnNullAndCreatePeriodicTimerWhenResponseExceededRulesNumber() {
        // given
        given(httpClient.get(anyString(), anyLong(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        jacksonMapper.encodeToString(
                                givenPriceFloorRules()
                                        .toBuilder()
                                        .data(PriceFloorData.builder()
                                                .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                                        .value("video", BigDecimal.ONE)
                                                        .value("banner", BigDecimal.TEN)
                                                        .build()))
                                                .build())
                                        .build()))));

        // when
        final Future<PriceFloorRules> priceFloorRules =
                priceFloorFetcher.fetch(givenAccount(account -> account.maxRules(1)));

        // then
        verify(httpClient).get(anyString(), anyLong(), anyLong());
        assertThat(priceFloorRules.succeeded()).isTrue();
        assertThat(priceFloorRules.result()).isNull();
        verify(vertx).setTimer(eq(1700000L), any());
        verify(vertx).setTimer(eq(1500000L), any());
        verifyNoMoreInteractions(vertx);
    }

    private Account givenAccount(UnaryOperator<AccountPriceFloorsFetchConfig.AccountPriceFloorsFetchConfigBuilder>
                                         configCustomizer) {

        return Account.builder()
                .id("1001")
                .auction(AccountAuctionConfig.builder()
                        .priceFloors(AccountPriceFloorsConfig.builder()
                                .fetch(givenFetchConfig(configCustomizer))
                                .build())
                        .build())
                .build();
    }

    private static AccountPriceFloorsFetchConfig givenFetchConfig(
            UnaryOperator<AccountPriceFloorsFetchConfig.AccountPriceFloorsFetchConfigBuilder> configCustomizer) {

        return configCustomizer.apply(AccountPriceFloorsFetchConfig.builder()
                        .enabled(true)
                        .url("http://test.host.com")
                        .maxRules(10)
                        .maxFileSize(10L)
                        .timeout(1300L)
                        .maxAgeSec(1500)
                        .periodSec(1700))
                .build();
    }

    private PriceFloorRules givenPriceFloorRules() {
        return PriceFloorRules.builder()
                .data(PriceFloorData.builder().currency("USD")
                        .modelGroups(singletonList(PriceFloorModelGroup.builder()
                                .modelVersion("model version 1.0")
                                .schema(PriceFloorSchema.of("|", singletonList(PriceFloorField.mediaType)))
                                .value("banner", BigDecimal.TEN)
                                .currency("EUR").build()))
                        .build())
                .skipRate(60)
                .build();
    }
}
