package org.prebid.server.bidder.facebook;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class FacebookUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new FacebookUsersyncer(null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new FacebookUsersyncer("//usersync.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of("//usersync.org/", "redirect", false));
    }
}
