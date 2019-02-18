package org.prebid.server.proto.response;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncInfoTest {

    @Test
    public void assembleUsersyncInfoShouldAppendRedirectUrlToUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler("http://url/redirect=", "redirectUrl", null, false).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url/redirect=redirectUrl");
    }

    @Test
    public void assembleUsersyncInfoShouldIgnoreRedirectUrlIfNotDefined() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler("http://url/redirect=", null, null, false).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url/redirect=");
    }


    @Test
    public void assembleWithGdprShouldCreateGdprAwareUsersyncInfo() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler("http://url?redir=%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}", null, null, false)
                .withGdpr("1", "consent$1").assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=%26gdpr%3D1%26gdpr_consent%3Dconsent%241");
    }

    @Test
    public void assembleWithGdprShouldTolerateMissingGdprParamsUsersyncInfo() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler("http://url?redir=%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}", null, null, false)
                .withGdpr(null, null).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=%26gdpr%3D%26gdpr_consent%3D");
    }

    @Test
    public void assembleWithGdprShouldIgnoreGdprParamsIfTheyAreMissingInUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler("http://url?redir=a%3Db", null, null, false)
                .withGdpr(null, null).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=a%3Db");
    }


    @Test
    public void assembleWithGdprUsersyncInfoShouldPopulateWithGdprRedirectAndUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler("http://url/{{gdpr}}/{{gdpr_consent}}?redir=",
                        "/setuid?bidder=adnxs&gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}&uid=$UID", null, false)
                .withGdpr("1", "consent$1").assemble();

        // then
        assertThat(result.getUrl()).isEqualTo(
                "http://url/1/consent%241?redir=%2Fsetuid%3Fbidder%3Dadnxs%26gdpr%3D1%26gdpr_consent%3Dconsent%241%2"
                        + "6uid%3D%24UID");
    }
}
