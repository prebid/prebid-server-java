package org.prebid.server.geolocation;

import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.geolocation.model.medianet.GeoInfoMapper;
import org.prebid.server.geolocation.model.medianet.IPInfo;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.concurrent.TimeoutException;

@AllArgsConstructor
public class MedianetGeoService implements GeoLocationService {

    private final HttpClient httpClient;

    private final JacksonMapper jacksonMapper;

    private final GeoInfoMapper geoInfoMapper;

    private final String endpoint;

    private final long responseTimeout;

    @Override
    public Future<GeoInfo> lookup(String ip, Timeout timeout) {
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return Future.failedFuture(
                    new TimeoutException("Timeout has been exceeded while executing geo service"));
        }
        return httpClient.get(endpoint, responseTimeout)
                .map(this::parseResponse);
    }

    private GeoInfo parseResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("Failed to fetch IP info, status code = %d", statusCode));
        }
        final String body = response.getBody();
        final IPInfo ipInfo;
        try {
            ipInfo = jacksonMapper.decodeValue(body, IPInfo.class);
        } catch (DecodeException e) {
            throw new PreBidException("Unable to parse geo info data", e);
        }
        return geoInfoMapper.from(ipInfo);
    }
}
