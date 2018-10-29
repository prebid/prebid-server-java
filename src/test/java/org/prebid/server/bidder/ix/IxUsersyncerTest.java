package org.prebid.server.bidder.ix;


import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class IxUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new IxUsersyncer(null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new IxUsersyncer("//usersync.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of("//usersync.org/", "redirect", false));
    }
}
