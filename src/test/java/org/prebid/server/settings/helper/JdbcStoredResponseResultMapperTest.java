package org.prebid.server.settings.helper;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.AbstractMap;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class JdbcStoredResponseResultMapperTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ResultSet resultSet;

    @Test
    public void mapShouldReturnEmptyStoredResponseResultWithErrorWhenResultSetIsEmpty() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(resultSet, emptySet());

        // then
        assertThat(result.getStoredSeatBid()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored responses found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResponseResultWithErrorWhenResultSetHasLessColumns() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(new JsonArray(singletonList("id1"))));

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(resultSet, emptySet());

        // then
        assertThat(result.getStoredSeatBid()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Result set column number is less than expected");
    }

    @Test
    public void mapShouldReturnStoredResponseResultWithErrorForMissingID() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(new JsonArray(asList("id1", "data"))));

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper
                .map(resultSet, new HashSet<>(asList("id1", "id2")));

        // then
        assertThat(result.getStoredSeatBid()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id1", "data"));
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored response found for id: id2");
    }

    @Test
    public void mapShouldReturnEmptyStoredResponseResultWithErrorWhenResultSetHasEmptyResultForGivenIDs() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(resultSet, singleton("id"));

        // then
        assertThat(result.getStoredSeatBid()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored responses were found for ids: id");
    }

    @Test
    public void mapShouldReturnFilledStoredResponseResultWithoutErrors() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("id1", "data1")),
                new JsonArray(asList("id2", "data2"))));

        // when
        final StoredResponseDataResult result = JdbcStoredResponseResultMapper.map(resultSet,
                new HashSet<>(asList("id1", "id2")));

        // then
        assertThat(result.getStoredSeatBid()).hasSize(2)
                .contains(new AbstractMap.SimpleEntry<>("id1", "data1"), new AbstractMap.SimpleEntry<>("id2", "data2"));
        assertThat(result.getErrors()).isEmpty();
    }
}
