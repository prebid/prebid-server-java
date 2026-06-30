package org.prebid.server.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UriTest {

    @Test
    public void expandShouldEncodeDangerousCharacters() {
        // when and then
        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com/a").expand())
                .isEqualTo("https://malicious.com%2Fa.prebid.org");

        assertThat(Uri.of("https://prebid.org.{inject}").replaceMacro("inject", "@malicious.com").expand())
                .isEqualTo("https://prebid.org.%40malicious.com");

        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com:8080").expand())
                .isEqualTo("https://malicious.com%3A8080.prebid.org");

        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com?a").expand())
                .isEqualTo("https://malicious.com%3Fa.prebid.org");

        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com#a").expand())
                .isEqualTo("https://malicious.com%23a.prebid.org");
    }
}
