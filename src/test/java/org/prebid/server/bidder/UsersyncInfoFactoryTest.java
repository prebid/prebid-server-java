package org.prebid.server.bidder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.response.UsersyncInfo;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class UsersyncInfoFactoryTest {

    private static final String BIDDER = "bidder";

    private UsersyncInfoFactory target;

    @BeforeEach
    public void setUp() {
        target = new UsersyncInfoFactory("http://localhost:8080");
    }

    @Test
    public void constructorShouldThrowExceptionIfExternalUrlIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> new UsersyncInfoFactory(null));
    }

    @Test
    public void constructorShouldThrowExceptionIfExternalUrlIsInvalid() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new UsersyncInfoFactory("invalid-url"));
    }

    @Test
    public void buildShouldUseUsersyncUrlFromUsersyncMethodIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("http://specific-usersync-url-from-method");

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).isEqualTo("http://specific-usersync-url-from-method");
    }

    @Test
    public void buildShouldUseEmptyStringWhenUsersyncUrlIsNullAndHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder.usersyncUrl(null));

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).isEmpty();
    }

    @Test
    public void buildShouldReplaceRedirectParameterWithRedirectUrlIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("http://usersync-url?redir={{redirect_url}}");

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl())
                .doesNotContain("{{redirect_url}}")
                .startsWith("http://usersync-url?redir=http%3A%2F%2F");
    }

    @Test
    public void buildShouldUseExternalUrlAsBaseForRedirectUrlIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("{{redirect_url}}");

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).startsWith("http%3A%2F%2Flocalhost%3A8080%2Fsetuid");
    }

    @Test
    public void buildShouldUseBidderParameterWithBidderNameInRedirectUrlIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("{{redirect_url}}");

        // when
        final UsersyncInfo result = target.build("specific-bidder", null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("bidder%3Dspecific-bidder");
    }

    @Test
    public void buildShouldUseUidParameterWithUidMacroFromUsersyncMethodInRedirectUrlIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder
                .usersyncUrl("{{redirect_url}}")
                .uidMacro("UID-MACRO"));

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("uid%3DUID-MACRO");
    }

    @Test
    public void buildShouldUseFormatParameterWithFormatFromUsersyncMethodInRedirectUrlIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder
                .usersyncUrl("{{redirect_url}}")
                .type(UsersyncMethodType.IFRAME));

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("f%3Db");
    }

    @Test
    public void buildShouldUseRedirectUrlAsUsersyncUrlIfHostCookieUidIsNotNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("http://method-usersync-url");

        // when
        final UsersyncInfo result = target.build(BIDDER, "host-cookie-uid", method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl())
                .doesNotContain("method-usersync-url")
                .startsWith("http://localhost:8080/setuid");
    }

    @Test
    public void buildshouldUseBidderParameterWithBidderNameInUsersyncUrlIfHostCookieUidIsNotNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(UnaryOperator.identity());

        // when
        final UsersyncInfo result = target.build(
                "specific-bidder", "host-cookie-uid", method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("bidder=specific-bidder");
    }

    @Test
    public void buildShouldUseUidParameterWithHostCookieUidInUsersyncUrlIfHostCookieUidIsNotNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(UnaryOperator.identity());

        // when
        final UsersyncInfo result = target.build(
                BIDDER, "specific-host-cookie-uid", method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("uid=specific-host-cookie-uid");
    }

    @Test
    public void buildShouldUseFormatParameterWithFormatFromUsersyncMethodInUsersyncUrlIfHostCookieUidIsNotNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder.type(UsersyncMethodType.IFRAME));

        // when
        final UsersyncInfo result = target.build(BIDDER, "host-cookie-uid", method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("f=b");
    }

    @Test
    public void buildShouldIncludePrivacyParametersInUsersyncUrl() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("""
                http://usersync-url?\
                gdpr={{gdpr}}\
                &gdpr_consent={{gdpr_consent}}\
                &us_privacy={{us_privacy}}\
                &gpp={{gpp}}\
                &gpp_sid={{gpp_sid}}""");
        final Privacy privacy = Privacy.builder()
                .gdpr("1")
                .consentString("consent$1")
                .ccpa(Ccpa.of("1YNN"))
                .gpp("g pp")
                .gppSid(List.of(1, 2))
                .build();

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, privacy);

        // then
        assertThat(result.getUrl()).isEqualTo("""
                http://usersync-url\
                ?gdpr=1\
                &gdpr_consent=consent%241\
                &us_privacy=1YNN\
                &gpp=g+pp\
                &gpp_sid=1%2C2""");
    }

    @Test
    public void buildShouldUseEmptyStringsIfPrivacyParametersAreNull() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("""
                http://usersync-url?\
                gdpr={{gdpr}}\
                &gdpr_consent={{gdpr_consent}}\
                &us_privacy={{us_privacy}}\
                &gpp={{gpp}}\
                &gpp_sid={{gpp_sid}}""");

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).isEqualTo("http://usersync-url?gdpr=&gdpr_consent=&us_privacy=&gpp=&gpp_sid=");
    }

    @Test
    public void buildShouldIncludePrivacyParametersInRedirectUrl() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("{{redirect_url}}");
        final Privacy privacy = Privacy.builder()
                .gdpr("1")
                .consentString("consent$1")
                .ccpa(Ccpa.of("1YNN"))
                .gpp("gpp")
                .gppSid(List.of(1, 2))
                .build();

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, privacy);

        // then
        assertThat(result.getUrl()).isEqualTo("""
                http%3A%2F%2Flocalhost%3A8080%2Fsetuid\
                %3Fbidder%3Dbidder\
                %26gdpr%3D1\
                %26gdpr_consent%3Dconsent%241\
                %26us_privacy%3D1YNN\
                %26gpp%3Dgpp\
                %26gpp_sid%3D1%2C2\
                %26f%3Di\
                %26uid%3D%24UID""");
    }

    @Test
    public void buildShouldReturnTypeFromUsersyncMethod() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder.type(UsersyncMethodType.IFRAME));

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getType()).isEqualTo(UsersyncMethodType.IFRAME);
    }

    @Test
    public void buildShouldReturnIsSupportCORSFromUsersyncMethod() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder.supportCORS(true));

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getSupportCORS()).isTrue();
    }

    @Test
    public void buildShouldUseFormatOverrideOverTypeFormat() {
        // given
        final UsersyncMethod method = givenUsersyncMethod(builder -> builder
                .usersyncUrl("{{redirect_url}}")
                .type(UsersyncMethodType.REDIRECT)
                .formatOverride(UsersyncFormat.BLANK));

        // when
        final UsersyncInfo result = target.build(BIDDER, null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("f%3Db");
    }

    @Test
    public void buildShouldEncodeBidderNameInRedirectUrl() {
        // given
        final UsersyncMethod method = givenUsersyncMethod("{{redirect_url}}");

        // when
        final UsersyncInfo result = target.build("bidder name", null, method, givenEmptyPrivacy());

        // then
        assertThat(result.getUrl()).contains("bidder%3Dbidder%2Bname");
    }

    @Test
    public void buildShouldReturnCorrectFullUrlIfHostCookieUidIsNull() {
        // given
        final UsersyncMethod method = UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl("""
                        http://usersync-url\
                        ?redir={{redirect_url}}\
                        &gdpr={{gdpr}}\
                        &gdpr_consent={{gdpr_consent}}\
                        &us_privacy={{us_privacy}}\
                        &gpp={{gpp}}\
                        &gpp_sid={{gpp_sid}}""")
                .uidMacro("$UID-MACRO")
                .supportCORS(true)
                .formatOverride(UsersyncFormat.PIXEL)
                .build();
        final Privacy privacy = Privacy.builder()
                .gdpr("1")
                .consentString("c$1")
                .ccpa(Ccpa.of("1YNN"))
                .gpp("g pp")
                .gppSid(List.of(1, 2))
                .build();

        // when
        final UsersyncInfo result = target.build("sync-bidder test", null, method, privacy);

        // then
        assertThat(result.getSupportCORS()).isTrue();
        assertThat(result.getType()).isEqualTo(UsersyncMethodType.IFRAME);
        assertThat(result.getUrl()).isEqualTo("""
                http://usersync-url\
                ?redir=\
                http%3A%2F%2Flocalhost%3A8080%2Fsetuid%3F\
                bidder%3Dsync-bidder%2Btest\
                %26gdpr%3D1\
                %26gdpr_consent%3Dc%241\
                %26us_privacy%3D1YNN\
                %26gpp%3Dg+pp\
                %26gpp_sid%3D1%2C2\
                %26f%3Di\
                %26uid%3D%24UID-MACRO\
                &gdpr=1\
                &gdpr_consent=c%241\
                &us_privacy=1YNN\
                &gpp=g+pp\
                &gpp_sid=1%2C2""");
    }

    @Test
    public void buildShouldReturnCorrectFullUrlIfHostCookieUidIsNotNull() {
        // given
        final UsersyncMethod method = UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl("http://ignored.example/should-not-appear")
                .uidMacro("$IGNORED")
                .supportCORS(true)
                .formatOverride(UsersyncFormat.PIXEL)
                .build();
        final Privacy privacy = Privacy.builder()
                .gdpr("1")
                .consentString("c$1")
                .ccpa(Ccpa.of("1YNN"))
                .gpp("g pp")
                .gppSid(List.of(1, 2))
                .build();

        // when
        final UsersyncInfo result = target.build("sync-bidder test", "host-uid value", method, privacy);

        // then
        assertThat(result.getSupportCORS()).isTrue();
        assertThat(result.getType()).isEqualTo(UsersyncMethodType.IFRAME);
        assertThat(result.getUrl()).isEqualTo("""
                http://localhost:8080/setuid?\
                bidder=sync-bidder+test\
                &gdpr=1\
                &gdpr_consent=c%241\
                &us_privacy=1YNN\
                &gpp=g+pp\
                &gpp_sid=1%2C2\
                &f=i\
                &uid=host-uid value""");
    }

    private static UsersyncMethod givenUsersyncMethod(String usersyncUrl) {
        return givenUsersyncMethod(builder -> builder.usersyncUrl(usersyncUrl));
    }

    private static UsersyncMethod givenUsersyncMethod(UnaryOperator<UsersyncMethod.UsersyncMethodBuilder> customize) {
        return customize.apply(UsersyncMethod.builder()
                .type(UsersyncMethodType.REDIRECT)
                .usersyncUrl("http://usersync-url")
                .uidMacro("$UID")).build();
    }

    private static Privacy givenEmptyPrivacy() {
        return Privacy.builder().ccpa(Ccpa.EMPTY).build();
    }
}
