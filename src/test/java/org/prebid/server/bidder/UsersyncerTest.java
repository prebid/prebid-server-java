package org.prebid.server.bidder;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncerTest {

    @Test
    public void createShouldReturnUsersyncerWithConcatenatedExternalAndRedirectUrl(){
        // given, when and then
        assertThat(Usersyncer.create("rubicon", "//usersync-url", "/redicret-url", "/external-url", "redirect", true))
                .extracting(Usersyncer::getRedirectUrl)
                .containsOnly("/external-url/redicret-url");
    }

    @Test
    public void createShouldReturnUsersyncerWithEmptyRedirectUrlWhenItWasNotDefined(){
        // given, when and then
        assertThat(Usersyncer.create("rubicon", "//usersync-url", null, null, "redirect", true))
                .extracting(Usersyncer::getRedirectUrl)
                .containsOnly("");
    }
}
