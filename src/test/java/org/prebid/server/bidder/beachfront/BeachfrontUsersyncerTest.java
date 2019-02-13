package org.prebid.server.bidder.beachfront;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class BeachfrontUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer(null, "type", false,
                "other_url", "partner"));
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer("some_url", null, false,
                "other_url", "partner"));
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer("some_url", "type", null,
                "other_url", "partner"));
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer("some_url", "type", false,
                null, "partner"));
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer("some_url", "type", false,
                "other_url", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org/syncb?pid=142&gdpr={{gdpr}}&gc={{gdpr_consent}}&gce=1&"
                        + "url=http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dbeachfront%26gdpr%3D{{gdpr}}"
                        + "%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%5Bio_cid%5D", "iframe", false);

        // when
        final UsersyncInfo result = new BeachfrontUsersyncer("//usersync.org/syncb?pid=", "iframe", false,
                "http://external.org/", "142").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}
