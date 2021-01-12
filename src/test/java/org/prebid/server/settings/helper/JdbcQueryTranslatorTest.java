package org.prebid.server.settings.helper;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.settings.jdbc.JdbcQueryTranslator;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.AbstractMap;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.BDDMockito.given;

public class JdbcQueryTranslatorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ResultSet resultSet;

    private final JdbcQueryTranslator jdbcQueryTranslator = new JdbcQueryTranslator("", "", "", "", jacksonMapper);

    @Test
    public void translateToStoredDataShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResult() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, null, emptySet(), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps were found");
    }

    @Test
    public void translateToStoredDataShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResultForGivenIds() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, null, singleton("reqId"), singleton("impId"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests for ids [reqId] and stored imps for ids [impId] were found");
    }

    @Test
    public void translateToStoredDataShouldReturnEmptyStoredResultWithErrorWhenResultSetHasLessColumns() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "accountId", singleton("reqId"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data");
    }

    @Test
    public void translateToStoredDataShouldReturnEmptyStoredResultWithErrorWhenResultSetHasUnexpectedColumnType() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data", 123))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "accountId", singleton("reqId"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data");
    }

    @Test
    public void translateToStoredDataShouldSkipStoredResultWithInvalidType() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id1", "data2", "invalid"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "accountId", singleton("id1"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void translateToStoredDataShouldReturnStoredResultWithErrorForMissingId() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data1", "request"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "accountId", singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored imp found for id: id2");
    }

    @Test
    public void translateToStoredDataShouldReturnEmptyStoredResultWithErrorsForMissingIdsIfAccountDiffers() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id2", "data2", "imp"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "otherAccountId", singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(
                        "No stored request found for id: id1 for account: otherAccountId",
                        "No stored imp found for id: id2 for account: otherAccountId");
    }

    @Test
    public void translateToStoredDataShouldReturnEmptyResultWithErrorIfMultipleStoredItemsFoundButNoAccountIdDefined() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId1", "id1", "data1", "request")),
                new JsonArray(asList("accountId2", "id1", "data2", "request"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, null, singleton("id1"), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Multiple stored requests found for id: id1 but no account was specified");
    }

    @Test
    public void translateToStoredDataShouldReturnEmptyResultWithErrorIfMultipleStoredItemsFoundButNoAccountIdDiffers() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId1", "id1", "data-accountId", "request")),
                new JsonArray(asList("accountId2", "id1", "data-otherAccountId", "request")),
                new JsonArray(asList("accountId1", "id2", "data-accountId", "imp")),
                new JsonArray(asList("accountId2", "id2", "data-otherAccountId", "imp"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "otherAccountId", singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(
                        "No stored request found among multiple id: id1 for account: otherAccountId",
                        "No stored imp found among multiple id: id2 for account: otherAccountId");
    }

    @Test
    public void translateToStoredDataShouldReturnExpectedStoredResultForGivenAccount() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data-accountId", "request")),
                new JsonArray(asList("otherAccountId", "id1", "data-otherAccountId", "request")),
                new JsonArray(asList("accountId", "id2", "data-accountId", "imp")),
                new JsonArray(asList("otherAccountId", "id2", "data-otherAccountId", "imp"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(
                resultSet, "accountId", singleton("id1"), singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data-accountId"));
        assertThat(result.getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("id2", "data-accountId"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void translateToStoredDataWithoutParamsShouldReturnEmptyStoredResultWithErrorWhenResultSetHasEmptyResult() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored requests or imps were found");
    }

    @Test
    public void translateToStoredDataWithoutParamsShouldSkipStoredResultWithInvalidType() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id2", "data2", "invalid"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void translateToStoredDataWithoutParamsShouldReturnEmptyStoredResultWithErrorWhenResultSetHasLessColumns() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data");
    }

    @Test
    public void translateToStoredDataWithoutParamsShouldReturnEmptyResultWithErrorIfResultSetHasUnexpectedColumnType() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(
                new JsonArray(asList("accountId", "id1", "data", 123))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error occurred while mapping stored request data");
    }

    @Test
    public void translateToStoredDataWithoutParamsShouldReturnExpectedStoredResult() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("accountId", "id1", "data1", "request")),
                new JsonArray(asList("accountId", "id2", "data2", "imp"))));

        // when
        final StoredDataResult result = jdbcQueryTranslator.translateQueryResultToStoredData(resultSet);

        // then
        assertThat(result.getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("id1", "data1"));
        assertThat(result.getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("id2", "data2"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void translateToStoredResponseDataShouldReturnEmptyStoredResponseResultWithErrorWhenResultSetIsEmpty() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredResponseDataResult result = jdbcQueryTranslator.translateQueryResultToStoredResponseData(
                resultSet, emptySet());

        // then
        assertThat(result.getStoredSeatBid()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored responses found");
    }

    @Test
    public void translateToStoredResponseDataShouldReturnEmptyStoredResponseResultWithErrorIfResultSetHasLessColumns() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(new JsonArray(singletonList("id1"))));

        // when
        final StoredResponseDataResult result = jdbcQueryTranslator.translateQueryResultToStoredResponseData(
                resultSet, emptySet());

        // then
        assertThat(result.getStoredSeatBid()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Result set column number is less than expected");
    }

    @Test
    public void translateToStoredResponseDataShouldReturnStoredResponseResultWithErrorForMissingID() {
        // given
        given(resultSet.getResults()).willReturn(singletonList(new JsonArray(asList("id1", "data"))));

        // when
        final StoredResponseDataResult result = jdbcQueryTranslator.translateQueryResultToStoredResponseData(
                resultSet, new HashSet<>(asList("id1", "id2")));

        // then
        assertThat(result.getStoredSeatBid()).hasSize(1)
                .containsOnly(new AbstractMap.SimpleEntry<>("id1", "data"));
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored response found for id: id2");
    }

    @Test
    public void translateToStoredResponseDataShouldReturnEmptyResultWithErrorIfResultSetHasEmptyResultForGivenIDs() {
        // given
        given(resultSet.getResults()).willReturn(emptyList());

        // when
        final StoredResponseDataResult result = jdbcQueryTranslator.translateQueryResultToStoredResponseData(
                resultSet, singleton("id"));

        // then
        assertThat(result.getStoredSeatBid()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("No stored responses were found for ids: id");
    }

    @Test
    public void translateToStoredResponseDataShouldReturnFilledStoredResponseResultWithoutErrors() {
        // given
        given(resultSet.getResults()).willReturn(asList(
                new JsonArray(asList("id1", "data1")),
                new JsonArray(asList("id2", "data2"))));

        // when
        final StoredResponseDataResult result = jdbcQueryTranslator.translateQueryResultToStoredResponseData(
                resultSet, new HashSet<>(asList("id1", "id2")));

        // then
        assertThat(result.getStoredSeatBid()).hasSize(2)
                .contains(new AbstractMap.SimpleEntry<>("id1", "data1"), new AbstractMap.SimpleEntry<>("id2", "data2"));
        assertThat(result.getErrors()).isEmpty();
    }
}
