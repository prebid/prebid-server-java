package org.prebid.server.geolocation;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import io.vertx.core.Future;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/**
 * Implementation of of the {@link GeoLocationService}
 * backed by <a href="https://dev.maxmind.com/geoip/geoip2/geolite2/">MaxMind free database</a>
 */
public class MaxMindGeoLocationService implements GeoLocationService {

    private final DatabaseReader databaseReader;

    private MaxMindGeoLocationService(DatabaseReader databaseReader) {
        this.databaseReader = Objects.requireNonNull(databaseReader);
    }

    public static MaxMindGeoLocationService create(String dbArchive, String databaseFileName) {
        final InputStream resourceAsStream = MaxMindGeoLocationService.class.getResourceAsStream(dbArchive);

        if (resourceAsStream == null) {
            throw new PreBidException(String.format("No database archive found with a file name: %s", dbArchive));
        }

        try (TarArchiveInputStream tarInput = new TarArchiveInputStream(
                new GZIPInputStream(resourceAsStream))) {

            TarArchiveEntry currentEntry;
            boolean hasDatabaseFile = false;
            while ((currentEntry = tarInput.getNextTarEntry()) != null) {
                if (currentEntry.getName().contains(databaseFileName)) {
                    hasDatabaseFile = true;
                    break;
                }
            }
            if (!hasDatabaseFile) {
                throw new PreBidException(String.format("Database file %s not found in %s archive",
                        databaseFileName, dbArchive));
            }
            final DatabaseReader databaseReader = new DatabaseReader.Builder(tarInput)
                    .fileMode(Reader.FileMode.MEMORY).build();

            return new MaxMindGeoLocationService(databaseReader);
        } catch (IOException e) {
            throw new PreBidException(String.format(
                    "IO Exception occurred while trying to read an archive/db file: %s", e.getMessage()), e);
        }
    }

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        final String countryIso;
        try {
            countryIso = databaseReader.country(InetAddress.getByName(ip))
                    .getCountry()
                    .getIsoCode()
                    .toLowerCase();
        } catch (IOException | GeoIp2Exception e) {
            return Future.failedFuture(e);
        }
        final GeoInfo geoInfo = GeoInfo.of(countryIso);
        return Future.succeededFuture(geoInfo);
    }
}
