package org.prebid.server.bidder.adkerneladn;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class AdkernelAdnUsersyncerTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AdkernelAdnUsersyncer(null, "some_url"));
        assertThatNullPointerException().isThrownBy(() -> new AdkernelAdnUsersyncer("some_url", null));
    }

    @Test
    public void creationShouldInitExpectedUsersyncInfo() {
        // given
        final UsersyncInfo expected = UsersyncInfo.of(
                "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3DadkernelAdn"
                        + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%24%7BUID%7D",
                "redirect", false);

        // when
        final UsersyncInfo result = new AdkernelAdnUsersyncer("//usersync.org/",
                "http://external.org/").usersyncInfo();

        // then
        assertThat(result).isEqualTo(expected);
    }
}