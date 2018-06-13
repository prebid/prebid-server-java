package org.prebid.server.bidder.conversant;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class ConversantUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new ConversantUsersyncer(null, null, false));
        assertThatNullPointerException().isThrownBy(() -> new ConversantUsersyncer("", null, false));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new ConversantUsersyncer("//usersync.org/", "http://external.org/", false).usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dconversant%26uid%3D",
                        "redirect",
                        false));
    }
}
