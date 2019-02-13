package org.prebid.server.bidder.yieldmo;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class YieldmoUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new YieldmoUsersyncer(null, "type", false, "some_url"));
        assertThatNullPointerException().isThrownBy(() -> new YieldmoUsersyncer("some_url", null, false, "some_url"));
        assertThatNullPointerException().isThrownBy(() -> new YieldmoUsersyncer("some_url", "type", null, "some_url"));
        assertThatNullPointerException().isThrownBy(() -> new YieldmoUsersyncer("some_url", "type", false, null));
    }

    @Test
    public void creationShouldInitExpectedUsersyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dyieldmo%26gdpr%3D{{gdpr}}"
                        + "%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%24UID",
                "redirect", false);

        // when
        final UsersyncInfo result = new YieldmoUsersyncer("//usersync.org/", "redirect", false,
                "http://external.org/").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}
