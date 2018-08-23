package org.prebid.server.bidder.beachfront;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class BeachfrontUsersyncerTest {
    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new BeachfrontUsersyncer("//usersync.org/", "142").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/142", "redirect", false));
    }
}
