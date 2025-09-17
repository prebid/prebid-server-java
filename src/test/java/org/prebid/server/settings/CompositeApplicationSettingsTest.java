package org.prebid.server.settings;

<<<<<<< HEAD
import com.fasterxml.jackson.databind.node.TextNode;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
<<<<<<< HEAD
import org.prebid.server.settings.model.Profile;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class CompositeApplicationSettingsTest {

    @Mock
    private ApplicationSettings delegate1;
    @Mock
    private ApplicationSettings delegate2;

    private CompositeApplicationSettings compositeApplicationSettings;

    @BeforeEach
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
        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("low")
                        .build())
                .build();
        given(delegate1.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> future = compositeApplicationSettings.getAccountById("ignore", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isSameAs(account);
        verifyNoInteractions(delegate2);
    }

    @Test
    public void getAccountByIdShouldReturnAccountFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("low")
                        .build())
                .build();
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
    public void getCategoriesShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.succeededFuture(singletonMap("iab", "id")));

        // when
        final Future<Map<String, String>> future
                = compositeApplicationSettings.getCategories("adServer", "publisher", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(singletonMap("iab", "id"));
        verifyNoInteractions(delegate2);
    }

    @Test
    public void getCategoriesShouldReturnResultFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        given(delegate2.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.succeededFuture(singletonMap("iab", "id")));

        // when
        final Future<Map<String, String>> future
                = compositeApplicationSettings.getCategories("adServer", "publisher", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(singletonMap("iab", "id"));
    }

    @Test
    public void getCategoriesShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error1")));

        given(delegate2.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("error2")));

        // when
        final Future<Map<String, String>> future
                = compositeApplicationSettings.getCategories("adServer", "publisher", null);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause().getMessage()).isEqualTo("error2");
    }

    @Test
    public void getStoredDataShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
<<<<<<< HEAD
                        StoredDataResult.of(
                                singletonMap("key1", "value1"),
                                singletonMap("key2", "value2"),
                                emptyList())));

        // when
        final Future<StoredDataResult<String>> future =
=======
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key2", "value2"),
                                emptyList())));

        // when
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                compositeApplicationSettings.getStoredData(null, singleton("key1"), singleton("key2"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
        assertThat(future.result().getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("key2", "value2"));
        verifyNoInteractions(delegate2);
    }

    @Test
    public void getStoredDataShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
<<<<<<< HEAD
                        StoredDataResult.of(
                                singletonMap("key1", "value1"),
                                singletonMap("key2", "value2"),
                                emptyList())));

        // when
        final Future<StoredDataResult<String>> future =
=======
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key2", "value2"),
                                emptyList())));

        // when
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                compositeApplicationSettings.getStoredData(null, singleton("key1"), singleton("key2"), null);

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
        given(delegate1.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error2"))));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                compositeApplicationSettings.getStoredData(null, singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getStoredDataShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
<<<<<<< HEAD
                        StoredDataResult.of(
                                singletonMap("key1", "value1"),
                                singletonMap("key3", "value3"),
                                singletonList("error1"))));

        // when
        compositeApplicationSettings.getStoredData(
                null,
                new HashSet<>(asList("key1", "key2")),
                new HashSet<>(asList("key3", "key4")),
                null);
=======
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key3", "value3"),
                                singletonList("error1"))));

        // when
        compositeApplicationSettings.getStoredData(null, new HashSet<>(asList("key1", "key2")),
                new HashSet<>(asList("key3", "key4")), null);
>>>>>>> 04d9d4a13 (Initial commit)

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> requestCaptor = ArgumentCaptor.forClass(
                Set.class);
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> impCaptor = ArgumentCaptor.forClass(Set.class);
        verify(delegate2).getStoredData(any(), requestCaptor.capture(), impCaptor.capture(), any());

        assertThat(requestCaptor.getValue()).hasSize(1)
                .containsOnly("key2");
        assertThat(impCaptor.getValue()).hasSize(1)
                .containsOnly("key4");
    }

    @Test
    public void getStoredDataShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
<<<<<<< HEAD
                        StoredDataResult.of(
                                singletonMap("key1", "value1"),
                                singletonMap("key3", "value3"),
=======
                        StoredDataResult.of(singletonMap("key1", "value1"), singletonMap("key3", "value3"),
>>>>>>> 04d9d4a13 (Initial commit)
                                asList("key2 not found", "key4 not found"))));

        given(delegate2.getStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
<<<<<<< HEAD
                        StoredDataResult.of(
                                singletonMap("key2", "value2"),
                                singletonMap("key4", "value4"),
                                emptyList())));

        // when
        final Future<StoredDataResult<String>> future =
                compositeApplicationSettings.getStoredData(
                        null,
                        new HashSet<>(asList("key1", "key2")),
                        new HashSet<>(asList("key3", "key4")),
                        null);
=======
                        StoredDataResult.of(singletonMap("key2", "value2"), singletonMap("key4", "value4"),
                                emptyList())));

        // when
        final Future<StoredDataResult> future =
                compositeApplicationSettings.getStoredData(null, new HashSet<>(asList("key1", "key2")),
                        new HashSet<>(asList("key3", "key4")), null);
>>>>>>> 04d9d4a13 (Initial commit)

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
        given(delegate1.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(), emptyList())));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                compositeApplicationSettings.getAmpStoredData(null, singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
        verifyNoInteractions(delegate2);
    }

    @Test
    public void getAmpStoredDataShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(), emptyList())));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                compositeApplicationSettings.getAmpStoredData(null, singleton("key1"), emptySet(), null);

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
        given(delegate1.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error2"))));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future =
=======
        final Future<StoredDataResult> future =
>>>>>>> 04d9d4a13 (Initial commit)
                compositeApplicationSettings.getAmpStoredData(null, singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getAmpStoredDataShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(), singletonList("error1"))));

        // when
<<<<<<< HEAD
        compositeApplicationSettings.getAmpStoredData(
                null, new HashSet<>(asList("key1", "key2")), emptySet(), null);
=======
        compositeApplicationSettings.getAmpStoredData(null, new HashSet<>(asList("key1", "key2")), emptySet(),
                null);
>>>>>>> 04d9d4a13 (Initial commit)

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> requestCaptor = ArgumentCaptor.forClass(
                Set.class);
        verify(delegate2).getAmpStoredData(any(), requestCaptor.capture(), anySet(), any());

        assertThat(requestCaptor.getValue()).hasSize(1)
                .containsOnly("key2");
    }

    @Test
    public void getAmpStoredDataShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
<<<<<<< HEAD
                        StoredDataResult.of(
                                singletonMap("key1", "value1"),
                                emptyMap(),
=======
                        StoredDataResult.of(singletonMap("key1", "value1"), emptyMap(),
>>>>>>> 04d9d4a13 (Initial commit)
                                singletonList("key2 not found"))));

        given(delegate2.getAmpStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(singletonMap("key2", "value2"), emptyMap(), emptyList())));

        // when
<<<<<<< HEAD
        final Future<StoredDataResult<String>> future = compositeApplicationSettings.getAmpStoredData(
                null, new HashSet<>(asList("key1", "key2")), emptySet(), null);
=======
        final Future<StoredDataResult> future = compositeApplicationSettings.getAmpStoredData(null,
                new HashSet<>(asList("key1", "key2")), emptySet(), null);
>>>>>>> 04d9d4a13 (Initial commit)

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(2)
                .containsOnly(
                        entry("key1", "value1"),
                        entry("key2", "value2"));
    }

    @Test
<<<<<<< HEAD
    public void getProfilesShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(
                                singletonMap("key1", givenProfile("value1")),
                                singletonMap("key2", givenProfile("value2")),
                                emptyList())));

        // when
        final Future<StoredDataResult<Profile>> future =
                compositeApplicationSettings.getProfiles(null, singleton("key1"), singleton("key2"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", givenProfile("value1")));
        assertThat(future.result().getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("key2", givenProfile("value2")));
        verifyNoInteractions(delegate2);
    }

    @Test
    public void getProfilesShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(
                                singletonMap("key1", givenProfile("value1")),
                                singletonMap("key2", givenProfile("value2")),
                                emptyList())));

        // when
        final Future<StoredDataResult<Profile>> future =
                compositeApplicationSettings.getProfiles(null, singleton("key1"), singleton("key2"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(1)
                .containsOnly(entry("key1", givenProfile("value1")));
        assertThat(future.result().getStoredIdToImp()).hasSize(1)
                .containsOnly(entry("key2", givenProfile("value2")));
    }

    @Test
    public void getProfilesShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error1"))));

        given(delegate2.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(emptyMap(), emptyMap(), singletonList("error2"))));

        // when
        final Future<StoredDataResult<Profile>> future =
                compositeApplicationSettings.getProfiles(null, singleton("key1"), emptySet(), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getStoredIdToRequest()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getProfilesShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(
                                singletonMap("key1", givenProfile("value1")),
                                singletonMap("key3", givenProfile("value3")),
                                singletonList("error1"))));

        // when
        compositeApplicationSettings.getProfiles(
                null,
                new HashSet<>(asList("key1", "key2")),
                new HashSet<>(asList("key3", "key4")),
                null);

        // then
        final ArgumentCaptor<Set<String>> requestCaptor = ArgumentCaptor.captor();
        final ArgumentCaptor<Set<String>> impCaptor = ArgumentCaptor.captor();
        verify(delegate2).getProfiles(any(), requestCaptor.capture(), impCaptor.capture(), any());

        assertThat(requestCaptor.getValue()).hasSize(1)
                .containsOnly("key2");
        assertThat(impCaptor.getValue()).hasSize(1)
                .containsOnly("key4");
    }

    @Test
    public void getProfilesShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(
                                singletonMap("key1", givenProfile("value1")),
                                singletonMap("key3", givenProfile("value3")),
                                asList("key2 not found", "key4 not found"))));

        given(delegate2.getProfiles(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredDataResult.of(
                                singletonMap("key2", givenProfile("value2")),
                                singletonMap("key4", givenProfile("value4")),
                                emptyList())));

        // when
        final Future<StoredDataResult<Profile>> future =
                compositeApplicationSettings.getProfiles(
                        null,
                        new HashSet<>(asList("key1", "key2")),
                        new HashSet<>(asList("key3", "key4")),
                        null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getStoredIdToRequest()).hasSize(2)
                .containsOnly(
                        entry("key1", givenProfile("value1")),
                        entry("key2", givenProfile("value2")));
        assertThat(future.result().getStoredIdToImp()).hasSize(2)
                .containsOnly(
                        entry("key3", givenProfile("value3")),
                        entry("key4", givenProfile("value4")));
    }

    @Test
=======
>>>>>>> 04d9d4a13 (Initial commit)
    public void getStoredResponsesShouldReturnResultFromFirstDelegateIfPresent() {
        // given
        given(delegate1.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(singletonMap("key1", "value1"), emptyList())));

        // when
        final Future<StoredResponseDataResult> future =
                compositeApplicationSettings.getStoredResponses(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getIdToStoredResponses()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
        verifyNoInteractions(delegate2);
    }

    @Test
    public void getStoredResponsesShouldReturnResultFromFromSecondDelegateIfFirstDelegateFails() {
        // given
        given(delegate1.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(singletonMap("key1", "value1"), emptyList())));

        // when
        final Future<StoredResponseDataResult> future =
                compositeApplicationSettings.getStoredResponses(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNotNull();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getIdToStoredResponses()).hasSize(1)
                .containsOnly(entry("key1", "value1"));
    }

    @Test
    public void getStoredResponsesShouldReturnEmptyResultIfAllDelegatesFail() {
        // given
        given(delegate1.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(emptyMap(), singletonList("error1"))));

        given(delegate2.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(emptyMap(), singletonList("error2"))));

        // when
        final Future<StoredResponseDataResult> future =
                compositeApplicationSettings.getStoredResponses(singleton("key1"), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getIdToStoredResponses()).isEmpty();
        assertThat(future.result().getErrors()).hasSize(1)
                .containsOnly("error2");
    }

    @Test
    public void getStoredResponsesShouldPassOnlyMissingIdsToSecondDelegateIfFirstDelegateAlreadyObtainedThey() {
        // given
        given(delegate1.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(singletonMap("key1", "value1"), singletonList("error1"))));

        // when
        compositeApplicationSettings.getStoredResponses(new HashSet<>(asList("key1", "key2")), null);

        // then
<<<<<<< HEAD
        final ArgumentCaptor<Set<String>> responseCaptor = ArgumentCaptor.captor();
=======
        @SuppressWarnings("unchecked") final ArgumentCaptor<Set<String>> responseCaptor = ArgumentCaptor.forClass(
                Set.class);
>>>>>>> 04d9d4a13 (Initial commit)
        verify(delegate2).getStoredResponses(responseCaptor.capture(), any());

        assertThat(responseCaptor.getValue()).hasSize(1).containsOnly("key2");
    }

    @Test
    public void getStoredResponsesShouldReturnResultConsequentlyFromAllDelegates() {
        // given
        given(delegate1.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(singletonMap("key1", "value1"), singletonList("key2 not found"))));

        given(delegate2.getStoredResponses(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        StoredResponseDataResult.of(singletonMap("key2", "value2"), emptyList())));

        // when
        final Future<StoredResponseDataResult> future =
                compositeApplicationSettings.getStoredResponses(new HashSet<>(asList("key1", "key2")), null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getErrors()).isEmpty();
        assertThat(future.result().getIdToStoredResponses()).hasSize(2)
                .containsOnly(
                        entry("key1", "value1"),
                        entry("key2", "value2"));
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
