package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WURFLDeviceDetectionConfigPropertiesTest {

    @Test
    void shouldInitializeWithEmptyValues() {
        // given
        final WURFLDeviceDetectionConfigProperties properties = new WURFLDeviceDetectionConfigProperties();

        // then
        assertThat(properties.getCacheSize()).isEqualTo(0);
        assertThat(properties.getWurflFileDirPath()).isNull();
        assertThat(properties.getWurflSnapshotUrl()).isNull();
        assertThat(properties.isExtCaps()).isFalse();
        assertThat(properties.isWurflRunUpdater()).isTrue();
    }

    @Test
    void shouldSetAndGetProperties() {
        // given
        final WURFLDeviceDetectionConfigProperties properties = new WURFLDeviceDetectionConfigProperties();

        // when
        properties.setCacheSize(1000);
        properties.setWurflFileDirPath("/path/to/file");

        properties.setWurflSnapshotUrl("https://example-scientiamobile.com/wurfl.zip");
        properties.setWurflRunUpdater(false);
        properties.setAllowedPublisherIds(List.of("1", "3"));
        properties.setExtCaps(true);

        // then
        assertThat(properties.getCacheSize()).isEqualTo(1000);
        assertThat(properties.getWurflFileDirPath()).isEqualTo("/path/to/file");
        assertThat(properties.getWurflSnapshotUrl()).isEqualTo("https://example-scientiamobile.com/wurfl.zip");
        assertThat(properties.isWurflRunUpdater()).isEqualTo(false);
        assertThat(properties.getAllowedPublisherIds()).isEqualTo(List.of("1", "3"));
        assertThat(properties.isExtCaps()).isTrue();
    }
}
