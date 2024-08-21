package org.prebid.server.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class VersionInfoTest extends VertxTest {

    @Test
    public void shouldCreateVersionWithUndefinedForAllFieldsIfFileWasNotFound() {
        // when
        final VersionInfo versionInfo = VersionInfo.create("not_found.json", jacksonMapper);

        // then
        assertThat(versionInfo)
                .extracting(VersionInfo::getVersion, VersionInfo::getCommitHash)
                .containsOnly("undefined", "undefined");
    }

    @Test
    public void shouldCreateVersionInfoWithAllProperties() {
        // when
        final VersionInfo versionInfo = VersionInfo.create(
                "org/prebid/server/util/resource/version/version.json", jacksonMapper);

        // then
        assertThat(versionInfo)
                .extracting(VersionInfo::getVersion, VersionInfo::getCommitHash)
                .containsOnly("1.41.0", "4df3f6192d7938ccdaac04df783c46c7e8847d08");
    }

    @Test
    public void shouldCreateVersionWithUndefinedForEachMissingPropertyInFile() {
        // when
        final VersionInfo versionInfo = VersionInfo.create(
                "org/prebid/server/util/resource/version/empty.json", jacksonMapper);

        // then
        assertThat(versionInfo)
                .extracting(VersionInfo::getVersion, VersionInfo::getCommitHash)
                .containsOnly("undefined", "undefined");
    }
}
