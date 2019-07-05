package org.prebid.server.bidder;

import org.junit.Test;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UsersyncerTest {

    @Test
    public void newUsersyncerShouldReturnUsersyncerWithConcatenatedExternalAndRedirectUrl() {
        // given, when and then
        assertThat(
                new Usersyncer("rubicon", "//usersync-url", "/redicret-url", "http://localhost:8000", "redirect", true))
                .extracting(Usersyncer::getRedirectUrl)
                .containsOnly("http://localhost:8000/redicret-url");
    }

    @Test
    public void newUsersyncerShouldReturnUsersyncerWithEmptyRedirectUrlWhenItWasNotDefined() {
        // given, when and then
        assertThat(new Usersyncer("rubicon", "//usersync-url", null, null, "redirect", true))
                .extracting(Usersyncer::getRedirectUrl)
                .containsOnly("");
    }

    @Test
    public void newUsersyncerShouldValidateExtenalUrl() {
        // given, when and then
        assertThatThrownBy(() -> new Usersyncer(null, "//usersync-url", "not-valid-url", null, "redirect", true))
                .hasCauseExactlyInstanceOf(MalformedURLException.class)
                .hasMessage("URL supplied is not valid: null");
    }
}
