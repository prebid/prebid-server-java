package org.prebid.server.bidder.sparteo;

import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.sparteo.util.SparteoUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class SparteoUtilTest extends VertxTest {

    @Test
    public void normalizeHostnameShouldReturnBaseDomain() {
        final String base = "dev.sparteo.com";

        assertThat(SparteoUtil.normalizeHostname(base)).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname("DeV.SpArTeO.CoM")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname("  " + base + "  ")).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname("www." + base)).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname("WWW." + base)).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname(base + ".")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname(base + "..")).isEqualTo(base + ".");

        assertThat(SparteoUtil.normalizeHostname("null")).isEqualTo("");
        assertThat(SparteoUtil.normalizeHostname("NuLl")).isEqualTo("");

        assertThat(SparteoUtil.normalizeHostname("")).isEqualTo("");
        assertThat(SparteoUtil.normalizeHostname("   ")).isEqualTo("");

        assertThat(SparteoUtil.normalizeHostname("www2." + base)).isEqualTo("www2." + base);

        assertThat(SparteoUtil.normalizeHostname("www." + base + ":8080")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname("DEV.SPARTEO.COM:443")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname(base + ".:8443")).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname(base + "/some/path?x=1")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname("www." + base + "/p")).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname(base + ":8080/p?q=1")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname("www." + base + ":3000/some/path")).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname("https://www." + base + "/x")).isEqualTo(base);
        assertThat(SparteoUtil.normalizeHostname("http://WWW." + base + ":8080/abc")).isEqualTo(base);

        assertThat(SparteoUtil.normalizeHostname("   https://www." + base + ":3000/x  ")).isEqualTo(base);
    }
}
