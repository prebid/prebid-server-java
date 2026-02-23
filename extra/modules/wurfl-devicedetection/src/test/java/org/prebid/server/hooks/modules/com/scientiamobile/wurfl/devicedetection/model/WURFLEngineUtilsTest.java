package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;

import static org.mockito.Mock.Strictness.LENIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class WURFLEngineUtilsTest {

    @Mock(strictness = LENIENT)
    private WURFLDeviceDetectionConfigProperties configProperties;

    @Test
    public void extractWURFLFileNameShouldReturnCorrectFileName() {
        // given
        final String url = "https://data.examplehost.com/snapshot/wurfl-latest.zip";

        // when
        final String result = WURFLEngineUtils.extractWURFLFileName(url);

        // then
        assertThat(result).isEqualTo("wurfl-latest.zip");
    }

    @Test
    public void extractWURFLFileNameShouldHandleSimpleFileName() {
        // given
        final String url = "http://example.com/wurfl.zip";

        // when
        final String result = WURFLEngineUtils.extractWURFLFileName(url);

        // then
        assertThat(result).isEqualTo("wurfl.zip");
    }

    @Test
    public void extractWURFLFileNameShouldHandleComplexPath() {
        // given
        final String url = "https://examplehost.com/path/to/files/wurfl-snapshot.zip";

        // when
        final String result = WURFLEngineUtils.extractWURFLFileName(url);

        // then
        assertThat(result).isEqualTo("wurfl-snapshot.zip");
    }

    @Test
    public void extractWURFLFileNameShouldHandleUrlWithQueryParams() {
        // given
        final String url = "https://example.com/wurfl.zip?version=latest&format=zip";

        // when
        final String result = WURFLEngineUtils.extractWURFLFileName(url);

        // then
        assertThat(result).isEqualTo("wurfl.zip");
    }

    @Test
    public void extractWURFLFileNameShouldThrowExceptionForNullUrl() {
        // when & then
        assertThatThrownBy(() -> WURFLEngineUtils.extractWURFLFileName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid WURFL snapshot URL: null");
    }

    @Test
    public void initializeEngineShouldThrowExceptionForNullDataFilePath() {
        // when & then
        assertThatThrownBy(() -> WURFLEngineUtils.initializeEngine(configProperties, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid WURFL snapshot URL: null");
    }
}
