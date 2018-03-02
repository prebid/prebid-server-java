package org.prebid.server.bidder.appnexus;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;


public class AppnexusUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AppnexusUsersyncer(null, null));
        assertThatNullPointerException().isThrownBy(() -> new AppnexusUsersyncer("", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new AppnexusUsersyncer("//usersync.org/", "http://external.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dadnxs%26uid%3D%24UID",
                        "redirect", false));
    }
}
