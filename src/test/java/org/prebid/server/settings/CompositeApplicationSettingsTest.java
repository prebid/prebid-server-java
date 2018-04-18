package org.prebid.server.settings;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredRequestResult;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class CompositeApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings delegate1;
    @Mock
    private ApplicationSettings delegate2;

    private CompositeApplicationSettings compositeApplicationSettings;

    @Before
    public void setUp() {
        compositeApplicationSettings = new CompositeApplicationSettings(asList(delegate1, delegate2));
    }

    @Test
    public void creationShouldFailOnEmptyDelegates() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CompositeApplicationSettings(emptyList()))
                .withMessage("At least one application settings implementation required");
    }

    @Test
    public void getAccountByIdShouldReturnAccountFromFirstDelegateIfPresent() {
        // given
        final Account account = Account.of("accountId1", "low");
        given(delegate1.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = compositeApplicationSettings.getAccountById("ignore", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verifyZeroInteractions(delegate2);
    }

    @Test
    public void getAccountByIdShouldReturnAccountFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        final Account account = Account.of("accountId1", "low");
        given(delegate2.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = compositeApplicationSettings.getAccountById("ignore", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
    }

    @Test
    public void getAccountByIdShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        given(delegate2.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error2")));

        // when
        final Future<Account> future = compositeApplicationSettings.getAccountById("ignore", null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("error2");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnConfigFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.succeededFuture("adUnitConfig1"));

        // when
        final Future<String> future = compositeApplicationSettings.getAdUnitConfigById("ignore", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("adUnitConfig1");
        verifyZeroInteractions(delegate2);
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnConfigFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        given(delegate2.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.succeededFuture("adUnitConfig1"));

        // when
        final Future<String> future = compositeApplicationSettings.getAdUnitConfigById("ignore", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("adUnitConfig1");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        given(delegate2.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error2")));

        // when
        final Future<String> future = compositeApplicationSettings.getAdUnitConfigById("ignore", null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("error2");
    }

    @Test
    public void getStoredRequestsByIdReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), emptyList())));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsById(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getStoredIdToJson()).hasSize(1)
                .containsOnly(entry("key1", "value1"));

        verifyZeroInteractions(delegate2);
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), emptyList())));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsById(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getStoredIdToJson()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
    }

    @Test
    public void getStoredRequestsByIdShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error2"))));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsById(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getStoredRequestsByIdShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), singletonList("error1"))));

        // when
        compositeApplicationSettings.getStoredRequestsById(new HashSet<>(asList("key1", "key2")), null);

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(delegate2).getStoredRequestsById(captor.capture(), any());

        assertThat(captor.getValue()).hasSize(1)
                .containsOnly("key2");
    }

    @Test
    public void getStoredRequestsByIdShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), singletonList("key2 not found"))));

        given(delegate2.getStoredRequestsById(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key2", "value2"), emptyList())));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsById(new HashSet<>(asList("key1", "key2")), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToJson()).hasSize(2)
                .containsOnly(
                        entry("key1", "value1"),
                        entry("key2", "value2"));
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), emptyList())));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsByAmpId(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getStoredIdToJson()).hasSize(1)
                .containsOnly(entry("key1", "value1"));

        verifyZeroInteractions(delegate2);
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), emptyList())));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsByAmpId(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getStoredIdToJson()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredRequestResult.of(emptyMap(), singletonList("error2"))));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsByAmpId(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToJson()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getStoredRequestsByAmpIdShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), singletonList("error1"))));

        // when
        compositeApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("key1", "key2")), null);

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(delegate2).getStoredRequestsByAmpId(captor.capture(), any());

        assertThat(captor.getValue()).hasSize(1)
                .containsOnly("key2");
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key1", "value1"), singletonList("key2 not found"))));

        given(delegate2.getStoredRequestsByAmpId(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredRequestResult.of(singletonMap("key2", "value2"), emptyList())));

        // when
        final Future<StoredRequestResult> future =
                compositeApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("key1", "key2")), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToJson()).hasSize(2)
                .containsOnly(
                        entry("key1", "value1"),
                        entry("key2", "value2"));
    }
}
