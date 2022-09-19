package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.StoredDataResult;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class S3ApplicationSettingsTest extends VertxTest {

    private static final String BUCKET = "bucket";
    private static final String ACCOUNTS_DIR = "accounts";
    private static final String STORED_IMPS_DIR = "stored-imps";
    private static final String STORED_REQUESTS_DIR = "stored-requests";
    private static final String STORED_RESPONSES_DIR = "stored-responses";
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    private Timeout timeout;

    @Mock
    private S3AsyncClient s3AsyncClient;
    private Vertx vertx;

    private S3ApplicationSettings s3ApplicationSettings;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        s3ApplicationSettings = new S3ApplicationSettings(s3AsyncClient, BUCKET, ACCOUNTS_DIR,
                STORED_IMPS_DIR, STORED_REQUESTS_DIR, STORED_RESPONSES_DIR, jacksonMapper, vertx);

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        timeout = timeoutFactory.create(500L);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void getAccountByIdShouldReturnFetchedAccount(TestContext context) throws JsonProcessingException {
        // given
        final Account account = Account.builder()
                .id("someId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("testPriceGranularity")
                        .build())
                .privacy(AccountPrivacyConfig.of(null, null, null, null))
                .build();

        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                mapper.writeValueAsString(account).getBytes())));

        // when
        final Future<Account> future = s3ApplicationSettings.getAccountById("someId", timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(returnedAccount -> {
            assertThat(returnedAccount.getId()).isEqualTo("someId");
            assertThat(returnedAccount.getAuction().getPriceGranularity()).isEqualTo("testPriceGranularity");

            verify(s3AsyncClient).getObject(
                    eq(GetObjectRequest.builder().bucket(BUCKET).key(ACCOUNTS_DIR + "/someId.json").build()),
                    any(AsyncResponseTransformer.class));
            async.complete();
        }));
    }

    @Test
    public void getAccountByIdNoSuchKey(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.failedFuture(
                        NoSuchKeyException.create(
                                "The specified key does not exist.",
                                new IllegalStateException(""))));

        // when
        final Future<Account> future = s3ApplicationSettings.getAccountById("notFoundId", timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertFailure(cause -> {
            assertThat(cause)
                    .isInstanceOf(PreBidException.class)
                    .hasMessage("Account with id notFoundId not found");

            async.complete();
        }));
    }

    @Test
    public void getAccountByIdInvalidJson(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "invalidJson".getBytes())));

        // when
        final Future<Account> future = s3ApplicationSettings.getAccountById("invalidJsonId", timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertFailure(cause -> {
            assertThat(cause)
                    .isInstanceOf(PreBidException.class)
                    .hasMessage("Invalid json for account with id invalidJsonId");
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredRequest(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "req1Result".getBytes())));

        // when
        final Future<StoredDataResult> future = s3ApplicationSettings
                .getStoredData("someId", Set.of("req1"), Collections.emptySet(), timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(account -> {
            assertThat(account.getStoredIdToRequest().size()).isEqualTo(1);
            assertThat(account.getStoredIdToImp().size()).isEqualTo(0);
            assertThat(account.getStoredIdToRequest()).isEqualTo(Map.of("req1", "req1Result"));
            assertThat(account.getErrors()).isEmpty();

            verify(s3AsyncClient).getObject(
                    eq(GetObjectRequest.builder().bucket(BUCKET).key(STORED_REQUESTS_DIR + "/req1.json").build()),
                    any(AsyncResponseTransformer.class));

            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredImpression(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "imp1Result".getBytes())));

        // when
        final Future<StoredDataResult> future = s3ApplicationSettings
                .getStoredData("someId", Collections.emptySet(), Set.of("imp1"), timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(account -> {
            assertThat(account.getStoredIdToRequest().size()).isEqualTo(0);
            assertThat(account.getStoredIdToImp().size()).isEqualTo(1);
            assertThat(account.getStoredIdToImp()).isEqualTo(Map.of("imp1", "imp1Result"));
            assertThat(account.getErrors()).isEmpty();

            verify(s3AsyncClient).getObject(
                    eq(GetObjectRequest.builder().bucket(BUCKET).key(STORED_IMPS_DIR + "/imp1.json").build()),
                    any(AsyncResponseTransformer.class));

            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredImpressionWithAdUnitPathStoredId(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "imp1Result".getBytes())));

        // when
        final Future<StoredDataResult> future = s3ApplicationSettings
                .getStoredData("/123/root/position-1", Collections.emptySet(), Set.of("imp1"), timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(account -> {
            assertThat(account.getStoredIdToRequest().size()).isEqualTo(0);
            assertThat(account.getStoredIdToImp().size()).isEqualTo(1);
            assertThat(account.getStoredIdToImp()).isEqualTo(Map.of("imp1", "imp1Result"));
            assertThat(account.getErrors()).isEmpty();

            verify(s3AsyncClient).getObject(
                    eq(GetObjectRequest.builder().bucket(BUCKET).key(STORED_IMPS_DIR + "/imp1.json").build()),
                    any(AsyncResponseTransformer.class));

            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredImpressionAndStoredRequest(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(
                        CompletableFuture.completedFuture(
                                ResponseBytes.fromByteArray(
                                        GetObjectResponse.builder().build(),
                                        "req1Result".getBytes())),
                        CompletableFuture.completedFuture(
                                ResponseBytes.fromByteArray(
                                        GetObjectResponse.builder().build(),
                                        "imp1Result".getBytes())));

        // when
        final Future<StoredDataResult> future = s3ApplicationSettings
                .getStoredData("someId", Set.of("req1"), Set.of("imp1"), timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(account -> {
            assertThat(account.getStoredIdToRequest().size()).isEqualTo(1);
            assertThat(account.getStoredIdToRequest()).isEqualTo(Map.of("req1", "req1Result"));
            assertThat(account.getStoredIdToImp().size()).isEqualTo(1);
            assertThat(account.getStoredIdToImp()).isEqualTo(Map.of("imp1", "imp1Result"));
            assertThat(account.getErrors()).isEmpty();

            verify(s3AsyncClient).getObject(
                    eq(GetObjectRequest.builder().bucket(BUCKET).key(STORED_IMPS_DIR + "/imp1.json").build()),
                    any(AsyncResponseTransformer.class));
            verify(s3AsyncClient).getObject(
                    eq(GetObjectRequest.builder().bucket(BUCKET).key(STORED_REQUESTS_DIR + "/req1.json").build()),
                    any(AsyncResponseTransformer.class));

            async.complete();
        }));
    }

    @Test
    public void getStoredDataReturnsErrorsForNotFoundRequests(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.failedFuture(
                        NoSuchKeyException.create(
                                "The specified key does not exist.",
                                new IllegalStateException(""))));

        // when
        final Future<StoredDataResult> future = s3ApplicationSettings
                .getStoredData("someId", Set.of("req1"), Collections.emptySet(), timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(account -> {
            assertThat(account.getStoredIdToImp()).isEmpty();
            assertThat(account.getStoredIdToRequest()).isEmpty();
            assertThat(account.getErrors().size()).isEqualTo(1);
            assertThat(account.getErrors())
                    .isNotNull()
                    .hasSize(1)
                    .isEqualTo(singletonList("No stored request found for id: req1"));

            async.complete();
        }));
    }

    @Test
    public void getStoredDataReturnsErrorsForNotFoundImpressions(TestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(
                        CompletableFuture.failedFuture(
                                NoSuchKeyException.create(
                                        "The specified key does not exist.",
                                        new IllegalStateException(""))));

        // when
        final Future<StoredDataResult> future = s3ApplicationSettings
                .getStoredData("someId", Collections.emptySet(), Set.of("imp1"), timeout);

        // then
        final Async async = context.async();

        future.onComplete(context.asyncAssertSuccess(account -> {
            assertThat(account.getStoredIdToImp()).isEmpty();
            assertThat(account.getStoredIdToRequest()).isEmpty();
            assertThat(account.getErrors().size()).isEqualTo(1);
            assertThat(account.getErrors())
                    .isNotNull()
                    .hasSize(1)
                    .isEqualTo(singletonList("No stored impression found for id: imp1"));

            async.complete();
        }));
    }

}
