package org.prebid.server.spring.config.bidder.util;

import org.junit.Test;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
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
        assertThat(UsersyncerCreator.create("http://localhost:8000").apply(config))
                .extracting(usersyncer -> usersyncer.getRedirect().getRedirectUrl())
                .isEqualTo("""
                        http://localhost:8000/setuid?bidder=rubicon&gdpr={{gdpr}}\
                        &gdpr_consent={{gdpr_consent}}&us_privacy={{us_privacy}}&uid=uid-macro\
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
        assertThatThrownBy(() -> UsersyncerCreator.create(null).apply(config))
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

        // when and then
        assertThat(UsersyncerCreator.create("http://localhost:8000").apply(config)).isEqualTo(
                Usersyncer.of(
                        "rubicon",
                        UsersyncMethod.of(
                                UsersyncMethodType.IFRAME,
                                "//usersync-url-iframe?uid=",
                                """
                                        http://localhost:8000/setuid?bidder=rubicon&gdpr={{gdpr}}\
                                        &gdpr_consent={{gdpr_consent}}&us_privacy={{us_privacy}}&uid=uid-macro-iframe\
                                        """,
                                true),
                        UsersyncMethod.of(
                                UsersyncMethodType.REDIRECT,
                                "//usersync-url-redirect?u=",
                                """
                                        http://localhost:8000/setuid?bidder=rubicon&gdpr={{gdpr}}\
                                        &gdpr_consent={{gdpr_consent}}&us_privacy={{us_privacy}}\
                                        &uid=uid-macro-redirect\
                                        """,
                                false)));
    }
}
