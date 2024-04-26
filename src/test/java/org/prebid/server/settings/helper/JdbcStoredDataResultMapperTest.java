package org.prebid.server.settings.helper;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import lombok.Value;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.IntStream;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class JdbcStoredDataResultMapperTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RowSet<Row> rowSet;

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResult() {
        // given
        givenRowSet();

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(rowSet, null, emptySet(), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps were found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResultForGivenIds() {
        // given
        givenRowSet();

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                null,
                singleton("reqId"),
                singleton("impId"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests for ids [reqId] and stored imps for ids [impId] were found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasLessColumns() {
        // given
        givenRowSet(givenRow("accountId", "id1", "data"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "accountId",
                singleton("reqId"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data: some columns are missing");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasUnexpectedColumnType() {
        // given
        givenRowSet(givenRow("accountId", "id1", "data", 123));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "accountId",
                singleton("reqId"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No stored request found for id: reqId");
    }

    @Test
    public void mapShouldSkipStoredResultWithInvalidType() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "data1", "request"),
                givenRow("accountId", "id1", "data2", "invalid"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapShouldReturnStoredResultWithErrorForMissingId() {
        // given
        givenRowSet(givenRow("accountId", "id1", "data1", "request"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
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
        givenRowSet(
                givenRow("accountId", "id1", "data1", "request"),
                givenRow("accountId", "id2", "data2", "imp"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "otherAccountId",
                singleton("id1"),
                singleton("id2"));

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
        givenRowSet(
                givenRow("accountId1", "id1", "data1", "request"),
                givenRow("accountId2", "id1", "data2", "request"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                null,
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Multiple stored requests found for id: id1 but no account was specified");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorIfMultipleStoredItemsFoundButNoAccountIdIsDiffers() {
        // given
        givenRowSet(
                givenRow("accountId1", "id1", "data-accountId", "request"),
                givenRow("accountId2", "id1", "data-otherAccountId", "request"),
                givenRow("accountId1", "id2", "data-accountId", "imp"),
                givenRow("accountId2", "id2", "data-otherAccountId", "imp"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "otherAccountId",
                singleton("id1"),
                singleton("id2"));

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
        givenRowSet(
                givenRow("accountId", "id1", "data-accountId", "request"),
                givenRow("otherAccountId", "id1", "data-otherAccountId", "request"),
                givenRow("accountId", "id2", "data-accountId", "imp"),
                givenRow("otherAccountId", "id2", "data-otherAccountId", "imp"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                singleton("id2"));

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
        givenRowSet();

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps were found");
    }

    @Test
    public void mapWithoutParamsShouldSkipStoredResultWithInvalidType() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "data1", "request"),
                givenRow("accountId", "id2", "data2", "invalid"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapWithoutParamsShouldReturnEmptyStoredResultWithErrorWhenResultSetHasLessColumns() {
        // given
        givenRowSet(givenRow("accountId", "id1", "data"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data: some columns are missing");
    }

    @Test
    public void mapWithoutParamsShouldReturnEmptyStoredResultWhenResultSetHasInvalidDataType() {
        // given
        givenRowSet(givenRow("accountId", "id1", "data", 123));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapWithoutParamsShouldReturnExpectedStoredResult() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "data1", "request"),
                givenRow("accountId", "id2", "data2", "imp"));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("id2", "data2"));
        assertThat(result.getErrors()).isEmpty();
    }

    private void givenRowSet(Row... rows) {
        given(rowSet.iterator()).willReturn(CustomRowIterator.of(Arrays.asList(rows).iterator()));
    }

    private Row givenRow(Object... values) {
        final Row row = mock(Row.class);
        given(row.getValue(anyInt())).willAnswer(invocation -> values[(Integer) invocation.getArgument(0)]);
        final JsonObject json = new JsonObject();
        IntStream.range(0, values.length).forEach(i -> json.put(String.valueOf(i), values[i]));
        given(row.toJson()).willReturn(json);
        return row;
    }

    @Value(staticConstructor = "of")
    private static class CustomRowIterator implements RowIterator<Row> {

        Iterator<Row> delegate;

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Row next() {
            return delegate.next();
        }
    }
}
