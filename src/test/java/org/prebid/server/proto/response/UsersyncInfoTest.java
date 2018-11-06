package org.prebid.server.proto.response;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncInfoTest {

    @Test
    public void withGdprShouldCreateGdprAwareUsersyncInfo() {
        // given
        final UsersyncInfo usersyncInfo = UsersyncInfo.of(
                "http://url?redir=%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}", null, false);

        // when
        final UsersyncInfo result = usersyncInfo.withGdpr("1", "consent$1");

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=%26gdpr%3D1%26gdpr_consent%3Dconsent%241");
    }

    @Test
    public void withGdprShouldTolerateMissingGdprParamsUsersyncInfo() {
        // given
        final UsersyncInfo usersyncInfo = UsersyncInfo.of(
                "http://url?redir=%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}", null, false);

        // when
        final UsersyncInfo result = usersyncInfo.withGdpr(null, null);

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=%26gdpr%3D%26gdpr_consent%3D");
    }

    @Test
    public void withGdprShouldIgnoreGdprParamsIfTheyAreMissingInUrl() {
        // given
        final UsersyncInfo usersyncInfo = UsersyncInfo.of(
                "http://url?redir=a%3Db", null, false);

        // when
        final UsersyncInfo result = usersyncInfo.withGdpr(null, null);

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=a%3Db");
    }
}
