package org.prebid.server.spring.config.bidder.util;

import org.junit.Test;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncMethodConfigurationProperties;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UsersyncerCreatorTest {

    @Test
    public void createShouldReturnUsersyncerWithConcatenatedExternalAndRedirectUrl() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        final UsersyncMethodConfigurationProperties methodConfig = new UsersyncMethodConfigurationProperties();
        methodConfig.setUrl("//redirect-url?uid=");
        methodConfig.setUidMacro("uid-macro");
        methodConfig.setSupportCors(false);

        config.setCookieFamilyName("rubicon");
        config.setRedirect(methodConfig);

        // when and then
        assertThat(UsersyncerCreator.create("http://localhost:8000").apply(config, CookieFamilySource.ROOT))
                .extracting(usersyncer -> usersyncer.getRedirect().getRedirectUrl())
                .isEqualTo("""
                        http://localhost:8000/setuid\
                        ?bidder=rubicon\
                        &gdpr={{gdpr}}\
                        &gdpr_consent={{gdpr_consent}}\
                        &us_privacy={{us_privacy}}\
                        &gpp={{gpp}}\
                        &gpp_sid={{gpp_sid}}\
                        &uid=uid-macro\
                        """);
    }

    @Test
    public void createShouldValidateExternalUrl() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        final UsersyncMethodConfigurationProperties methodConfig = new UsersyncMethodConfigurationProperties();
        methodConfig.setUrl("//usersync-url");
        methodConfig.setUidMacro("not-valid-macro");
        methodConfig.setSupportCors(true);
        config.setRedirect(methodConfig);

        // given, when and then
        assertThatThrownBy(() -> UsersyncerCreator.create(null).apply(config, CookieFamilySource.ROOT))
                .hasCauseExactlyInstanceOf(MalformedURLException.class)
                .hasMessage("URL supplied is not valid: null");
    }

    @Test
    public void createShouldReturnUsersyncerWithPrimaryAndSecondaryMethods() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        config.setCookieFamilyName("rubicon");

        final UsersyncMethodConfigurationProperties iframeConfig = new UsersyncMethodConfigurationProperties();
        iframeConfig.setUrl("//usersync-url-iframe?uid=");
        iframeConfig.setUidMacro("uid-macro-iframe");
        iframeConfig.setSupportCors(true);

        final UsersyncMethodConfigurationProperties redirectConfig = new UsersyncMethodConfigurationProperties();
        redirectConfig.setUrl("//usersync-url-redirect?u=");
        redirectConfig.setUidMacro("uid-macro-redirect");
        redirectConfig.setSupportCors(false);

        config.setIframe(iframeConfig);
        config.setRedirect(redirectConfig);

        // when
        final Usersyncer result = UsersyncerCreator.create("http://localhost:8000").apply(config, CookieFamilySource.ROOT);

        // then
        final UsersyncMethod expectedIframeMethod = UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl("//usersync-url-iframe?uid=")
                .redirectUrl("""
                        http://localhost:8000/setuid\
                        ?bidder=rubicon\
                        &gdpr={{gdpr}}\
                        &gdpr_consent={{gdpr_consent}}\
                        &us_privacy={{us_privacy}}\
                        &gpp={{gpp}}\
                        &gpp_sid={{gpp_sid}}\
                        &uid=uid-macro-iframe\
                        """)
                .supportCORS(true)
                .build();

        final UsersyncMethod expectedRedirectMethod = UsersyncMethod.builder()
                .type(UsersyncMethodType.REDIRECT)
                .usersyncUrl("//usersync-url-redirect?u=")
                .redirectUrl("""
                        http://localhost:8000/setuid\
                        ?bidder=rubicon&gdpr={{gdpr}}\
                        &gdpr_consent={{gdpr_consent}}\
                        &us_privacy={{us_privacy}}\
                        &gpp={{gpp}}\
                        &gpp_sid={{gpp_sid}}\
                        &uid=uid-macro-redirect\
                        """)
                .supportCORS(false)
                .build();

        assertThat(result).isEqualTo(
                Usersyncer.of("rubicon", CookieFamilySource.ROOT, expectedIframeMethod, expectedRedirectMethod));
    }

    @Test
    public void createShouldTolerateMissingUid() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        final UsersyncMethodConfigurationProperties methodConfig = new UsersyncMethodConfigurationProperties();
        methodConfig.setUrl("//redirect-url?uid=");
        methodConfig.setSupportCors(false);

        config.setCookieFamilyName("rubicon");
        config.setRedirect(methodConfig);

        // when and then
        assertThat(UsersyncerCreator.create("http://localhost:8000").apply(config, CookieFamilySource.ROOT))
                .extracting(usersyncer -> usersyncer.getRedirect().getRedirectUrl())
                .isEqualTo("""
                        http://localhost:8000/setuid\
                        ?bidder=rubicon\
                        &gdpr={{gdpr}}\
                        &gdpr_consent={{gdpr_consent}}\
                        &us_privacy={{us_privacy}}\
                        &gpp={{gpp}}\
                        &gpp_sid={{gpp_sid}}\
                        &uid=\
                        """);
    }
}
