package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.settings.model.Account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class FileApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> FileApplicationSettings.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> FileApplicationSettings.create(fileSystem, null));
    }

    @Test
    public void creationShouldFailIfFileCouldNotBeParsed() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("invalid"));

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileApplicationSettings.create(fileSystem, "ignore"));
    }

    @Test
    public void getAccountByIdShouldReturnEmptyWhenAccountsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("configs:"));

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        // when
        final Future<Account> account = applicationSettings.getAccountById("123");

        // then
        assertThat(account.failed()).isTrue();
    }

    @Test
    public void getAccountByIdShouldReturnPresentAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts: [ '123', '456' ]"));

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        // when
        final Future<Account> account = applicationSettings.getAccountById("123");

        // then
        assertThat(account.succeeded()).isTrue();
        assertThat(account.result()).isEqualTo(Account.builder().id("123").build());
    }

    @Test
    public void getAccountByIdShouldReturnEmptyForUnknownAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts: [ '123', '456' ]"));

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        // when
        final Future<Account> account = applicationSettings.getAccountById("789");

        // then
        assertThat(account.failed()).isTrue();
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyWhenConfigsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts:"));

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        // when
        final Future<String> config = applicationSettings.getAdUnitConfigById("123");

        // then
        assertThat(config.failed()).isTrue();
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnPresentConfig() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer(
                "configs: [ {id: '123', config: '{\"bidder\": \"rubicon\"}'}, {id: '456'} ]"));

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        // when
        final Future<String> adUnitConfigById1 = applicationSettings.getAdUnitConfigById("123");
        final Future<String> adUnitConfigById2 = applicationSettings.getAdUnitConfigById("456");

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

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        // when
        final Future<String> config = applicationSettings.getAdUnitConfigById("789");

        // then
        assertThat(config.failed()).isTrue();
    }
}
