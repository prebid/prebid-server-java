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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.ReflectionMemberAccessor;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.IOException;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class MaxMindGeoLocationServiceTest {

    private static final String TEST_IP = "80.215.195.122";

    private MaxMindGeoLocationService maxMindGeoLocationService;

    @BeforeEach
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
            GeoIp2Exception, IllegalAccessException {
        // given
        final Country country = Mockito.mock(Country.class);
        Mockito.when(country.getIsoCode()).thenReturn("fr");

        final Continent continent = Mockito.mock(Continent.class);
        Mockito.when(continent.getCode()).thenReturn("eu");

        final City city = Mockito.mock(City.class);
        Mockito.when(city.getNames()).thenReturn(singletonMap("en", "Paris"));
        Mockito.when(city.getName()).thenReturn("Paris");

        final Location location = Mockito.mock(Location.class);
        Mockito.when(location.getLatitude()).thenReturn(48.8566);
        Mockito.when(location.getLongitude()).thenReturn(2.3522);

        final Subdivision subdivision = Mockito.mock(Subdivision.class);
        Mockito.when(subdivision.getIsoCode()).thenReturn("paris");

        final CityResponse cityResponse = Mockito.mock(CityResponse.class);
        Mockito.when(cityResponse.getCountry()).thenReturn(country);
        Mockito.when(cityResponse.getContinent()).thenReturn(continent);
        Mockito.when(cityResponse.getCity()).thenReturn(city);
        Mockito.when(cityResponse.getLocation()).thenReturn(location);
        Mockito.when(cityResponse.getSubdivisions()).thenReturn(singletonList(subdivision));

        final DatabaseReader databaseReader = Mockito.mock(DatabaseReader.class);
        given(databaseReader.city(any())).willReturn(cityResponse);

        new ReflectionMemberAccessor().set(
                        maxMindGeoLocationService.getClass().getDeclaredField("databaseReader"),
                        maxMindGeoLocationService,
                        databaseReader);

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
    public void lookupShouldTolerateMissingGeoInfo() throws IOException, GeoIp2Exception, NoSuchFieldException,
            IllegalAccessException {
        // given
        final DatabaseReader databaseReader = Mockito.mock(DatabaseReader.class);
        given(databaseReader.city(any())).willReturn(null);

        new ReflectionMemberAccessor().set(maxMindGeoLocationService.getClass().getDeclaredField("databaseReader"),
                maxMindGeoLocationService, databaseReader);

        // when
        final Future<GeoInfo> future = maxMindGeoLocationService.lookup(TEST_IP, null);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(GeoInfo.builder().vendor("maxmind").build());
    }
}
