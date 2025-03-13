package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver;

import com.iab.openrtb.request.BrandVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformNameVersionTest {

    @Test
    void fromShouldReturnNullWhenPlatformIsNull() {
        // when
        final PlatformNameVersion target = PlatformNameVersion.from(null);

        // then
        assertThat(target).isNull();
    }

    @Test
    void fromShouldCreatePlatformNameVersionWithValidInput() {
        // given
        final BrandVersion platform = new BrandVersion("Windows",
                Arrays.asList("10", "0", "0"),
                null);

        // when
        final PlatformNameVersion target = PlatformNameVersion.from(platform);

        // then
        assertThat(target).isNotNull();
        assertThat(target.getPlatformName()).isEqualTo("Windows");
        assertThat(target.getPlatformVersion()).isEqualTo("10.0.0");
    }

    @Test
    void toStringShouldReturnFormattedString() {
        // given
        final BrandVersion platform = new BrandVersion("macOS",
                Arrays.asList("13", "1"),
                null);
        final PlatformNameVersion target = PlatformNameVersion.from(platform);

        // when
        final String result = target.toString();

        // then
        assertThat(result).isEqualTo("macOS 13.1");
    }

    @Test
    void fromShouldHandleEmptyVersionList() {
        // given
        final BrandVersion platform = new BrandVersion("Linux",
                List.of(),
                null);

        // when
        final PlatformNameVersion target = PlatformNameVersion.from(platform);

        // then
        assertThat(target).isNotNull();
        assertThat(target.getPlatformName()).isEqualTo("Linux");
        assertThat(target.getPlatformVersion()).isEmpty();
        assertThat(target.toString()).isEqualTo("Linux ");
    }
}
