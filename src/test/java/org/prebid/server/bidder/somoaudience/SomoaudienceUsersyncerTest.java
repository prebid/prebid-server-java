package org.prebid.server.bidder.somoaudience;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class SomoaudienceUsersyncerTest {

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new SomoaudienceUsersyncer("//usersync.org/", "http://external.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dsomoaudience%26uid%3D%7Buid%7D",
                        "redirect", false));
    }
}
