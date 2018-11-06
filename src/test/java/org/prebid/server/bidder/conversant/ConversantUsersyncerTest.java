package org.prebid.server.bidder.conversant;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class ConversantUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new ConversantUsersyncer(null, null));
        assertThatNullPointerException().isThrownBy(() -> new ConversantUsersyncer("", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new ConversantUsersyncer("//usersync.org/", "http://external.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dconversant"
                                + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D",
                        "redirect", false));
    }
}
