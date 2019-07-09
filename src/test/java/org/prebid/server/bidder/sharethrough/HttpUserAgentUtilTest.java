package org.prebid.server.bidder.sharethrough;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpUserAgentUtilTest {

    @Test
    public void isAndroidShouldReturnTrueWhenDeviceOcIsAndroid() {
        // given
        final String firstUaAndroid = "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) " +
                "AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19";

        final String secondUaAndroid = "Mozilla/5.0 (Linux; android 4.4; Nexus 5 Build/_BuildID_) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36";

        // when and then
        assertThat(HttpUserAgentUtil.isAndroid(firstUaAndroid)).isTrue();
        assertThat(HttpUserAgentUtil.isAndroid(secondUaAndroid)).isTrue();
    }

    @Test
    public void isIosShouldReturnTrueWhenDeviceIsIphoneOrIpadORIpod() {
        // given
        final String uaIphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/603.1.23 (KHTML, like Gecko) Version/10.0 Mobile/14E5239e Safari/602.1";
        final String uaIpad = "Mozilla/5.0(iPad; U; like Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10";
        final String uaIpod = "Mozilla/5.0(iPod; U; Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10";

        // when and then
        assertThat(HttpUserAgentUtil.isIos(uaIphone)).isTrue();
        assertThat(HttpUserAgentUtil.isIos(uaIpad)).isTrue();
        assertThat(HttpUserAgentUtil.isIos(uaIpod)).isTrue();
    }

    @Test
    public void isAtMinChromeIosVersionShouldReturnFalseWhenVersionIsNotSetup() {
        // given
        final String uaWithoutCriOsVersion = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/602.1.50 (KHTML, like Gecko) CriOS/ Mobile/14E5239e Safari/602.1";
        final String uaWithoutCriOs = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/602.1.50 (KHTML, like Gecko) Mobile/14E5239e Safari/602.1";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaWithoutCriOsVersion, 2)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaWithoutCriOsVersion, 1)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaWithoutCriOs, 1)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaWithoutCriOs, 100)).isFalse();
    }


    @Test
    public void isAtMinChromeVersionShouldReturnFalseWhenVersionIsNotSetup() {
        // given
        final String uaWithoutChromeVersion = "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) " +
                "AppleWebKit/535.19 (KHTML, like Gecko) Chrome/ Mobile Safari/535.19";
        final String uaWithoutChrome = "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) " +
                "AppleWebKit/535.19 (KHTML, like Gecko)Mobile Safari/535.19";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinChromeVersion(uaWithoutChromeVersion, 2)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeVersion(uaWithoutChromeVersion, 1)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeVersion(uaWithoutChrome, 1)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeVersion(uaWithoutChrome, 100)).isFalse();
    }

    @Test
    public void isAtMinSafariVersionShouldReturnFalseWhenVersionIsNotSetup() {
        // given
        final String uaWithoutSafariVersion = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/603.1.23 (KHTML, like Gecko) Version/ Mobile/14E5239e Safari/602.1";
        final String uaWithoutSafari = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/603.1.23 (KHTML, like Gecko) Mobile/14E5239e Safari/602.1";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaWithoutSafariVersion, 2)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaWithoutSafariVersion, 1)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaWithoutSafari, 1)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaWithoutSafari, 100)).isFalse();
    }


    @Test
    public void isAtMinChromeVersionShouldReturnFalseWhenVersionIsLessThanMinVersion() {
        // given
        final String firstUaAndroid = "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) " +
                "AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19";
        final String secondUaAndroid = "Mozilla/5.0 (Linux; android 4.4; Nexus 5 Build/_BuildID_) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(firstUaAndroid, 19)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(firstUaAndroid, 20)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(firstUaAndroid, 190)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(secondUaAndroid, 31)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(secondUaAndroid, 32)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(secondUaAndroid, 300)).isFalse();
    }

    @Test
    public void isAtMinChromeIosVersionShouldReturnFalseWhenVersionIsLessThanMinVersion() {
        // given
        final String uaIphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/602.1.50 (KHTML, like Gecko) CriOS/56.0.2924.75 Mobile/14E5239e ";
        final String uaIpad = "Mozilla/5.0(iPad; U; like Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) CriOS/23.0.2924.75  Mobile/7B314 ";
        final String uaIpod = "Mozilla/5.0(iPod; U; Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) CriOS/13.0.2924.75  Mobile/7B314 ";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIphone, 57)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIphone, 100)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIphone, 59)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpad, 24)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpad, 25)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpad, 200)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpod, 14)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpod, 15)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpod, 240)).isFalse();
    }

    @Test
    public void isAtMinSafariVersionShouldReturnFalseWhenVersionIsLessThanMinVersion() {
        // given
        final String uaIphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/603.1.23 (KHTML, like Gecko) Version/10.0 Mobile/14E5239e Safari/602.1";
        final String uaIpad = "Mozilla/5.0(iPad; U; like Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10";
        final String uaIpod = "Mozilla/5.0(iPod; U; Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) Version/4.0.4 Mobile/7B314 Safari/531.21.10";

        // when and then
        assertThat(HttpUserAgentUtil.isIos(uaIphone)).isTrue();
        assertThat(HttpUserAgentUtil.isIos(uaIpad)).isTrue();
        assertThat(HttpUserAgentUtil.isIos(uaIpod)).isTrue();
    }

    @Test
    public void isAtMinChromeVersionShouldReturnTrueWhenVersionIsGreaterEqualMinVersion() {
        // given
        final String firstUaAndroid = "Mozilla/5.0 (Linux; Android 4.0.4; Galaxy Nexus Build/IMM76B) " +
                "AppleWebKit/535.19 (KHTML, like Gecko) Chrome/18.0.1025.133 Mobile Safari/535.19";
        final String secondUaAndroid = "Mozilla/5.0 (Linux; android 4.4; Nexus 5 Build/_BuildID_) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/30.0.0.0 Mobile Safari/537.36";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(firstUaAndroid, 18)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(firstUaAndroid, 19)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(firstUaAndroid, 100)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(secondUaAndroid, 30)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(secondUaAndroid, 31)).isFalse();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(secondUaAndroid, 300)).isFalse();
    }

    @Test
    public void isAtMinChromeIosVersionShouldReturnTrueWhenVersionIsGreaterEqualMinVersion() {
        // given
        final String uaIphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/602.1.50 (KHTML, like Gecko) CriOS/56.0.2924.75 Mobile/14E5239e ";
        final String uaIpad = "Mozilla/5.0(iPad; U; like Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) CriOS/23.0.2924.75  Mobile/7B314 ";
        final String uaIpod = "Mozilla/5.0(iPod; U; Mac OS X; en-us) " +
                "AppleWebKit/531.21.10 (KHTML, like Gecko) CriOS/13.0.2924.75  Mobile/7B314 ";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIphone, 56)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIphone, 54)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIphone, 23)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpad, 23)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpad, 22)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpad, 10)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpod, 13)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpod, 11)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinChromeIosVersion(uaIpod, 2)).isTrue();
    }

    @Test
    public void isAtMinSafariVersionShouldReturnTrueWhenVersionIsGreaterEqualMinVersion() {
        // given
        final String uaSafari = "Mozilla/5.0 (iPhone; CPU iPhone OS 10_3 like Mac OS X) " +
                "AppleWebKit/603.1.23 (KHTML, like Gecko) Version/10.0 Mobile/14E5239e Safari/602.1";

        // when and then
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaSafari, 10)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaSafari, 9)).isTrue();
        assertThat(HttpUserAgentUtil.isAtMinSafariVersion(uaSafari, 1)).isTrue();
    }
}

