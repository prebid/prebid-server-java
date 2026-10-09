package org.prebid.server.spring.config.bidder;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.BidderInstanceDeps;
import org.prebid.server.bidder.UsersyncInfoFactory;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.floxis.ExtImpFloxis;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.spring.config.BiddersConfiguration;
import org.prebid.server.spring.config.SpringConfiguration;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FloxisConfigurationTest extends VertxTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(FloxisConfiguration.class, BiddersConfiguration.class, SpringConfiguration.class)
            .withBean(JacksonMapper.class, () -> jacksonMapper)
            .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(
                    new PropertiesPropertySource("application",
                            YamlPropertySourceFactory.readPropertiesFromYamlResource(
                                    new ClassPathResource("application.yaml")))));

    @Test
    public void adapexShouldBeDisabledByDefaultAndInheritFloxisCapabilities() {
        contextRunner.run(context -> {
            final BidderDeps deps = context.getBean(BidderDeps.class);
            final BidderInstanceDeps adapex = instance(deps, "adapex");
            final BidderInstanceDeps floxis = instance(deps, "floxis");

            assertThat(adapex.getBidderInfo().isEnabled()).isFalse();
            assertThat(adapex.getBidderInfo().getAliasOf()).isEqualTo("floxis");
            assertThat(adapex.getBidderInfo().getCapabilities()).isEqualTo(floxis.getBidderInfo().getCapabilities());
            assertThat(adapex.getBidderInfo().getGdpr().getVendorId()).isEqualTo(1609);
            assertThat(adapex.getBidderInfo().getOrtbVersion()).isEqualTo(OrtbVersion.ORTB_2_6);
            assertThat(adapex.getBidderInfo().getCompressionType()).isEqualTo(CompressionType.GZIP);
            assertThat(adapex.getBidderInfo().isCcpaEnforced()).isTrue();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    public void enabledAdapexShouldUseOwnEndpointWhenFloxisIsDisabled() {
        contextRunner.withPropertyValues("adapters.floxis.aliases.adapex.enabled=true").run(context -> {
            final BidderDeps deps = context.getBean(BidderDeps.class);
            final Bidder<BidRequest> bidder = (Bidder<BidRequest>) instance(deps, "adapex").getBidder();
            final BidRequest request = BidRequest.builder()
                    .imp(List.of(Imp.builder()
                            .id("imp-1")
                            .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpFloxis.of("a b&c", "eu", "acme"))))
                            .build()))
                    .build();

            assertThat(instance(deps, "floxis").getBidderInfo().isEnabled()).isFalse();
            assertThat(bidder.makeHttpRequests(request).getValue())
                    .extracting(HttpRequest::getUri)
                    .containsExactly("https://hb.adapex.io/pbs?seat=a%20b%26c");
        });
    }

    @Test
    public void adapexUsersyncShouldUseOwnCookieFamilyAndForwardPrivacyToOwnHostAndCallback() {
        contextRunner.run(context -> {
            final BidderInstanceDeps adapex = instance(context.getBean(BidderDeps.class), "adapex");
            final Privacy privacy = Privacy.builder()
                    .gdpr("1")
                    .consentString("consent")
                    .ccpa(Ccpa.of("1YNN"))
                    .gpp("gpp")
                    .gppSid(List.of(2, 6))
                    .build();

            final UsersyncInfo usersync = new UsersyncInfoFactory("https://pbs.example")
                    .build("adapex", null, adapex.getUsersyncer().getRedirect(), privacy);

            assertThat(adapex.getUsersyncer().getCookieFamilyName()).isEqualTo("adapex");
            assertThat(adapex.getUsersyncer().isEnabled()).isTrue();
            assertThat(usersync.getType()).isEqualTo(UsersyncMethodType.REDIRECT);
            assertThat(usersync.getUrl()).isEqualTo("https://sync.adapex.io/sync?gdpr=1&gdpr_consent=consent"
                    + "&gpp=gpp&gpp_sid=2%2C6&us_privacy=1YNN"
                    + "&dest=https%3A%2F%2Fpbs.example%2Fsetuid%3Fbidder%3Dadapex%26gdpr%3D1"
                    + "%26gdpr_consent%3Dconsent%26us_privacy%3D1YNN%26gpp%3Dgpp%26gpp_sid%3D2%252C6"
                    + "%26f%3Di%26uid%3D%24%7BUSER_ID%7D");
        });
    }

    private static BidderInstanceDeps instance(BidderDeps deps, String name) {
        return deps.getInstances().stream()
                .filter(instance -> instance.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
