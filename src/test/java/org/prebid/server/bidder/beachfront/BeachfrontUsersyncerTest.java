package org.prebid.server.bidder.beachfront;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class BeachfrontUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer("some_url", null, null));
        assertThatNullPointerException().isThrownBy(() -> new BeachfrontUsersyncer("some_url", "other_url", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org/syncb?pid=142&gdpr={{.GDPR}}&gc={{.GDPRConsent}}&gce=1&"
                        + "url=http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dbeachfront%26gdpr%3D{{.GDPR}}"
                        + "%26gdpr_consent%3D{{.GDPRConsent}}%26uid%3D%5Bio_cid%5D", "iframe", false);

        // when
        final UsersyncInfo result = new BeachfrontUsersyncer("//usersync.org/syncb?pid=",
                "http://external.org/", "142").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}
