package org.prebid.server.bidder.rubicon;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class RubiconUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new RubiconUsersyncer(null, "type", false));
        assertThatNullPointerException().isThrownBy(() -> new RubiconUsersyncer("some_url", null, false));
        assertThatNullPointerException().isThrownBy(() -> new RubiconUsersyncer("some_url", "type", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new RubiconUsersyncer("//usersync.org/", "redirect", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of("//usersync.org/", "redirect", false));
    }
}
