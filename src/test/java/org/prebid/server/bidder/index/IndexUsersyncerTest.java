package org.prebid.server.bidder.index;


import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class IndexUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new IndexUsersyncer(null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new IndexUsersyncer("//usersync.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of("//usersync.org/", "redirect", false));
    }
}
