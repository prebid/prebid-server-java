package org.prebid.server.geolocation;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.execution.RemoteFileProcessor;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Implementation of the {@link GeoLocationService}
 * backed by <a href="https://dev.maxmind.com/geoip/geoip2/geolite2/">MaxMind free database</a>
 */
public class MaxMindGeoLocationService implements GeoLocationService, RemoteFileProcessor {

    private static final String VENDOR = "maxmind";

    private static final String DATABASE_FILE_NAME = "GeoLite2-City.mmdb";

    private DatabaseReader databaseReader;

    public Future<?> setDataPath(String dataFilePath) {
        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(new GZIPInputStream(
                new FileInputStream(dataFilePath)))) {

            TarArchiveEntry currentEntry;
            boolean hasDatabaseFile = false;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.getName().contains(DATABASE_FILE_NAME)) {
                    hasDatabaseFile = true;
                    break;
                }
            }
            if (!hasDatabaseFile) {
                return Future.failedFuture(String.format("Database file %s not found in %s archive", DATABASE_FILE_NAME,
                        dataFilePath));
            }

            databaseReader = new DatabaseReader.Builder(tarInput).fileMode(Reader.FileMode.MEMORY).build();
            return Future.succeededFuture();
        } catch (IOException e) {
            return Future.failedFuture(
                    String.format("IO Exception occurred while trying to read an archive/db file: %s", e.getMessage()));
        }
    }

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        if (databaseReader == null) {
            return Future.failedFuture("Geo location database file hasn't been downloaded yet, try again later");
        }

        try {
            final InetAddress inetAddress = InetAddress.getByName(ip);
            final CityResponse cityResponse = databaseReader.city(inetAddress);
            final Location location = cityResponse != null ? cityResponse.getLocation() : null;

            return Future.succeededFuture(GeoInfo.builder()
                    .vendor(VENDOR)
                    .continent(resolveContinent(cityResponse))
                    .country(resolveCountry(cityResponse))
                    .region(resolveRegion(cityResponse))
                    // metro code is skipped as Max Mind uses Google's version (Nielsen DMAs required)
                    .city(resolveCity(cityResponse))
                    .lat(resolveLatitude(location))
                    .lon(resolveLongitude(location))
                    .build());
        } catch (IOException | GeoIp2Exception e) {
            return Future.failedFuture(e);
        }
    }

    private static String resolveContinent(CityResponse cityResponse) {
        final Continent continent = cityResponse != null ? cityResponse.getContinent() : null;
        final String code = continent != null ? continent.getCode() : null;
        return StringUtils.lowerCase(code);
    }

    private static String resolveCountry(CityResponse cityResponse) {
        final Country country = cityResponse != null ? cityResponse.getCountry() : null;
        final String isoCode = country != null ? country.getIsoCode() : null;
        return StringUtils.lowerCase(isoCode);
    }

    private static String resolveRegion(CityResponse cityResponse) {
        final List<Subdivision> subdivisions = cityResponse != null ? cityResponse.getSubdivisions() : null;
        final Subdivision firstSubdivision = CollectionUtils.isEmpty(subdivisions) ? null : subdivisions.get(0);
        return firstSubdivision != null ? firstSubdivision.getIsoCode() : null;
    }

    private static String resolveCity(CityResponse cityResponse) {
        final City city = cityResponse != null ? cityResponse.getCity() : null;
        return city != null ? city.getName() : null;
    }

    private static Float resolveLatitude(Location location) {
        final Double latitude = location != null ? location.getLatitude() : null;
        return latitude != null ? latitude.floatValue() : null;
    }

    private static Float resolveLongitude(Location location) {
        final Double longitude = location != null ? location.getLongitude() : null;
        return longitude != null ? longitude.floatValue() : null;
    }
}
