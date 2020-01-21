package org.prebid.server.proto.response;

import org.junit.Test;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.privacy.model.Privacy;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncInfoTest {

    @Test
    public void assembleUsersyncInfoShouldAppendRedirectUrlToUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url/redirect=", "redirectUrl",
                        "http://localhost:8000", null, false)).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url/redirect=http%3A%2F%2Flocalhost%3A8000redirectUrl");
    }

    @Test
    public void assembleUsersyncInfoShouldAppendEncodedRedirectUrlAndNotEncodedQueryParamsToUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url/redirect=", "/setuid?gdpr={{gdpr}}?gdpr={{gdpr}}",
                        "http://localhost:8000", null, false)).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo(
                "http://url/redirect=http%3A%2F%2Flocalhost%3A8000%2Fsetuid%3Fgdpr%3D%7B%7Bgdpr%7D%7D?gdpr={{gdpr}}");
    }

    @Test
    public void assembleUsersyncInfoShouldIgnoreRedirectUrlIfNotDefined() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url/redirect=", null, null, null, false)).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url/redirect=");
    }

    @Test
    public void assembleWithPrivacyShouldCreatePrivacyAwareUsersyncInfo() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url?redir=%26gdpr%3D{{gdpr}}"
                        + "%26gdpr_consent%3D{{gdpr_consent}}"
                        + "%26us_privacy={{us_privacy}}",
                        null, null, null, false))
                .withPrivacy(Privacy.of("1", "consent$1", "1YN")).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=%26gdpr%3D1%26gdpr_consent%3Dconsent%241%26us_privacy=1YN");
    }

    @Test
    public void assembleWithPrivacyShouldTolerateMissingPrivacyParamsUsersyncInfo() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url?redir=%26gdpr%3D{{gdpr}}"
                        + "%26gdpr_consent%3D{{gdpr_consent}}"
                        + "%26us_privacy%3D{{us_privacy}}",
                        null, null, null, false)).withPrivacy(Privacy.empty()).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=%26gdpr%3D%26gdpr_consent%3D%26us_privacy%3D");
    }

    @Test
    public void assembleWithPrivacyShouldIgnorePrivacyParamsIfTheyAreMissingInUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url?redir=a%3Db", null, null, null, false))
                .withPrivacy(Privacy.of("1", "consent", "YNN")).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=a%3Db");
    }

    @Test
    public void assembleWithPrivacyUsersyncInfoShouldPopulateWithPrivacyRedirectAndUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url/{{gdpr}}/{{gdpr_consent}}?redir=",
                        "/setuid?bidder=adnxs&gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}"
                                + "&us_privacy={{us_privacy}}"
                                + "&uid=$UID",
                        "http://localhost:8000", null, false))
                .withPrivacy(Privacy.of("1", "consent$1", "1YN")).assemble();

        // then
        assertThat(result.getUrl()).isEqualTo(
                "http://url/1/consent%241?redir=http%3A%2F%2Flocalhost%3A8000%2Fsetuid%3Fbidder%3Dadnxs%26gdpr%3D1"
                        + "%26gdpr_consent%3Dconsent%241%26us_privacy%3D1YN%26uid%3D%24UID");
    }

    @Test
    public void assembleWithUrlUsersyncInfoShouldUpdateUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfo.UsersyncInfoAssembler
                .assembler(new Usersyncer(null, "http://url", null, null, null, false))
                .withUrl("http://updated-url").assemble();

        // then
        assertThat(result.getUrl()).isEqualTo("http://updated-url");
    }
}
