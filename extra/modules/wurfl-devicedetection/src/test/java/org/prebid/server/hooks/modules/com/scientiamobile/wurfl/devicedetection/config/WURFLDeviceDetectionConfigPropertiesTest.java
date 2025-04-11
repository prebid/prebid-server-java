package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

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
        assertThat(properties.getUpdateConnTimeoutMs()).isEqualTo(5000);
        assertThat(properties.getUpdateRetries()).isEqualTo(3);
        assertThat(properties.getRetryIntervalMs()).isEqualTo(200);
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
        properties.setAllowedPublisherIds(Set.of("1", "3"));
        properties.setExtCaps(true);
        properties.setUpdateConnTimeoutMs(7000);
        properties.setUpdateRetries(1);
        properties.setRetryIntervalMs(100L);


        // then
        assertThat(properties.getCacheSize()).isEqualTo(1000);
        assertThat(properties.getWurflFileDirPath()).isEqualTo("/path/to/file");
        assertThat(properties.getWurflSnapshotUrl()).isEqualTo("https://example-scientiamobile.com/wurfl.zip");
        assertThat(properties.isWurflRunUpdater()).isEqualTo(false);
        assertThat(properties.getAllowedPublisherIds()).isEqualTo(Set.of("1", "3"));
        assertThat(properties.isExtCaps()).isTrue();
        assertThat(properties.getUpdateConnTimeoutMs()).isEqualTo(7000);
        assertThat(properties.getUpdateRetries()).isEqualTo(1);
        assertThat(properties.getRetryIntervalMs()).isEqualTo(100);
    }
}
