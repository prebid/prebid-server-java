package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataFileUpdateTest {
    @Test
    public void shouldReturnAuto() {
        // given
        final boolean value = true;

        // when
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setAuto(value);

        // then
        assertThat(dataFileUpdate.getAuto()).isEqualTo(value);
    }

    @Test
    public void shouldReturnOnStartup() {
        // given
        final boolean value = true;

        // when
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setOnStartup(value);

        // then
        assertThat(dataFileUpdate.getOnStartup()).isEqualTo(value);
    }

    @Test
    public void shouldReturnUrl() {
        // given
        final String value = "/path/to/file.txt";

        // when
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setUrl(value);

        // then
        assertThat(dataFileUpdate.getUrl()).isEqualTo(value);
    }

    @Test
    public void shouldReturnLicenseKey() {
        // given
        final String value = "/path/to/file.txt";

        // when
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setLicenseKey(value);

        // then
        assertThat(dataFileUpdate.getLicenseKey()).isEqualTo(value);
    }

    @Test
    public void shouldReturnWatchFileSystem() {
        // given
        final boolean value = true;

        // when
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setWatchFileSystem(value);

        // then
        assertThat(dataFileUpdate.getWatchFileSystem()).isEqualTo(value);
    }

    @Test
    public void shouldReturnPollingInterval() {
        // given
        final int value = 42;

        // when
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setPollingInterval(value);

        // then
        assertThat(dataFileUpdate.getPollingInterval()).isEqualTo(value);
    }

    @Test
    public void shouldHaveDescription() {
        // given
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setPollingInterval(29);

        // when and then
        assertThat(dataFileUpdate.toString()).isNotBlank();
    }
}
