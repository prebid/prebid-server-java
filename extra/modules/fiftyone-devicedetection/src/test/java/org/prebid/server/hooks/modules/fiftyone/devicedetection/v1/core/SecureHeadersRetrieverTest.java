package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.UserAgent;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SecureHeadersRetrieverTest {

    @Test
    public void callShouldAddEmptyMapOfSecureHeadersWhenUserAgentIsEmpty() {
        // given
        final UserAgent userAgent = UserAgent.builder().build();

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence).isEmpty();
    }

    @Test
    public void callShouldAddBrowsersToSecureHeaders() {
        // given
        final UserAgent userAgent = UserAgent.builder()
                .browsers(List.of(
                        new BrandVersion("Nickel", List.of("6", "3", "1", "a"), null),
                        new BrandVersion(null, List.of("7", "52"), null), // should be skipped
                        new BrandVersion("FrostCat", List.of("9", "2", "5", "8"), null)
                ))
                .build();
        final String expectedBrowsers = "\"Nickel\";v=\"6.3.1.a\", \"FrostCat\";v=\"9.2.5.8\"";

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(2);
        assertThat(evidence.get("header.Sec-CH-UA")).isEqualTo(expectedBrowsers);
        assertThat(evidence.get("header.Sec-CH-UA-Full-Version-List")).isEqualTo(expectedBrowsers);
    }

    @Test
    public void callShouldAddPlatformToSecureHeaders() {
        final UserAgent userAgent = UserAgent.builder()
                .platform(new BrandVersion("Cyborg", List.of("19", "5"), null))
                .build();
        final String expectedPlatformName = "\"Cyborg\"";
        final String expectedPlatformVersion = "\"19.5\"";

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(2);
        assertThat(evidence.get("header.Sec-CH-UA-Platform")).isEqualTo(expectedPlatformName);
        assertThat(evidence.get("header.Sec-CH-UA-Platform-Version")).isEqualTo(expectedPlatformVersion);
    }

    @Test
    public void callShouldAddIsMobileToSecureHeaders() {
        final UserAgent userAgent = UserAgent.builder()
                .mobile(5)
                .build();
        final String expectedIsMobile = "?5";

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Mobile")).isEqualTo(expectedIsMobile);
    }

    @Test
    public void callShouldAddArchitectureToSecureHeaders() {
        final UserAgent userAgent = UserAgent.builder()
                .architecture("LEG")
                .build();
        final String expectedArchitecture = "\"LEG\"";

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Arch")).isEqualTo(expectedArchitecture);
    }

    @Test
    public void callShouldAddBitnessToSecureHeaders() {
        final UserAgent userAgent = UserAgent.builder()
                .bitness("doubtful")
                .build();
        final String expectedBitness = "\"doubtful\"";

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Bitness")).isEqualTo(expectedBitness);
    }

    @Test
    public void callShouldAddModelToSecureHeaders() {
        final UserAgent userAgent = UserAgent.builder()
                .model("reflectivity")
                .build();
        final String expectedModel = "\"reflectivity\"";

        // when
        final Map<String, String> evidence = SecureHeadersRetriever.retrieveFrom(userAgent);

        // then
        assertThat(evidence).isNotNull();
        assertThat(evidence.size()).isEqualTo(1);
        assertThat(evidence.get("header.Sec-CH-UA-Model")).isEqualTo(expectedModel);
    }
}
