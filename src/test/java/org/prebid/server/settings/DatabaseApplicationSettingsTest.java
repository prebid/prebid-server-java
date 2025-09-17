package org.prebid.server.settings;

<<<<<<< HEAD
import com.fasterxml.jackson.databind.node.TextNode;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.settings.helper.ParametrizedQueryHelper;
import org.prebid.server.settings.model.Account;
<<<<<<< HEAD
import org.prebid.server.settings.model.Profile;
=======
>>>>>>> 04d9d4a13 (Initial commit)
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

@ExtendWith(MockitoExtension.class)

public class DatabaseApplicationSettingsTest extends VertxTest {

    private static final String SELECT_ACCOUNT_QUERY =
            "SELECT config FROM accounts_account where uuid = %ACCOUNT_ID% LIMIT 1";

<<<<<<< HEAD
    private static final String SELECT_QUERY = """
            SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests \
            WHERE reqid IN (%REQUEST_ID_LIST%) \
            UNION ALL \
            SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps \
            WHERE impid IN (%IMP_ID_LIST%)
            """;

    private static final String SELECT_PROFILES_QUERY = """
               SELECT accountId, profileId, profile, mergePrecedence, type \
               FROM profiles \
               WHERE profileId in (%REQUEST_ID_LIST%, %IMP_ID_LIST%)
            """;

    private static final String SELECT_RESPONSE_QUERY = """
            SELECT responseId, responseData \
            FROM stored_responses \
            WHERE responseId IN (%RESPONSE_ID_LIST%)
            """;
=======
    private static final String SELECT_QUERY =
            "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                    + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                    + "WHERE impid IN (%IMP_ID_LIST%)";

    private static final String SELECT_RESPONSE_QUERY = "SELECT responseId, responseData FROM stored_responses "
            + "WHERE responseId IN (%RESPONSE_ID_LIST%)";
>>>>>>> 04d9d4a13 (Initial commit)

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
<<<<<<< HEAD
                SELECT_PROFILES_QUERY,
=======
>>>>>>> 04d9d4a13 (Initial commit)
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

<<<<<<< HEAD
        final StoredDataResult<String> givenStoredDataResult = StoredDataResult.of(
=======
        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
>>>>>>> 04d9d4a13 (Initial commit)
                Map.of("1", "value1", "2", "value2"),
                Map.of("4", "value4", "5", "value5"),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2", "4", "5")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future = target.getStoredData(
=======
        final Future<StoredDataResult> future = target.getStoredData(
>>>>>>> 04d9d4a13 (Initial commit)
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

<<<<<<< HEAD
        final StoredDataResult<String> givenStoredDataResult = StoredDataResult.of(
=======
        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
>>>>>>> 04d9d4a13 (Initial commit)
                Map.of("1", "value1", "2", "value2"),
                Map.of(),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future = target.getAmpStoredData(
=======
        final Future<StoredDataResult> future = target.getAmpStoredData(
>>>>>>> 04d9d4a13 (Initial commit)
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

<<<<<<< HEAD
        final StoredDataResult<String> givenStoredDataResult = StoredDataResult.of(
=======
        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
>>>>>>> 04d9d4a13 (Initial commit)
                Map.of("1", "value1", "2", "value2"),
                Map.of("4", "value4", "5", "value5"),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2", "4", "5")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future = target.getVideoStoredData("1001",
=======
        final Future<StoredDataResult> future = target.getVideoStoredData("1001",
>>>>>>> 04d9d4a13 (Initial commit)
                new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
<<<<<<< HEAD
    public void getProfilesShouldReturnExpectedResult() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_PROFILES_QUERY, 2, 2))
                .willReturn("query");

        final StoredDataResult<Profile> givenProfilesResult = StoredDataResult.of(
                Map.of("1", givenProfile("value1"), "2", givenProfile("value2")),
                Map.of("4", givenProfile("value4"), "5", givenProfile("value5")),
                emptyList());
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "2", "4", "5")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenProfilesResult));

        // when
        final Future<StoredDataResult<Profile>> future = target.getProfiles(
                "1001", new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenProfilesResult);
    }

    @Test
=======
>>>>>>> 04d9d4a13 (Initial commit)
    public void getStoredDataShouldReturnResultWithError() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_QUERY, 2, 0))
                .willReturn("query");

<<<<<<< HEAD
        final StoredDataResult<String> givenStoredDataResult = StoredDataResult.of(
=======
        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
>>>>>>> 04d9d4a13 (Initial commit)
                Map.of("1", "value1"),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
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

<<<<<<< HEAD
        final StoredDataResult<String> givenStoredDataResult = StoredDataResult.of(
=======
        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
>>>>>>> 04d9d4a13 (Initial commit)
                Map.of("1", "value1"),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
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

<<<<<<< HEAD
        final StoredDataResult<String> givenStoredDataResult = StoredDataResult.of(
=======
        final StoredDataResult givenStoredDataResult = StoredDataResult.of(
>>>>>>> 04d9d4a13 (Initial commit)
                Map.of("1", "value1"),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenStoredDataResult));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                target.getVideoStoredData("1001", new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenStoredDataResult);
    }

    @Test
<<<<<<< HEAD
    public void getProfilesShouldReturnResultWithError() {
        // given
        given(parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(SELECT_PROFILES_QUERY, 2, 0))
                .willReturn("query");

        final StoredDataResult<Profile> givenProfilesResult = StoredDataResult.of(
                Map.of("1", givenProfile("value1")),
                Map.of(),
                List.of("No stored request found for id: 3"));
        given(databaseClient.executeQuery(eq("query"), eq(List.of("1", "3")), any(), eq(timeout)))
                .willReturn(Future.succeededFuture(givenProfilesResult));

        // when
        final Future<StoredDataResult<Profile>> future =
                target.getProfiles("1001", new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(givenProfilesResult);
    }

    @Test
=======
>>>>>>> 04d9d4a13 (Initial commit)
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
<<<<<<< HEAD
        final Future<Map<String, String>> result = target.getCategories("adServer", "publisher", timeout);
=======
        final Future<Map<String, String>> result = target.getCategories("adServer", "publisher",
                timeout);
>>>>>>> 04d9d4a13 (Initial commit)

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Not supported");
    }
<<<<<<< HEAD

    private static Profile givenProfile(String value) {
        return Profile.of(
                Profile.Type.REQUEST,
                Profile.MergePrecedence.PROFILE,
                TextNode.valueOf(value));
    }
=======
>>>>>>> 04d9d4a13 (Initial commit)
}
