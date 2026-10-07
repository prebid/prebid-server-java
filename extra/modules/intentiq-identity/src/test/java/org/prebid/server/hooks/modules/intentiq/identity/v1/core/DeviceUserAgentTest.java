package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeviceUserAgentTest {

    @Test
    public void shouldReturnEmptyForBlankUa() {
        assertThat(DeviceUserAgent.normalize(null)).isEmpty();
        assertThat(DeviceUserAgent.normalize("  ")).isEmpty();
    }

    @Test
    public void shouldReturnEmptyForUnrecognizedUa() {
        assertThat(DeviceUserAgent.normalize("UA")).isEmpty();
    }

    @Test
    public void shouldNormalizeIphoneSafari() {
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

        assertThat(DeviceUserAgent.normalize(ua))
                .contains("iOS17")
                .contains("iPhone")
                .doesNotContain(" ");
    }

    @Test
    public void shouldBeDeterministic() {
        final String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        assertThat(DeviceUserAgent.normalize(ua)).isEqualTo(DeviceUserAgent.normalize(ua));
        assertThat(DeviceUserAgent.normalize(ua)).contains("Chrome120");
    }
}
