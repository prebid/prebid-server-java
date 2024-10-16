package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GreenbidsUserAgentTest {

    @Test
    public void getDeviceShouldReturnPCWhenWindowsNTInUserAgent() {
        // given
        final String userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        // when
        final GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgentString);

        // then
        assertThat(greenbidsUserAgent.getDevice()).isEqualTo("PC");
    }

    @Test
    public void getDeviceShouldReturnDeviceIPhoneWhenIOSInUserAgent() {
        // given
        final String userAgentString = "Mozilla/5.0 (iPhone; CPU iPhone OS 14_2 like Mac OS X)";

        // when
        final GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgentString);

        // then
        assertThat(greenbidsUserAgent.getDevice()).isEqualTo("iPhone");
    }

    @Test
    public void getBrowserShouldReturnBrowserNameAndVersionWhenUserAgentIsPresent() {
        // given
        final String userAgentString =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        + " (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3";

        // when
        final GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgentString);

        // then
        assertThat(greenbidsUserAgent.getBrowser()).isEqualTo("Chrome 58");
    }

    @Test
    public void getBrowserShouldReturnEmptyStringWhenBrowserIsNull() {
        // given
        final String userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        // when
        final GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgentString);

        // then
        assertThat(greenbidsUserAgent.getBrowser()).isEqualTo(StringUtils.EMPTY);
    }
}
