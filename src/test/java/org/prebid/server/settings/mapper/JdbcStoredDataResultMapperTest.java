package org.prebid.server.settings.mapper;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
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
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps found");
    }

    @Test
    public void mapShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResultForGivenIDs() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet,
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
        given(resultSet.getResults()).willReturn(singletonList(new JsonArray(Arrays.asList("id1", "data"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Result set column number is less than expected");
    }

    @Test
    public void mapShouldReturnStoredResultWithErrorForMissingID() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(new JsonArray(Arrays.asList("id1", "data", "request"))));

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet, singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id1", "data"));
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored imp found for id: id2");
    }

    @Test
    public void mapSkipResultSetWithInvalidType() {
        // given
        final List<JsonArray> jsonArrays = Arrays.asList(
                new JsonArray(Arrays.asList("id1", "data", "request")),
                new JsonArray(Arrays.asList("id2", "data", "invalid")));

        given(resultSet.getResults()).willReturn(jsonArrays);

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id1", "data"));
    }

    @Test
    public void mapShouldReturnStoredResultWithExpectedResult() {
        // given
        final List<JsonArray> jsonArrays = Arrays.asList(
                new JsonArray(Arrays.asList("id1", "data", "request")),
                new JsonArray(Arrays.asList("id2", "data", "imp")));

        given(resultSet.getResults()).willReturn(jsonArrays);

        // when
        final StoredDataResult result = JdbcStoredDataResultMapper.map(resultSet);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getStoredIdToImp()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id2", "data"));
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id1", "data"));
    }
}
