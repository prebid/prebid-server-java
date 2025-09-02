package org.prebid.server.settings.helper;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Arrays;
import java.util.Iterator;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class DatabaseProfilesResultMapperTest extends VertxTest {

    @Mock
    private RowSet<Row> rowSet;

    @Test
    public void mapShouldReturnEmptyProfilesWithErrorWhenResultSetHasEmptyResult() {
        // given
        givenRowSet();

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(rowSet, null, emptySet(), emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No profiles were found");
    }

    @Test
    public void mapShouldReturnEmptyProfilesWithErrorWhenResultSetHasEmptyResultForGivenIds() {
        // given
        givenRowSet();

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                null,
                singleton("reqId"),
                singleton("impId"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No request profiles for ids [reqId] and imp profiles for ids [impId] were found");
    }

    @Test
    public void mapShouldReturnEmptyProfilesWithErrorWhenResultSetHasLessColumns() {
        // given
        givenRowSet(givenRow("accountId", "id1", "data", "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("reqId"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("Error occurred while mapping profiles: some columns are missing");
    }

    @Test
    public void mapShouldReturnEmptyProfilesWithErrorWhenResultSetHasUnexpectedColumnType() {
        // given
        givenRowSet(givenRow("accountId", "id1", "{}", 123, "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("reqId"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No profile found for id: reqId");
    }

    @Test
    public void mapShouldSkipProfileWithInvalidBody() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "{}", "request", "request"),
                givenRow("accountId", "id1", "invalid", "request", "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapShouldSkipProfileWithInvalidMergePrecedence() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "{}", "request", "request"),
                givenRow("accountId", "id1", "{}", "invalid", "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapShouldUseDefaultMergePrecedence() {
        // given
        givenRowSet(givenRow("accountId", "id1", "{}", null, "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapShouldSkipProfileWithInvalidType() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "{}", "request", "request"),
                givenRow("accountId", "id1", "{}", "request", "invalid"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapShouldReturnProfileWithErrorForMissingId() {
        // given
        givenRowSet(givenRow("accountId", "id1", "{}", "request", "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No profile found for id: id2");
    }

    @Test
    public void mapShouldReturnEmptyProfileWithErrorsForMissingIdsIfAccountDiffers() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "{}", "request", "request"),
                givenRow("accountId", "id2", "{}", "request", "imp"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "otherAccountId",
                singleton("id1"),
                singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactlyInAnyOrder(
                        "No profile found for id: id1 for account: otherAccountId",
                        "No profile found for id: id2 for account: otherAccountId");
    }

    @Test
    public void mapShouldReturnEmptyProfileWithErrorIfMultipleStoredItemsFoundButNoAccountIdIsDefined() {
        // given
        givenRowSet(
                givenRow("accountId1", "id1", "{}", "request", "request"),
                givenRow("accountId2", "id1", "{}", "request", "request"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                null,
                singleton("id1"),
                emptySet());

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("Multiple profiles found for id: id1 but no account was specified");
    }

    @Test
    public void mapShouldReturnEmptyProfileWithErrorIfMultipleStoredItemsFoundButNoAccountIdIsDiffers() {
        // given
        givenRowSet(
                givenRow("accountId1", "id1", "{}", "request", "request"),
                givenRow("accountId2", "id1", "{}", "request", "request"),
                givenRow("accountId1", "id2", "{}", "request", "imp"),
                givenRow("accountId2", "id2", "{}", "request", "imp"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "otherAccountId",
                singleton("id1"),
                singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactlyInAnyOrder(
                        "No profile found among multiple id: id1 for account: otherAccountId",
                        "No profile found among multiple id: id2 for account: otherAccountId");
    }

    @Test
    public void mapShouldReturnExpectedProfileForGivenAccount() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "{}", "request", "request"),
                givenRow("otherAccountId", "id1", "{}", "request", "request"),
                givenRow("accountId", "id2", "{}", "profile", "imp"),
                givenRow("otherAccountId", "id2", "{}", "request", "imp"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(
                rowSet,
                "accountId",
                singleton("id1"),
                singleton("id2"));

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp())
                .containsExactly(entry("id2", Profile.of(
                        Profile.Type.IMP,
                        Profile.MergePrecedence.PROFILE,
                        mapper.createObjectNode())));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void mapWithoutParamsShouldReturnEmptyProfileWithErrorWhenResultSetHasEmptyResult() {
        // given
        givenRowSet();

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest()).isEmpty();
        assertThat(result.getStoredIdToImp()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly("No profiles were found");
    }

    @Test
    public void mapWithoutParamsShouldReturnExpectedProfile() {
        // given
        givenRowSet(
                givenRow("accountId", "id1", "{}", "request", "request"),
                givenRow("accountId", "id2", "{}", "profile", "imp"));

        // when
        final StoredDataResult<Profile> result = DatabaseProfilesResultMapper.map(rowSet);

        // then
        assertThat(result.getStoredIdToRequest())
                .containsExactly(entry("id1", Profile.of(
                        Profile.Type.REQUEST,
                        Profile.MergePrecedence.REQUEST,
                        mapper.createObjectNode())));
        assertThat(result.getStoredIdToImp())
                .containsExactly(entry("id2", Profile.of(
                        Profile.Type.IMP,
                        Profile.MergePrecedence.PROFILE,
                        mapper.createObjectNode())));
        assertThat(result.getErrors()).isEmpty();
    }

    private void givenRowSet(Row... rows) {
        given(rowSet.iterator()).willReturn(CustomRowIterator.of(Arrays.asList(rows).iterator()));
    }

    private Row givenRow(Object... values) {
        final Row row = mock(Row.class, withSettings().strictness(LENIENT));
        given(row.getValue(anyInt())).willAnswer(invocation -> values[(Integer) invocation.getArgument(0)]);
        given(row.size()).willReturn(values.length);
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
