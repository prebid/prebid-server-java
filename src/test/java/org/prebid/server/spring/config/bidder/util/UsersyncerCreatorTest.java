package org.prebid.server.spring.config.bidder.util;

import org.junit.Test;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UsersyncerCreatorTest {

    @Test
    public void createShouldReturnUsersyncerWithConcatenatedExternalAndRedirectUrl() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        config.setCookieFamilyName("rubicon");
        config.setUrl("//usersync-url");
        config.setRedirectUrl("/redirect-url");
        config.setType("redirect");
        config.setSupportCors(true);

        // when and then
        assertThat(UsersyncerCreator.create(config, "http://localhost:8000").get())
                .extracting(usersyncer -> usersyncer.getPrimaryMethod().getRedirectUrl())
                .containsOnly("http://localhost:8000/redirect-url");
    }

    @Test
    public void createShouldReturnUsersyncerWithEmptyRedirectUrlWhenItWasNotDefined() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        config.setCookieFamilyName("rubicon");
        config.setUrl("//usersync-url");
        config.setType("redirect");
        config.setSupportCors(true);

        // when and then
        assertThat(UsersyncerCreator.create(config, null).get())
                .extracting(usersyncer -> usersyncer.getPrimaryMethod().getRedirectUrl())
                .containsOnly("");
    }

    @Test
    public void createShouldValidateExternalUrl() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        config.setUrl("//usersync-url");
        config.setRedirectUrl("not-valid-url");
        config.setType("redirect");
        config.setSupportCors(true);

        // given, when and then
        assertThatThrownBy(() -> UsersyncerCreator.create(config, null).get())
                .hasCauseExactlyInstanceOf(MalformedURLException.class)
                .hasMessage("URL supplied is not valid: null");
    }

    @Test
    public void createShouldReturnUsersyncerWithPrimaryAndSecondaryMethods() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        config.setCookieFamilyName("rubicon");
        config.setUrl("//usersync-url");
        config.setRedirectUrl("/redirect-url");
        config.setType("redirect");
        config.setSupportCors(true);

        final UsersyncConfigurationProperties.SecondaryConfigurationProperties secondaryMethodConfig =
                new UsersyncConfigurationProperties.SecondaryConfigurationProperties();
        secondaryMethodConfig.setUrl("//usersync-url-secondary");
        secondaryMethodConfig.setRedirectUrl("/redirect-url-secondary");
        secondaryMethodConfig.setType("iframe");
        secondaryMethodConfig.setSupportCors(false);

        config.setSecondary(secondaryMethodConfig);

        // when and then
        assertThat(UsersyncerCreator.create(config, "http://localhost:8000").get()).isEqualTo(
                Usersyncer.of(
                        "rubicon",
                        Usersyncer.UsersyncMethod.of(
                                "redirect",
                                "//usersync-url",
                                "http://localhost:8000/redirect-url",
                                true),
                        Usersyncer.UsersyncMethod.of(
                                "iframe",
                                "//usersync-url-secondary",
                                "http://localhost:8000/redirect-url-secondary",
                                false)));
    }

    @Test
    public void createShouldFailWhenSecondaryMethodPresentAndPrimaryAbsent() {
        // given
        final UsersyncConfigurationProperties config = new UsersyncConfigurationProperties();
        config.setUrl("");
        config.setCookieFamilyName("rubicon");
        config.setType("redirect");
        config.setSupportCors(true);

        final UsersyncConfigurationProperties.SecondaryConfigurationProperties secondaryMethodConfig =
                new UsersyncConfigurationProperties.SecondaryConfigurationProperties();
        secondaryMethodConfig.setUrl("//usersync-url-secondary");
        secondaryMethodConfig.setRedirectUrl("/redirect-url-secondary");
        secondaryMethodConfig.setType("iframe");
        secondaryMethodConfig.setSupportCors(false);

        config.setSecondary(secondaryMethodConfig);

        // when and then
        assertThatThrownBy(() -> UsersyncerCreator.create(config, "http://localhost:8000").get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid usersync configuration: primary method is missing while secondary is"
                        + " present. Configuration:");
    }
}
