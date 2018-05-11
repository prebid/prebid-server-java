package org.prebid.server.bidder.sovrn;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class SovrnUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new SovrnUsersyncer(null, null));
        assertThatNullPointerException().isThrownBy(() -> new SovrnUsersyncer("", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new SovrnUsersyncer("//usersync.org/", "http://external.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/redir=http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dsovrn%26uid%3D",
                        "redirect", false));
    }
}
