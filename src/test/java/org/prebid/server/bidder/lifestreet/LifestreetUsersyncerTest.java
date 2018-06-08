package org.prebid.server.bidder.lifestreet;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class LifestreetUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new LifestreetUsersyncer(null, null, false));
        assertThatNullPointerException().isThrownBy(() -> new LifestreetUsersyncer("", null, false));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new LifestreetUsersyncer("//usersync.org/", "http://external.org/", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3D"
                                + "lifestreet%26uid%3D%24%24visitor_cookie%24%24", "redirect", false));
    }
}
