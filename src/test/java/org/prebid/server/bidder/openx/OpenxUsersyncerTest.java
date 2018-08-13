package org.prebid.server.bidder.openx;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class OpenxUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new OpenxUsersyncer(null, ""));
        assertThatNullPointerException().isThrownBy(() -> new OpenxUsersyncer("", null));
    }

    @Test
    public void creationShouldInitExpectedUsercyncInfo() {
        assertThat(new OpenxUsersyncer("https://rtb.openx.net/sync/prebid?r=", "localhost").usersyncInfo())
                .isEqualTo(UsersyncInfo.of("https://rtb.openx.net/sync/prebid?r=localhost%2Fsetuid%3Fbidder%3Dopenx"
                                + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%24%7BUID%7D",
                        "redirect", false));
    }
}
