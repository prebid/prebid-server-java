package org.prebid.server.bidder.ix;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class IxUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new IxUsersyncer(null, null));
        assertThatNullPointerException().isThrownBy(() -> new IxUsersyncer("some_url", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dix"
                        + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D",
                "redirect", false);

        // when
        final UsersyncInfo result = new IxUsersyncer("//usersync.org/",
                "http://external.org/").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}
