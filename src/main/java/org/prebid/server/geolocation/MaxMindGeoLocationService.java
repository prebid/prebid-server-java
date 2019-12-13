package org.prebid.server.geolocation;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.prebid.server.execution.RemoteFileProcessor;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Implementation of of the {@link GeoLocationService}
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
            return Future.succeededFuture(GeoInfo.builder()
                    .vendor(VENDOR)
                    .continent(getCity(inetAddress).getContinent().getCode().toLowerCase())
                    .country(getCity(inetAddress).getCountry().getIsoCode().toLowerCase())
                    .region(getRegionCode(inetAddress))
                    //metro code is skipped as Max Mind uses Google's version (Nielsen DMAs required)
                    .city(getCity(inetAddress).getCity().getName())
                    .lat(getLocation(inetAddress).getLatitude().floatValue())
                    .lon(getLocation(inetAddress).getLongitude().floatValue())
                    .build());
        } catch (IOException | GeoIp2Exception e) {
            return Future.failedFuture(e);
        }
    }

    private CityResponse getCity(InetAddress inetAddress) throws IOException, GeoIp2Exception {
        return databaseReader.city(inetAddress);
    }

    private String getRegionCode(InetAddress inetAddress) throws IOException, GeoIp2Exception {
        final List<Subdivision> subdivisions = getCity(inetAddress).getSubdivisions();
        return CollectionUtils.isEmpty(subdivisions) ? null : subdivisions.get(0).getIsoCode();
    }

    private Location getLocation(InetAddress inetAddress) throws IOException, GeoIp2Exception {
        return getCity(inetAddress).getLocation();
    }
}
