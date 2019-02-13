package org.prebid.server.bidder.pubmatic;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class PubmaticUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new PubmaticUsersyncer(null, "", false, ""));
        assertThatNullPointerException().isThrownBy(() -> new PubmaticUsersyncer("", null, false, ""));
        assertThatNullPointerException().isThrownBy(() -> new PubmaticUsersyncer("", "", null, ""));
        assertThatNullPointerException().isThrownBy(() -> new PubmaticUsersyncer("", "", false, null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new PubmaticUsersyncer("//usersync.org/", "iframe", false, "http://external.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dpubmatic"
                                + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D",
                        "iframe", false));
    }
}
