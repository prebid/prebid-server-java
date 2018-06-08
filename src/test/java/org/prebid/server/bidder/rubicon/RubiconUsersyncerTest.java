package org.prebid.server.bidder.rubicon;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class RubiconUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new RubiconUsersyncer(null, false));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new RubiconUsersyncer("//usersync.org/", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of("//usersync.org/", "redirect", false));
    }
}
