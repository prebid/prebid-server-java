package org.prebid.server.util;

import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class ResourceUtilTest {

    @Test
    public void shouldThrowIllegalArgumentExceptionOnNotExistingPath() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> ResourceUtil.readFromClasspath("notExistingPath"));
    }

    @Test
    public void readFromFileReturnsExpectedContent() throws IOException {
        // when
        final String content = ResourceUtil.readFromClasspath(
                "org/prebid/server/util/resource/test-data.txt");

        // then
        assertThat(content).isEqualTo("Test content");
    }
}
