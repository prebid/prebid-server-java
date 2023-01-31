package org.prebid.server.bidder;

import org.junit.Test;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncInfoBuilderTest {

    @Test
    public void assembleUsersyncInfoShouldAppendRedirectUrlToUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod(
                        "http://url/redirect={{redirect_url}}",
                        "http://localhost:8000redirectUrl"))
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url/redirect=http%3A%2F%2Flocalhost%3A8000redirectUrl%3Ff%3Di");
    }

    @Test
    public void assembleUsersyncInfoShouldAppendEncodedRedirectUrlAndNotEncodedQueryParamsToUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod(
                        "http://url/redirect={{redirect_url}}",
                        "http://localhost:8000/setuid?gdpr={{gdpr}}?gdpr={{gdpr}}"))
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo(
                "http://url/redirect=http%3A%2F%2Flocalhost%3A8000%2F"
                        + "setuid%3Fgdpr%3D%7B%7Bgdpr%7D%7D?gdpr={{gdpr}}&f=i");
    }

    @Test
    public void assembleUsersyncInfoShouldIgnoreRedirectUrlIfNotDefined() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod("http://url/redirect=", null))
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url/redirect=");
    }

    @Test
    public void assembleWithPrivacyShouldCreatePrivacyAwareUsersyncInfo() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod(
                        """
                                http://url?redir=\
                                %26gdpr%3D{{gdpr}}\
                                %26gdpr_consent%3D{{gdpr_consent}}\
                                %26us_privacy%3D{{us_privacy}}\
                                %26gpp%3D{{gpp}}\
                                %26gpp_sid%3D{{gpp_sid}}""",
                        null))
                .privacy(Privacy.builder()
                        .gdpr("1")
                        .consentString("consent$1")
                        .ccpa(Ccpa.of("1YNN"))
                        .gpp("gpp")
                        .gppSid(List.of(1))
                        .build())
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo(
                "http://url?redir=%26gdpr%3D1%26gdpr_consent%3Dconsent%241"
                        + "%26us_privacy%3D1YNN%26gpp%3Dgpp%26gpp_sid%3D1");
    }

    @Test
    public void assembleWithPrivacyShouldTolerateMissingPrivacyParamsUsersyncInfo() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod(
                        """
                                http://url?redir=\
                                %26gdpr%3D{{gdpr}}\
                                %26gdpr_consent%3D{{gdpr_consent}}\
                                %26us_privacy%3D{{us_privacy}}\
                                %26gpp%3D{{gpp}}\
                                %26gpp_sid%3D{{gpp_sid}}""",
                        null))
                .privacy(Privacy.builder().ccpa(Ccpa.EMPTY).build())
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo(
                "http://url?redir=%26gdpr%3D%26gdpr_consent%3D%26us_privacy%3D%26gpp%3D%26gpp_sid%3D");
    }

    @Test
    public void assembleWithPrivacyShouldIgnorePrivacyParamsIfTheyAreMissingInUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod("http://url?redir=a%3Db", null))
                .privacy(Privacy.builder()
                        .gdpr("1")
                        .consentString("consent")
                        .ccpa(Ccpa.of("YNN"))
                        .build())
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo("http://url?redir=a%3Db");
    }

    @Test
    public void assembleWithPrivacyUsersyncInfoShouldPopulateWithPrivacyRedirectAndUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod(
                        """
                                http://url\
                                ?gdpr={{gdpr}}\
                                &{{gdpr_consent}}\
                                &gpp={{gpp}}\
                                &gpp_sid={{gpp_sid}}\
                                &redir={{redirect_url}}""",
                        """
                                http://localhost:8000/setuid\
                                ?bidder=adnxs\
                                &gdpr={{gdpr}}\
                                &gdpr_consent={{gdpr_consent}}\
                                &us_privacy={{us_privacy}}\
                                &gpp={{gpp}}\
                                &gpp_sid={{gpp_sid}}\
                                &uid=$UID"""))
                .privacy(Privacy.builder()
                        .gdpr("1")
                        .consentString("consent$1")
                        .ccpa(Ccpa.of("1YNN"))
                        .build())
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo("""
                http://url?gdpr=1&consent%241&gpp=&gpp_sid=\
                &redir=http%3A%2F%2Flocalhost%3A8000%2Fsetuid%3Fbidder%3Dadnxs%26gdpr%3D1\
                %26gdpr_consent%3Dconsent%241%26us_privacy%3D1YNN\
                %26gpp%3D%26gpp_sid%3D%26f%3Di%26uid%3D%24UID""");
    }

    @Test
    public void assembleWithUrlUsersyncInfoShouldUpdateUsersyncUrl() {
        // given and when
        final UsersyncInfo result = UsersyncInfoBuilder
                .from(createUsersyncMethod("http://url", null))
                .usersyncUrl("http://updated-url")
                .build();

        // then
        assertThat(result.getUrl()).isEqualTo("http://updated-url");
    }

    private static UsersyncMethod createUsersyncMethod(String usersyncUrl, String redirectUrl) {
        return UsersyncMethod.builder()
                .type(UsersyncMethodType.REDIRECT)
                .usersyncUrl(usersyncUrl)
                .redirectUrl(redirectUrl)
                .supportCORS(false)
                .build();
    }
}
