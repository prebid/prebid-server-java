package org.prebid.server.bidder.adtelligent;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class AdtelligentUsersyncerTest {

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new AdtelligentUsersyncer("//usersync.org/", "http://external.org/", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dadtelligent%26uid%3D%7Buid%7D",
                        "redirect", false));
    }
}
