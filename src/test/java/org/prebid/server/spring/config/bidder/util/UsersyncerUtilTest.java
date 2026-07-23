package org.prebid.server.spring.config.bidder.util;

import org.junit.jupiter.api.Test;
import org.prebid.server.bidder.UsersyncFormat;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncBidderRegulationScopeProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.usersync.UsersyncMethodConfigurationProperties;
import org.prebid.server.util.Uri;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncerUtilTest {

    @Test
    public void createShouldReturnUsersyncerWithCorrectParamsIfSkipWhenConfigIsNull() {
        // given
        final UsersyncConfigurationProperties properties = givenUsersyncConfigurationProperties();
        properties.setSkipwhen(null);

        // when
        final Usersyncer usersyncer = UsersyncerUtil.create(properties);

        // then
        assertThat(usersyncer.isEnabled()).isTrue();
        assertThat(usersyncer.getCookieFamilyName()).isEqualTo("cookie-family-name");
        assertThat(usersyncer.isSkipWhenInGdprScope()).isFalse();
        assertThat(usersyncer.getGppSidToSkip()).isNull();
    }

    @Test
    public void createShouldReturnUsersyncerWithCorrectParamsIfSkipWhenConfigIsNotNull() {
        // given
        final UsersyncBidderRegulationScopeProperties skipwhen = new UsersyncBidderRegulationScopeProperties();
        skipwhen.setGdpr(true);
        skipwhen.setGppSid(List.of(1, 2, 3));

        final UsersyncConfigurationProperties properties = givenUsersyncConfigurationProperties();
        properties.setSkipwhen(skipwhen);

        // when
        final Usersyncer usersyncer = UsersyncerUtil.create(properties);

        // then
        assertThat(usersyncer.isEnabled()).isTrue();
        assertThat(usersyncer.getCookieFamilyName()).isEqualTo("cookie-family-name");
        assertThat(usersyncer.isSkipWhenInGdprScope()).isTrue();
        assertThat(usersyncer.getGppSidToSkip()).containsExactly(1, 2, 3);
    }

    @Test
    public void createShouldReturnUsersyncerWithCorrectUsersyncMethodParams() {
        // given
        final UsersyncMethodConfigurationProperties iframe = new UsersyncMethodConfigurationProperties();
        iframe.setUrl("https://iframe-url");
        iframe.setUidMacro("iframe-uid-macro");
        iframe.setFormatOverride(UsersyncFormat.BLANK);

        final UsersyncConfigurationProperties properties = givenUsersyncConfigurationProperties();
        properties.setIframe(iframe);

        // when
        final Usersyncer usersyncer = UsersyncerUtil.create(properties);

        // then
        assertThat(usersyncer.getIframe()).isEqualTo(UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl(Uri.of("https://iframe-url"))
                .uidMacro("iframe-uid-macro")
                .formatOverride(UsersyncFormat.BLANK)
                .build());
    }

    @Test
    public void createShouldReturnUsersyncerWithNullUsersyncMethodIfPropertiesIsNull() {
        // given
        final UsersyncConfigurationProperties properties = givenUsersyncConfigurationProperties();
        properties.setIframe(null);
        properties.setRedirect(null);

        // when
        final Usersyncer usersyncer = UsersyncerUtil.create(properties);

        // then
        assertThat(usersyncer.getIframe()).isNull();
        assertThat(usersyncer.getRedirect()).isNull();
    }

    @Test
    public void createShouldUseCorrespondingParamsForIframeAndRedirectIfBothAreProvided() {
        // given
        final UsersyncMethodConfigurationProperties iframe = givenUsersyncMethodConfigurationProperties();
        iframe.setUrl("https://iframe-url");

        final UsersyncMethodConfigurationProperties redirect = givenUsersyncMethodConfigurationProperties();
        redirect.setUrl("https://redirect-url");

        final UsersyncConfigurationProperties properties = givenUsersyncConfigurationProperties();
        properties.setIframe(iframe);
        properties.setRedirect(redirect);

        // when
        final Usersyncer useresyncer = UsersyncerUtil.create(properties);

        // then
        assertThat(useresyncer.getIframe()).satisfies(method -> {
            assertThat(method.getType()).isEqualTo(UsersyncMethodType.IFRAME);
            assertThat(method.getUsersyncUrl()).isEqualTo(Uri.of("https://iframe-url"));
        });
        assertThat(useresyncer.getRedirect()).satisfies(method -> {
            assertThat(method.getType()).isEqualTo(UsersyncMethodType.REDIRECT);
            assertThat(method.getUsersyncUrl()).isEqualTo(Uri.of("https://redirect-url"));
        });
    }

    private static UsersyncConfigurationProperties givenUsersyncConfigurationProperties() {
        final UsersyncConfigurationProperties properties = new UsersyncConfigurationProperties();
        properties.setEnabled(true);
        properties.setCookieFamilyName("cookie-family-name");
        properties.setIframe(givenUsersyncMethodConfigurationProperties());
        properties.setRedirect(givenUsersyncMethodConfigurationProperties());
        return properties;
    }

    private static UsersyncMethodConfigurationProperties givenUsersyncMethodConfigurationProperties() {
        final UsersyncMethodConfigurationProperties properties = new UsersyncMethodConfigurationProperties();
        properties.setUrl("https://url");
        return properties;
    }
}
