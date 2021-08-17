package org.prebid.server.geolocation;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.FieldSetter;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.IOException;
import java.util.ArrayList;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class MaxMindGeoLocationServiceTest {

    private static final String TEST_IP = "80.215.195.122";

    private MaxMindGeoLocationService maxMindGeoLocationService;

    @Before
    public void setUp() {
        maxMindGeoLocationService = new MaxMindGeoLocationService();
    }

    @Test
    public void lookupShouldReturnFailedFutureWhenDatabaseReaderWasNotSet() {
        // given and when
        final Future<GeoInfo> result = maxMindGeoLocationService.lookup(TEST_IP, null);

        // then
        assertTrue(result.failed());
        assertThat(result.cause())
                .hasMessage("Geo location database file hasn't been downloaded yet, try again later");
    }

    @Test
    public void setDatabaseReaderShouldReturnFailedFutureIfDatabaseArchiveNotFound() {
        // given and when
        final Future<?> result = maxMindGeoLocationService.setDataPath("no_file");

        // then
        assertTrue(result.failed());
        assertThat(result.cause())
                .hasMessageStartingWith("IO Exception occurred while trying to read an archive/db file: no_file");
    }

    @Test
    public void lookupShouldReturnCountryIsoWhenDatabaseReaderWasSet() throws NoSuchFieldException, IOException,
            GeoIp2Exception {
        // given
        final Country country = new Country(null, null, null, "fr", null);
        final Continent continent = new Continent(null, "eu", null, null);
        final City city = new City(singletonList("test"), null, null, singletonMap("test", "Paris"));
        final Location location = new Location(null, null, 48.8566, 2.3522,
                null, null, null);
        final ArrayList<Subdivision> subdivisions = new ArrayList<>();
        subdivisions.add(new Subdivision(null, null, null, "paris", null));
        final CityResponse cityResponse = new CityResponse(city, continent, country, location, null,
                null, null, null, subdivisions, null);

        final DatabaseReader databaseReader = Mockito.mock(DatabaseReader.class);
        given(databaseReader.city(any())).willReturn(cityResponse);

        FieldSetter.setField(maxMindGeoLocationService,
                maxMindGeoLocationService.getClass().getDeclaredField("databaseReader"), databaseReader);

        // when
        final Future<GeoInfo> future = maxMindGeoLocationService.lookup(TEST_IP, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(GeoInfo.builder()
                        .vendor("maxmind")
                        .continent("eu")
                        .country("fr")
                        .region("paris")
                        .city("Paris")
                        .lat(48.8566f)
                        .lon(2.3522f)
                        .build());
    }

    @Test
    public void lookupShouldTolerateMissingGeoInfo() throws IOException, GeoIp2Exception, NoSuchFieldException {
        // given
        final DatabaseReader databaseReader = Mockito.mock(DatabaseReader.class);
        given(databaseReader.city(any())).willReturn(null);

        FieldSetter.setField(maxMindGeoLocationService,
                maxMindGeoLocationService.getClass().getDeclaredField("databaseReader"), databaseReader);

        // when
        final Future<GeoInfo> future = maxMindGeoLocationService.lookup(TEST_IP, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GeoInfo.builder().vendor("maxmind").build());
    }
}
