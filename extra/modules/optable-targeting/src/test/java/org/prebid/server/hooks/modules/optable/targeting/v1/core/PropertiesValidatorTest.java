package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Site;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesValidatorTest {

    @Test
    public void isValidShouldReturnTrueWhenTenantAndOriginArePresent() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setTenant("tenant");
        properties.setOrigin("origin");

        // when
        final boolean result = PropertiesValidator.isValid(properties);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void isValidShouldReturnFalseWhenTenantIsMissing() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setOrigin("origin");

        // when
        final boolean result = PropertiesValidator.isValid(properties);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isValidShouldReturnFalseWhenOriginIsMissing() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setTenant("tenant");

        // when
        final boolean result = PropertiesValidator.isValid(properties);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isTrafficSourceValidShouldReturnTrueWhenEnrichWebIsTrueAndSiteIsPresent() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setEnrichWeb(true);
        final BidRequest bidRequest = BidRequest.builder().site(Site.builder().build()).build();

        // when
        final boolean result = PropertiesValidator.isTrafficSourceValid(bidRequest, properties);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void isTrafficSourceValidShouldReturnFalseWhenEnrichWebIsTrueAndSiteIsMissing() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setEnrichWeb(true);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final boolean result = PropertiesValidator.isTrafficSourceValid(bidRequest, properties);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isTrafficSourceValidShouldReturnTrueWhenEnrichAppIsTrueAndAppIsPresent() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setEnrichApp(true);
        final BidRequest bidRequest = BidRequest.builder().app(App.builder().build()).build();

        // when
        final boolean result = PropertiesValidator.isTrafficSourceValid(bidRequest, properties);

        // then
        assertThat(result).isTrue();
    }

    @Test
    public void isTrafficSourceValidShouldReturnFalseWhenEnrichAppIsTrueAndAppIsMissing() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setEnrichApp(true);
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final boolean result = PropertiesValidator.isTrafficSourceValid(bidRequest, properties);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void isTrafficSourceValidShouldReturnFalseWhenBothEnrichWebAndEnrichAppAreFalseOrNull() {
        // given
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setEnrichWeb(false);
        properties.setEnrichApp(false);
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().build())
                .app(App.builder().build())
                .build();

        // when
        final boolean result = PropertiesValidator.isTrafficSourceValid(bidRequest, properties);

        // then
        assertThat(result).isFalse();
    }
}
