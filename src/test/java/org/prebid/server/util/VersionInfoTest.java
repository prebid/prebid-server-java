package org.prebid.server.util;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

public class VersionInfoTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

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
