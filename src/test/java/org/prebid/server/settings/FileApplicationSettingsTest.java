package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class FileApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;

    @Test
    public void creationShouldFailIfFileCouldNotBeParsed() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("invalid"));

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore"));
    }

    @Test
    public void getAccountByIdShouldReturnEmptyWhenAccountsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("configs:"));

        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<Account> account = applicationSettings.getAccountById("123", null);

        // then
        assertThat(account.failed()).isTrue();
    }

    @Test
    public void getAccountByIdShouldReturnPresentAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts: [ { id: '123', priceGranularity: 'low', bannerCacheTtl: '100',"
                        + " videoCacheTtl : '100', eventsEnabled: 'true', enforceGdpr: 'true'} ]"));

        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<Account> account = applicationSettings.getAccountById("123", null);

        // then
        assertThat(account.succeeded()).isTrue();
        assertThat(account.result()).isEqualTo(Account.builder()
                .id("123")
                .priceGranularity("low")
                .bannerCacheTtl(100)
                .videoCacheTtl(100)
                .eventsEnabled(true)
                .enforceGdpr(true)
                .build());
    }

    @Test
    public void getAccountByIdShouldReturnEmptyForUnknownAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts: [ {id: '123'}, {id: '456'} ]"));

        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<Account> account = applicationSettings.getAccountById("789", null);

        // then
        assertThat(account.failed()).isTrue();
    }


    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyWhenConfigsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts:"));

        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<String> config = applicationSettings.getAdUnitConfigById("123", null);

        // then
        assertThat(config.failed()).isTrue();
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnPresentConfig() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer(
                "configs: [ {id: '123', config: '{\"bidder\": \"rubicon\"}'}, {id: '456'} ]"));

        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<String> adUnitConfigById1 = applicationSettings.getAdUnitConfigById("123", null);
        final Future<String> adUnitConfigById2 = applicationSettings.getAdUnitConfigById("456", null);

        // then
        assertThat(adUnitConfigById1.succeeded()).isTrue();
        assertThat(adUnitConfigById1.result()).isEqualTo("{\"bidder\": \"rubicon\"}");
        assertThat(adUnitConfigById2.succeeded()).isTrue();
        assertThat(adUnitConfigById2.result()).isEqualTo("");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyForUnknownConfig() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("configs: [ id: '123', id: '456' ]"));

        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<String> config = applicationSettings.getAdUnitConfigById("789", null);

        // then
        assertThat(config.failed()).isTrue();
    }

    @Test
    public void getStoredDataShouldReturnResultWithNotFoundErrorForNonExistingRequestId() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")); // stored imp
        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getStoredData(singleton("2"), emptySet(), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/requests/1.json"));
        assertThat(storedRequestResult.succeeded()).isTrue();
        assertThat(storedRequestResult.result().getErrors()).isNotNull().hasSize(1)
                .isEqualTo(singletonList("No stored request found for id: 2"));
        assertThat(storedRequestResult.result().getStoredIdToRequest()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
    }

    @Test
    public void getStoredDataShouldReturnResultWithNotFoundErrorForNonExistingImpId() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/1.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")); // stored imp
        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getStoredData(emptySet(), singleton("2"), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/imps/1.json"));
        assertThat(storedRequestResult.succeeded()).isTrue();
        assertThat(storedRequestResult.result().getErrors()).isNotNull().hasSize(1)
                .isEqualTo(singletonList("No stored imp found for id: 2"));
        assertThat(storedRequestResult.result().getStoredIdToRequest()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
    }

    @Test
    public void getStoredDataShouldReturnResultWithNoErrorsIfAllIdsArePresent() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")); // stored imp
        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getStoredData(singleton("1"), singleton("2"), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/requests/1.json"));
        verify(fileSystem).readFileBlocking(eq("/home/user/imps/2.json"));
        assertThat(storedRequestResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedRequestResult.result().getStoredIdToRequest()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
        assertThat(storedRequestResult.result().getStoredIdToImp()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("2", "value2"));
    }

    @Test
    public void getAmpStoredDataShouldIgnoreImpIdsArgument() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")); // stored imp
        final FileApplicationSettings applicationSettings =
                FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getAmpStoredData(emptySet(), singleton("2"), null);

        // then
        assertThat(storedRequestResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedRequestResult.result().getStoredIdToImp()).isEmpty();
    }

    @Test
    public void storedDataInitializationShouldNotReadFromNonJsonFiles() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willReturn(singletonList("/home/user/requests/1.txt"));
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts:")); // settings file

        // when
        FileApplicationSettings.create(fileSystem, "ignore", "ignore", "ignore");

        // then
        verify(fileSystem, never()).readFileBlocking(eq("1.txt"));
    }
}
