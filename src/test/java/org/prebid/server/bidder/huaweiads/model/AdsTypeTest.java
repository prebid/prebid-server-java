package org.prebid.server.bidder.huaweiads.model;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AdsTypeTest {

    @Test
    public void ofTypeNameShouldCreateAdsTypeWhenTypeIsPassedInLowerCase() {
        final AdsType roll = AdsType.ofTypeName("roll");
        assertThat(roll).isEqualTo(AdsType.ROLL);
    }

    @Test
    public void ofTypeNameShouldCreateBannerAdsTypeWhenInputParamIsNull() {
        final AdsType roll = AdsType.ofTypeName(null);
        assertThat(roll).isEqualTo(AdsType.BANNER);
    }

    @Test
    public void ofTypeNameShouldCreateBannerAdsTypeWhenInputParamDoesNotMatchAnyAdsType() {
        final AdsType roll = AdsType.ofTypeName(UUID.randomUUID().toString());
        assertThat(roll).isEqualTo(AdsType.BANNER);
    }

}
