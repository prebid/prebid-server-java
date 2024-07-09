package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DataFileTest {
    @Test
    public void shouldReturnPath() {
        // given
        final String path = "/path/to/file.txt";

        // when
        final DataFile dataFile = new DataFile();
        dataFile.setPath(path);

        // then
        assertThat(dataFile.getPath()).isEqualTo(path);
    }

    @Test
    public void shouldReturnMakeTempCopy() {
        // given
        final boolean makeCopy = true;

        // when
        final DataFile dataFile = new DataFile();
        dataFile.setMakeTempCopy(makeCopy);

        // then
        assertThat(dataFile.getMakeTempCopy()).isEqualTo(makeCopy);
    }

    @Test
    public void shouldReturnUpdate() {
        // given
        final DataFileUpdate dataFileUpdate = new DataFileUpdate();
        dataFileUpdate.setUrl("www.void");

        // when
        final DataFile dataFile = new DataFile();
        dataFile.setUpdate(dataFileUpdate);

        // then
        assertThat(dataFile.getUpdate()).isEqualTo(dataFileUpdate);
    }

    @Test
    public void shouldHaveDescription() {
        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("/etc/null");

        // when and then
        assertThat(dataFile.toString()).isNotBlank();
    }
}
