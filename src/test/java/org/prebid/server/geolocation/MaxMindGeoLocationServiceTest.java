package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.junit.Test;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.geolocation.model.GeoInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class MaxMindGeoLocationServiceTest {

    private static final String ARCHIVE_NAME = "GeoLite2-test.tar.gz";
    private static final String DB_FILE_NAME = "GeoLite2-Country.mmdb";

    @Test
    public void creationShouldThrowExceptionIfDatabaseArchiveNotFound() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> MaxMindGeoLocationService.create("no_file", null))
                .withMessage("No database archive found with a file name: no_file");
    }

    @Test
    public void creationShouldThrowExceptionIfFileIsNotArchive() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> MaxMindGeoLocationService.create("not_gzip_file.txt", DB_FILE_NAME))
                .withMessage("IO Exception occurred while trying to read an archive/db file: Not in GZIP format");
    }

    @Test
    public void creationShouldThrowExceptionIfDatabaseFileNotFoundInArchive() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> MaxMindGeoLocationService.create(ARCHIVE_NAME, "no_file"))
                .withMessage("Database file no_file not found in GeoLite2-test.tar.gz archive");
    }

    @Test
    public void shouldReturnCountryISO() {
        // given
        final MaxMindGeoLocationService maxMindGeoLocationService =
                MaxMindGeoLocationService.create(ARCHIVE_NAME, DB_FILE_NAME);

        // when
        final Future<GeoInfo> future = maxMindGeoLocationService.lookup("80.215.195.122", null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .extracting(GeoInfo::getCountry)
                .containsOnly("fr");
    }
}
