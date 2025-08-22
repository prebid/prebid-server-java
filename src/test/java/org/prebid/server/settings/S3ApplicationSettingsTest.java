package org.prebid.server.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class S3ApplicationSettingsTest extends VertxTest {

    private static final String BUCKET = "bucket";
    private static final String ACCOUNTS_DIR = "accounts";
    private static final String STORED_IMPS_DIR = "stored-imps";
    private static final String STORED_REQUESTS_DIR = "stored-requests";
    private static final String STORED_RESPONSES_DIR = "stored-responses";

    @Mock
    private S3AsyncClient s3AsyncClient;

    private Vertx vertx;

    private S3ApplicationSettings target;

    @Mock
    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        target = new S3ApplicationSettings(
                s3AsyncClient,
                BUCKET,
                ACCOUNTS_DIR,
                STORED_IMPS_DIR,
                STORED_REQUESTS_DIR,
                STORED_RESPONSES_DIR,
                jacksonMapper,
                vertx);

        given(timeout.remaining()).willReturn(500L);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void getAccountByIdShouldReturnFetchedAccount(VertxTestContext context) throws JsonProcessingException {
        // given
        final Account account = Account.builder().id("accountId").build();

        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(ACCOUNTS_DIR, "accountId"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                mapper.writeValueAsString(account).getBytes())));

        // when
        final Future<Account> result = target.getAccountById("accountId", timeout);

        // then
        result.onComplete(context.succeeding(returnedAccount -> {
            assertThat(returnedAccount.getId()).isEqualTo("accountId");
            context.completeNow();
        }));
    }

    @Test
    public void getAccountByIdShouldReturnTimeout(VertxTestContext context) {
        // given
        given(timeout.remaining()).willReturn(-1L);

        // when
        final Future<Account> result = target.getAccountById("account", timeout);

        // then
        result.onComplete(context.failing(cause -> {
            assertThat(cause)
                    .isInstanceOf(TimeoutException.class)
                    .hasMessage("Timeout has been exceeded");

            context.completeNow();
        }));
    }

    @Test
    public void getAccountByIdShouldReturnAccountNotFound(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.failedFuture(
                        NoSuchKeyException.create(
                                "The specified key does not exist.",
                                new IllegalStateException("error"))));

        // when
        final Future<Account> result = target.getAccountById("notFoundId", timeout);

        // then
        result.onComplete(context.failing(cause -> {
            assertThat(cause)
                    .isInstanceOf(PreBidException.class)
                    .hasMessage("Account with id notFoundId not found");

            context.completeNow();
        }));
    }

    @Test
    public void getAccountByIdShouldReturnInvalidJson(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "invalidJson".getBytes())));

        // when
        final Future<Account> result = target.getAccountById("invalidJsonId", timeout);

        // then
        result.onComplete(context.failing(cause -> {
            assertThat(cause)
                    .isInstanceOf(PreBidException.class)
                    .hasMessage("Invalid json for account with id invalidJsonId");

            context.completeNow();
        }));
    }

    @Test
    public void getAccountByIdShouldReturnAccountIdMismatch(VertxTestContext context) throws JsonProcessingException {
        // given
        final Account account = Account.builder().id("accountId").build();

        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(ACCOUNTS_DIR, "anotherAccountId"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                mapper.writeValueAsString(account).getBytes())));

        // when
        final Future<Account> result = target.getAccountById("anotherAccountId", timeout);

        // then
        result.onComplete(context.failing(cause -> {
            assertThat(cause)
                    .isInstanceOf(PreBidException.class)
                    .hasMessage("Account with id anotherAccountId does not match id accountId in file");

            context.completeNow();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredRequest(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_REQUESTS_DIR, "request"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "storedRequest".getBytes())));

        // when
        final Future<StoredDataResult<String>> result = target.getStoredData(
                "accountId", Set.of("request"), emptySet(), timeout);

        // then
        result.onComplete(context.succeeding(storedDataResult -> {
            assertThat(storedDataResult.getStoredIdToRequest()).isEqualTo(Map.of("request", "storedRequest"));
            assertThat(storedDataResult.getStoredIdToImp()).isEmpty();
            assertThat(storedDataResult.getErrors()).isEmpty();

            context.completeNow();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredImpression(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_IMPS_DIR, "imp"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "storedImp".getBytes())));

        // when
        final Future<StoredDataResult<String>> result = target.getStoredData(
                "accountId", emptySet(), Set.of("imp"), timeout);

        // then
        result.onComplete(context.succeeding(storedDataResult -> {
            assertThat(storedDataResult.getStoredIdToRequest()).isEmpty();
            assertThat(storedDataResult.getStoredIdToImp()).isEqualTo(Map.of("imp", "storedImp"));
            assertThat(storedDataResult.getErrors()).isEmpty();

            context.completeNow();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredImpressionWithAdUnitPath(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_IMPS_DIR, "imp"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "storedImp".getBytes())));

        // when
        final Future<StoredDataResult<String>> result = target.getStoredData(
                "accountId", emptySet(), Set.of("/imp"), timeout);

        // then
        result.onComplete(context.succeeding(storedDataResult -> {
            assertThat(storedDataResult.getStoredIdToRequest()).isEmpty();
            assertThat(storedDataResult.getStoredIdToImp()).isEqualTo(Map.of("/imp", "storedImp"));
            assertThat(storedDataResult.getErrors()).isEmpty();

            context.completeNow();
        }));
    }

    @Test
    public void getStoredDataShouldReturnFetchedStoredRequestAndStoredImpression(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_REQUESTS_DIR, "request"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "storedRequest".getBytes())));
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_IMPS_DIR, "imp"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "storedImp".getBytes())));

        // when
        final Future<StoredDataResult<String>> result = target.getStoredData(
                "accountId", Set.of("request"), Set.of("imp"), timeout);

        // then
        result.onComplete(context.succeeding(storedDataResult -> {
            assertThat(storedDataResult.getStoredIdToRequest()).isEqualTo(Map.of("request", "storedRequest"));
            assertThat(storedDataResult.getStoredIdToImp()).isEqualTo(Map.of("imp", "storedImp"));
            assertThat(storedDataResult.getErrors()).isEmpty();

            context.completeNow();
        }));
    }

    @Test
    public void getStoredDataShouldReturnErrorsForNotFoundRequests(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.failedFuture(
                        NoSuchKeyException.create(
                                "The specified key does not exist.",
                                new IllegalStateException("error"))));

        // when
        final Future<StoredDataResult<String>> result = target.getStoredData(
                "accountId", Set.of("request"), emptySet(), timeout);

        // then
        result.onComplete(context.succeeding(storedDataResult -> {
            assertThat(storedDataResult.getStoredIdToImp()).isEmpty();
            assertThat(storedDataResult.getStoredIdToRequest()).isEmpty();
            assertThat(storedDataResult.getErrors())
                    .isEqualTo(singletonList("No stored request found for id: request"));

            context.completeNow();
        }));
    }

    @Test
    public void getStoredDataShouldReturnErrorsForNotFoundImpressions(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.failedFuture(
                        NoSuchKeyException.create(
                                "The specified key does not exist.",
                                new IllegalStateException("error"))));

        // when
        final Future<StoredDataResult<String>> result = target.getStoredData(
                "accountId", emptySet(), Set.of("imp"), timeout);

        // then
        result.onComplete(context.succeeding(storedDataResult -> {
            assertThat(storedDataResult.getStoredIdToImp()).isEmpty();
            assertThat(storedDataResult.getStoredIdToRequest()).isEmpty();
            assertThat(storedDataResult.getErrors()).isEqualTo(singletonList("No stored impression found for id: imp"));

            context.completeNow();
        }));
    }

    @Test
    public void getStoredResponsesShouldReturnExpectedResult(VertxTestContext context) {
        // given
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_RESPONSES_DIR, "response1"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.completedFuture(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(),
                                "storedResponse1".getBytes())));
        given(s3AsyncClient.getObject(
                eq(GetObjectRequest.builder()
                        .bucket(BUCKET)
                        .key("%s/%s.json".formatted(STORED_RESPONSES_DIR, "response2"))
                        .build()),
                any(AsyncResponseTransformer.class)))
                .willReturn(CompletableFuture.failedFuture(
                        NoSuchKeyException.create(
                                "The specified key does not exist.",
                                new IllegalStateException("error"))));

        // when
        final Future<StoredResponseDataResult> result = target.getStoredResponses(
                Set.of("response1", "response2"), timeout);

        // then
        result.onComplete(context.succeeding(storedResponseDataResult -> {
            assertThat(storedResponseDataResult.getIdToStoredResponses())
                    .isEqualTo(Map.of("response1", "storedResponse1"));
            assertThat(storedResponseDataResult.getErrors())
                    .isEqualTo(singletonList("No stored response found for id: response2"));

            context.completeNow();
        }));
    }
}
