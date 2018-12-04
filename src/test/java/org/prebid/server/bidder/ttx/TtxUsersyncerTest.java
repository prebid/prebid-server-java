package org.prebid.server.bidder.ttx;

import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class TtxUsersyncerTest extends VertxTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new TtxUsersyncer(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new TtxUsersyncer("some_url", null, null));
    }

    @Test
    public void creationShouldInitExpectedUsersyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org//?ri=partnerId&ru=http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dttx"
                        + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D33XUSERID33X",
                "redirect", false);

        // when
        final UsersyncInfo result = new TtxUsersyncer("//usersync.org/",
                "http://external.org/", "partnerId").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}
