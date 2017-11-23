package org.rtb.vexing.settings;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.settings.model.Account;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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
    public void getAccountByIdShouldReturnEmptyWhenAccountsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("configs:"));

        //when
        final Optional<Account> account = FileApplicationSettings.create(fileSystem, "ignore").getAccountById("123");

        // then
        assertThat(account).isEmpty();
    }

    @Test
    public void getAccountByIdShouldReturnPresentAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts: [ '123', '456' ]"));

        //when
        final Optional<Account> account = FileApplicationSettings.create(fileSystem, "ignore").getAccountById("123");

        // then
        assertThat(account).hasValue(Account.builder().id("123").build());
    }

    @Test
    public void getAccountByIdShouldReturnEmptyForUnknownAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts: [ '123', '456' ]"));

        //when
        final Optional<Account> account = FileApplicationSettings.create(fileSystem, "ignore").getAccountById("789");

        // then
        assertThat(account).isEmpty();
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyWhenConfigsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts:"));

        //when
        final Optional<String> config = FileApplicationSettings.create(fileSystem, "ignore").getAdUnitConfigById("123");

        // then
        assertThat(config).isEmpty();
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnPresentConfig() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer(
                "configs: [ {id: '123', config: '{\"bidder\": \"rubicon\"}'}, {id: '456'} ]"));

        final FileApplicationSettings applicationSettings = FileApplicationSettings.create(fileSystem, "ignore");

        //

        // when and then
        assertThat(applicationSettings.getAdUnitConfigById("123")).hasValue("{\"bidder\": \"rubicon\"}");
        assertThat(applicationSettings.getAdUnitConfigById("456")).hasValue("");
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnEmptyForUnknownConfig() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("configs: [ id: '123', id: '456' ]"));

        //when
        final Optional<String> config = FileApplicationSettings.create(fileSystem, "ignore").getAdUnitConfigById("789");

        // then
        assertThat(config).isEmpty();
    }
}
