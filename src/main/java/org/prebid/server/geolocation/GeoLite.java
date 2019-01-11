package org.prebid.server.geolocation;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import io.vertx.core.Future;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Default implementation of {@link GeoLocationService}
 */
public class GeoLite implements GeoLocationService {

    private final DatabaseReader databaseReader;

    private GeoLite(DatabaseReader databaseReader) {
        this.databaseReader = Objects.requireNonNull(databaseReader);
    }

    public static GeoLite create(String dbArchive) {
        try (TarArchiveInputStream tarInput =
                     new TarArchiveInputStream(new GZIPInputStream(
                             GeoLite.class.getClassLoader().getResourceAsStream(dbArchive)))) {

            TarArchiveEntry currentEntry;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.getName().contains(".mmdb")) {
                    break;
                }
            }
            DatabaseReader databaseReader = new DatabaseReader.Builder(tarInput)
                    .fileMode(Reader.FileMode.MEMORY).build();

            return new GeoLite(databaseReader);
        } catch (IOException e) {
            throw new PreBidException(String.format(
                    "IO Exception occurred while trying to read an archive/db file: %s", e.getMessage()), e);
        } catch (NullPointerException e) {
            throw new PreBidException(String.format("No database file found with a file name: %s", dbArchive));
        }
    }

    private static String getCountryIso(String ip, DatabaseReader databaseReader) throws IOException, GeoIp2Exception {
        final CountryResponse response = databaseReader.country(InetAddress.getByName(ip));
        return response.getCountry().getIsoCode().toLowerCase();
    }

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        final String countryIso;
        try {
            countryIso = getCountryIso(ip, databaseReader);
        } catch (IOException | GeoIp2Exception e) {
            return Future.failedFuture(e.getMessage());
        }
        final GeoInfo geoInfo = GeoInfo.of(countryIso);
        return Future.succeededFuture(geoInfo);
    }
}
