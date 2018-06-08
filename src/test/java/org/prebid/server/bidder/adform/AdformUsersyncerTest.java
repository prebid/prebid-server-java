package org.prebid.server.bidder.adform;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class AdformUsersyncerTest {

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new AdformUsersyncer("//usersync.org/", "http://external.org/", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dadform%26uid%3D%24UID",
                        "redirect", false));
    }
}
