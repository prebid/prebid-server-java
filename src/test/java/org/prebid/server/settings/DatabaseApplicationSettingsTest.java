package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.helper.ParametrizedQueryHelper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.vertx.database.DatabaseClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@RunWith(VertxUnitRunner.class)
public class DatabaseApplicationSettingsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final String SELECT_ACCOUNT_QUERY =
            "SELECT config FROM accounts_account where uuid = %ACCOUNT_ID% LIMIT 1";

    private static final String SELECT_QUERY =
            "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                    + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                    + "WHERE impid IN (%IMP_ID_LIST%)";

    private static final String SELECT_RESPONSE_QUERY = "SELECT responseId, responseData FROM stored_responses "
            + "WHERE responseId IN (%RESPONSE_ID_LIST%)";

    @Mock
    private ParametrizedQueryHelper parametrizedQueryHelper;

    @Mock
    private DatabaseClient databaseClient;

    private DatabaseApplicationSettings target;

    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(5000L);
        given(parametrizedQueryHelper.replaceAccountIdPlaceholder(SELECT_ACCOUNT_QUERY)).willReturn("query");
        target = new DatabaseApplicationSettings(
                databaseClient,
                jacksonMapper,
                parametrizedQueryHelper,
                SELECT_ACCOUNT_QUERY,
                SELECT_QUERY,
                SELECT_QUERY,
                SELECT_RESPONSE_QUERY);
    }

    @Test
    public void getAccountByIdShouldReturnAccountWithAllFieldsPopulated() {
        // given
        final Account givenAccount = Account.builder().build();
        given(databaseClient.executeQuery(
                eq("query"),
                eq(List.of("1001")),
                any(),
                eq(timeout)))
                .willReturn(Future.succeededFuture(givenAccount));

        // when
        final Future<Account> future = target.getAccountById("1001", timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenAccount);
    }

    @Test
    public void getAccountByIdShouldFailIfAccountNotFound() {
        // given
        given(databaseClient.executeQuery(
                eq("query"),
                eq(List.of("non-existing")),
                any(),
                eq(timeout)))
                .willReturn(Future.succeededFuture(null));

        // when
        final Future<Account> future = target.getAccountById("non-existing", timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("Account not found: non-existing");
    }

    @Test
    public void getStoredDataShouldReturnExpectedResult() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 2))
                .willReturn("query");

        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
                Map.of("1", "value1", "2", "value2"),
                Map.of("4", "value4", "5", "value5"),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2", "4", "5")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
        final Future<StoredDataResult> future = target.getStoredData(
                "1001", new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
    public void getAmpStoredDataShouldReturnExpectedResult() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 0))
                .willReturn("query");

        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
                Map.of("1", "value1", "2", "value2"),
                Map.of(),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
        final Future<StoredDataResult> future = target.getAmpStoredData(
                "1001", new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
    public void getVideoStoredDataShouldReturnExpectedResult() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 2))
                .willReturn("query");

        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
                Map.of("1", "value1", "2", "value2"),
                Map.of("4", "value4", "5", "value5"),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2", "4", "5")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
        final Future<StoredDataResult> future = target.getVideoStoredData("1001",
                new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
    public void getStoredDataShouldReturnResultWithError() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 0))
                .willReturn("query");

        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
                Map.of("1", "value1"),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
        final Future<StoredDataResult> future =
                target.getStoredData("1001", new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
    public void getAmpStoredDataShouldReturnResultWithError() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 0))
                .willReturn("query");

        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
                Map.of("1", "value1"),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
        final Future<StoredDataResult> future =
                target.getAmpStoredData("1001", new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
    public void getVideoStoredDataShouldReturnResultWithError() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 0))
                .willReturn("query");

        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
                Map.of("1", "value1"),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
        final Future<StoredDataResult> future =
                target.getVideoStoredData("1001", new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
    public void getStoredResponseShouldReturnExpectedResult() {
        // given
        given(parametrizedQueryHelper.replaceStoredResponseIdPlaceholders(SELECT_RESPONSE_QUERY, 2))
                .willReturn("query");

        final StoredResponseDataResult givenStoredResponseResult = StoredResponseDataResult.of(
                Map.of("1", "response1"),
                List.of("No stored response found for id: 2"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredResponseResult));

        // when
        final Future<StoredResponseDataResult> future = target.getStoredResponses(
                new HashSet<>(asList("1", "2")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredResponseResult);
    }

    @Test
    public void getCategoriesShouldReturnFailedFutureWithUnsupportedPrebidException() {
        // given and when
        final Future<Map<String, String>> result = target.getCategories("adServer", "publisher",
                timeout);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Not supported");
    }
}
