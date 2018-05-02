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
import org.prebid.server.settings.model.StoredDataResult;

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
    public void getStoredDataShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key2", "value2"),
                                emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getStoredData(singleton("key1"), singleton("key2"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
        assertThat(future.result().getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("key2", "value2"));
        verifyZeroInteractions(delegate2);
    }

    @Test
    public void getStoredDataShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key2", "value2"),
                                emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getStoredData(singleton("key1"), singleton("key2"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
        assertThat(future.result().getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("key2", "value2"));
    }

    @Test
    public void getStoredDataShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error2"))));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getStoredData(singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getStoredDataShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key3", "value3"),
                                singletonList("error1"))));

        // when
        compositeApplicationSettings.getStoredData(new HashSet<>(asList("key1", "key2")),
                new HashSet<>(asList("key3", "key4")), null);

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> requestCaptor = ArgumentCaptor.forClass(
                Set.class);
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> impCaptor = ArgumentCaptor.forClass(Set.class);
        verify(delegate2).getStoredData(requestCaptor.capture(), impCaptor.capture(), any());

        assertThat(requestCaptor.getValue()).hasSize(1)
                .containsOnly("key2");
        assertThat(impCaptor.getValue()).hasSize(1)
                .containsOnly("key4");
    }

    @Test
    public void getStoredDataShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key3", "value3"),
                                asList("key2 not found", "key4 not found"))));

        given(delegate2.getStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key2", "value2"), singletonMap("key4", "value4"),
                                emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getStoredData(new HashSet<>(asList("key1", "key2")),
                        new HashSet<>(asList("key3", "key4")), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(2)
                .containsOnly(
                        entry("key1", "value1"),
                        entry("key2", "value2"));
        assertThat(future.result().getStoredIdToImp()).hasSize(2)
                .containsOnly(
                        entry("key3", "value3"),
                        entry("key4", "value4"));
    }

    @Test
    public void getAmpStoredDataShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(), emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getAmpStoredData(singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
        verifyZeroInteractions(delegate2);
    }

    @Test
    public void getAmpStoredDataShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(), emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getAmpStoredData(singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
    }

    @Test
    public void getAmpStoredDataShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error2"))));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getAmpStoredData(singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getAmpStoredDataShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(), singletonList("error1"))));

        // when
        compositeApplicationSettings.getAmpStoredData(new HashSet<>(asList("key1", "key2")), emptySet(), null);

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> requestCaptor = ArgumentCaptor.forClass(
                Set.class);
        verify(delegate2).getAmpStoredData(requestCaptor.capture(), anySet(), any());

        assertThat(requestCaptor.getValue()).hasSize(1)
                .containsOnly("key2");
    }

    @Test
    public void getAmpStoredDataShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(),
                                singletonList("key2 not found"))));

        given(delegate2.getAmpStoredData(anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key2", "value2"), emptyMap(), emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getAmpStoredData(new HashSet<>(asList("key1", "key2")), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(2)
                .containsOnly(
                        entry("key1", "value1"),
                        entry("key2", "value2"));
    }
}
