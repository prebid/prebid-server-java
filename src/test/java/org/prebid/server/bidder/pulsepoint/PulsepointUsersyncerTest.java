package org.prebid.server.bidder.pulsepoint;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class PulsepointUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new PulsepointUsersyncer(null, null, false));
        assertThatNullPointerException().isThrownBy(() -> new PulsepointUsersyncer("", null, false));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new PulsepointUsersyncer("//usersync.org/", "http://external.org/", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of("//usersync.org/http%3A%2F%2Fexternal" +
                        ".org%2F%2Fsetuid%3Fbidder%3Dpulsepoint%26uid%3D%25%25VGUID%25%25", "redirect", false));
    }
}
