package org.prebid.server.settings.helper;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.settings.model.StoredDataResult;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.BDDMockito.given;

public class JdbcStoredDataResultMapperTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ResultSet resultSet;

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResult() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, null, emptySet(), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps were found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResultForGivenIds() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, null,
                singleton("reqId"), singleton("impId"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests for ids [reqId] and stored imps for ids [impId] were found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasLessColumns() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "accountId",
                singleton("reqId"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasUnexpectedColumnType() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data", 123))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "accountId",
                singleton("reqId"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No stored request found for id: reqId");
    }

    @Test
    public void mapShouldSkipStoredResultWithInvalidType() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id1", "data2", "invalid"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "accountId",
                singleton("id1"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapShouldReturnStoredResultWithErrorForMissingId() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data1", "request"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "accountId", singleton("id1"),
                singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored imp found for id: id2");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorsForMissingIdsIfAccountDiffers() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id2", "data2", "imp"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "otherAccountId",
                singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(
                        "No stored request found for id: id1 for account: otherAccountId",
                        "No stored imp found for id: id2 for account: otherAccountId");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorIfMultipleStoredItemsFoundButNoAccountIdIsDefined() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId1", "id1", "data1", "request")),
                new JsonArray(asList("accountId2", "id1", "data2", "request"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, null,
                singleton("id1"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Multiple stored requests found for id: id1 but no account was specified");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorIfMultipleStoredItemsFoundButNoAccountIdIsDiffers() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId1", "id1", "data-accountId", "request")),
                new JsonArray(asList("accountId2", "id1", "data-otherAccountId", "request")),
                new JsonArray(asList("accountId1", "id2", "data-accountId", "imp")),
                new JsonArray(asList("accountId2", "id2", "data-otherAccountId", "imp"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "otherAccountId",
                singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(
                        "No stored request found among multiple id: id1 for account: otherAccountId",
                        "No stored imp found among multiple id: id2 for account: otherAccountId");
    }

    @Test
    public void mapShouldReturnExpectedStoredResultForGivenAccount() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data-accountId", "request")),
                new JsonArray(asList("otherAccountId", "id1", "data-otherAccountId", "request")),
                new JsonArray(asList("accountId", "id2", "data-accountId", "imp")),
                new JsonArray(asList("otherAccountId", "id2", "data-otherAccountId", "imp"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, "accountId",
                singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data-accountId"));
        assertThat(result.getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("id2", "data-accountId"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapWithoutParamsShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResult() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps were found");
    }

    @Test
    public void mapWithoutParamsShouldSkipStoredResultWithInvalidType() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id2", "data2", "invalid"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapWithoutParamsShouldReturnEmptyStoredResultWithErrorWhenResultSetHasLessColumns() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data");
    }

    @Test
    public void mapWithoutParamsShouldReturnEmptyStoredResultWhenResultSetHasInvalidDataType() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data", 123))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapWithoutParamsShouldReturnExpectedStoredResult() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id2", "data2", "imp"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("id2", "data2"));
        assertThat(result.getErrors()).isEmpty();
    }
}
