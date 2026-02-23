package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HeadersResolverTest {

    private static final String TEST_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    @Test
    public void resolveWithNullDeviceShouldReturnOriginalHeaders() {
        // given
        final Map<String, String> headers = new HashMap<>();
        headers.put("test", "value");

        // when
        final Map<String, String> result = HeadersResolver.resolve(null, headers);

        // then
        assertThat(result).isEqualTo(headers);
    }

    @Test
    public void resolveWithDeviceUaShouldReturnUserAgentHeader() {
        // given
        final Device device = Device.builder()
                .ua("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        // when
        final Map<String, String> result = HeadersResolver.resolve(device, new HashMap<>());

        // then
        assertThat(result).containsEntry("User-Agent", TEST_USER_AGENT);
    }

    @Test
    public void resolveWithFullSuaShouldReturnAllHeaders() {
        // given
        final BrandVersion brandVersion = new BrandVersion(
                "Chrome",
                Arrays.asList("100", "0", "0"),
                null);

        final BrandVersion winBrandVersion = new BrandVersion(
                "Windows",
                Arrays.asList("10", "0", "0"),
                null);
        final UserAgent sua = UserAgent.builder()
                .browsers(List.of(brandVersion))
                .platform(winBrandVersion)
                .model("Test Model")
                .architecture("x86")
                .mobile(0)
                .build();

        final Device device = Device.builder()
                .sua(sua)
                .build();

        // when
        final Map<String, String> result = HeadersResolver.resolve(device, new HashMap<>());

        // then
        assertThat(result)
                .containsEntry("Sec-CH-UA", "\"Chrome\";v=\"100.0.0\"")
                .containsEntry("Sec-CH-UA-Full-Version-List", "\"Chrome\";v=\"100.0.0\"")
                .containsEntry("Sec-CH-UA-Platform", "Windows")
                .containsEntry("Sec-CH-UA-Platform-Version", "10.0.0")
                .containsEntry("Sec-CH-UA-Model", "Test Model")
                .containsEntry("Sec-CH-UA-Arch", "x86")
                .containsEntry("Sec-CH-UA-Mobile", "?0");
    }

    @Test
    public void resolveWithFullDeviceAndHeadersShouldPrioritizeDevice() {
        // given
        final BrandVersion brandVersion = new BrandVersion(
                "Chrome",
                Arrays.asList("100", "0", "0"),
                null);

        final BrandVersion winBrandVersion = new BrandVersion(
                "Windows",
                Arrays.asList("10", "0", "0"),
                null);
        final UserAgent sua = UserAgent.builder()
                .browsers(List.of(brandVersion))
                .platform(winBrandVersion)
                .model("Test Model")
                .architecture("x86")
                .mobile(0)
                .build();

        final Device device = Device.builder()
                .sua(sua)
                .ua(TEST_USER_AGENT)
                .build();

        final Map<String, String> headers = new HashMap<>();
        headers.put("Sec-CH-UA", "Test UA-CH");
        headers.put("Sec-CH-UA-Full-Version-List", "Test-UA-Full-Version-List");
        headers.put("Sec-CH-UA-Platform", "Test-UA-Platform");
        headers.put("Sec-CH-UA-Platform-Version", "Test-UA-Platform-Version");
        headers.put("Sec-CH-UA-Model", "Test-UA-Model");
        headers.put("Sec-CH-UA-Arch", "Test-UA-Arch");
        headers.put("Sec-CH-UA-Mobile", "Test-UA-Mobile");
        headers.put("User-Agent", "Mozilla/5.0 (Test OS; 10) like Gecko");
        // when
        final Map<String, String> result = HeadersResolver.resolve(device, headers);

        // then
        assertThat(result)
                .containsEntry("Sec-CH-UA", "\"Chrome\";v=\"100.0.0\"")
                .containsEntry("Sec-CH-UA-Full-Version-List", "\"Chrome\";v=\"100.0.0\"")
                .containsEntry("Sec-CH-UA-Platform", "Windows")
                .containsEntry("Sec-CH-UA-Platform-Version", "10.0.0")
                .containsEntry("Sec-CH-UA-Model", "Test Model")
                .containsEntry("Sec-CH-UA-Arch", "x86")
                .containsEntry("Sec-CH-UA-Mobile", "?0");
    }

    @Test
    public void resolveWithMultipleBrandVersionsShouldFormatCorrectly() {
        // given
        final BrandVersion chrome = new BrandVersion("Chrome",
                Arrays.asList("100", "0"),
                null);
        final BrandVersion chromium = new BrandVersion("Chromium",
                Arrays.asList("100", "0"),
                null);

        final BrandVersion notABrand = new BrandVersion("Not\\A;Brand",
                Arrays.asList("99", "0"),
                null);

        final UserAgent sua = UserAgent.builder()
                .browsers(Arrays.asList(chrome, chromium, notABrand))
                .build();

        final Device device = Device.builder()
                .sua(sua)
                .build();

        // when
        final Map<String, String> result = HeadersResolver.resolve(device, new HashMap<>());

        // then
        final String expectedFormat = "\"Chrome\";v=\"100.0\", \"Chromium\";v=\"100.0\", \"Not\\A;Brand\";v=\"99.0\"";
        assertThat(result)
                .containsEntry("Sec-CH-UA", expectedFormat)
                .containsEntry("Sec-CH-UA-Full-Version-List", expectedFormat);
    }

    @Test
    public void resolveWithNullDeviceAndNullHeadersShouldReturnEmptyMap() {
        // when
        final Map<String, String> result = HeadersResolver.resolve(null, null);

        // then
        assertThat(result).isEmpty();
    }
}
