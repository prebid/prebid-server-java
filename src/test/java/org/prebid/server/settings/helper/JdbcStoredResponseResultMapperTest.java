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
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class JdbcStoredResponseResultMapperTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RowSet<Row> rowSet;

    @Test
    public void mapShouldReturnEmptyStoredResponseResultWithErrorWhenRowSetIsEmpty() {
        // given
        givenRowSet();

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(rowSet, emptySet());

        // then
        assertThat(result.getIdToStoredResponses()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored responses found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResponseResultWithErrorWhenRowSetHasLessColumns() {
        // given
        givenRowSet(givenRow("id1"));

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(rowSet, emptySet());

        // then
        assertThat(result.getIdToStoredResponses()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Result set column number is less than expected");
    }

    @Test
    public void mapShouldReturnStoredResponseResultWithErrorForMissingID() {
        // given
        givenRowSet(givenRow("id1", "data"));

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(rowSet, Set.of("id1", "id2"));

        // then
        assertThat(result.getIdToStoredResponses()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id1", "data"));
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored response found for id: id2");
    }

    @Test
    public void mapShouldReturnEmptyStoredResponseResultWithErrorWhenRowSetHasEmptyResultForGivenIDs() {
        // given
        givenRowSet();

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(rowSet, singleton("id"));

        // then
        assertThat(result.getIdToStoredResponses()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored responses were found for ids: id");
    }

    @Test
    public void mapShouldReturnFilledStoredResponseResultWithoutErrors() {
        // given
        givenRowSet(givenRow("id1", "data1"), givenRow("id2", "data2"));

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(rowSet, Set.of("id1", "id2"));

        // then
        assertThat(result.getIdToStoredResponses()).hasSize(2)
                .contains(new AbstractMap.SimpleEntry<>("id1", "data1"), new AbstractMap.SimpleEntry<>("id2", "data2"));
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
