package org.prebid.server.geolocation;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import io.vertx.core.Future;
import org.junit.Test;
import org.mockito.Mockito;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class MaxMindGeoLocationServiceTest {

    private static final String TEST_IP = "80.215.195.122";

    @Test
    public void lookupShouldReturnFailedFutureWhenDatabaseReaderWasNotSet() {
        // given and when
        final Future<GeoInfo> result = new MaxMindGeoLocationService().lookup(TEST_IP, null);

        // then
        assertTrue(result.failed());
    }

    @Test
    public void setDatabaseReaderShouldThrowExceptionIfDatabaseArchiveNotFound() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> new MaxMindGeoLocationService().setDatabaseReader("no_file"))
                .withMessage("IO Exception occurred while trying to read an archive/db file: no_file " +
                        "(No such file or directory)");
    }

    @Test
    public void lookupShouldReturnCountryIsoWhenDatabaseReaderWasSet() throws IOException, GeoIp2Exception {
        // given
        final DatabaseReader databaseReader = Mockito.mock(DatabaseReader.class);

        final Country country = new Country(null, null, null, "fr", null);
        final CountryResponse countryResponse = new CountryResponse(null, country, null, null, null, null);

        given(databaseReader.country(any())).willReturn(countryResponse);

        // when
        final Future<GeoInfo> future = new MaxMindGeoLocationService(databaseReader).lookup(TEST_IP, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .extracting(GeoInfo::getCountry)
                .containsOnly("fr");
    }
}
