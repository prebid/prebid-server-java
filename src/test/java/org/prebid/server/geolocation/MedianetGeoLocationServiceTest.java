package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.geolocation.model.medianet.GeoInfoMapper;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class MedianetGeoLocationServiceTest extends VertxTest {

    private static final String TEST_IP = "23.235.60.92";

    private static final String GEO_SERVICE_ENDPOINT = "http://test.com";

    private static final String GEO_SERVICE_ENDPOINT_WITH_IP = GEO_SERVICE_ENDPOINT + TEST_IP;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private MedianetGeoService mediaNetGeoService;

    Timeout defaultTimeout;

    @Before
    public void setUp() {
        defaultTimeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(5000);
        GeoInfoMapper geoInfoMapper = Mappers.getMapper(GeoInfoMapper.class);
        mediaNetGeoService = new MedianetGeoService(
                httpClient,
                jacksonMapper,
                geoInfoMapper,
                GEO_SERVICE_ENDPOINT,
                5000);
    }

    @Test
    public void lookupShouldSucceed() {
        // given
        HttpClientResponse httpClientResponse = HttpClientResponse.of(
                200,
                null,
                "{\"city\": \"raleigh\", \"stateCode\": \"NC\", \"cc\": \"US\"}");
        given(httpClient.get(eq(GEO_SERVICE_ENDPOINT_WITH_IP), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        // when
        final Future<GeoInfo> future = mediaNetGeoService.lookup(TEST_IP, defaultTimeout);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .isEqualTo(GeoInfo.builder()
                        .vendor("netacuity")
                        .country("US")
                        .region("NC")
                        .city("raleigh")
                        .build());
    }

    @Test
    public void lookupShouldFailInCaseOfTimeout() {
        // given
        HttpClientResponse httpClientResponse = HttpClientResponse.of(
                200,
                null,
                "{\"city\": \"raleigh\", \"stateCode\": \"NC\", \"cc\": \"US\"}");
        given(httpClient.get(eq(GEO_SERVICE_ENDPOINT_WITH_IP), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        // when
        Timeout timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(1);
        timeout = timeout.minus(1);
        final Future<GeoInfo> future = mediaNetGeoService.lookup(TEST_IP, timeout);

        // then
        assertThat(future.succeeded()).isFalse();
        assertThat(future.cause()).hasMessage("Timeout has been exceeded while executing geo service");
    }

    @Test
    public void lookupShouldFailInCaseOfInvalidStatusCode() {
        // given
        HttpClientResponse httpClientResponse = HttpClientResponse.of(
                500,
                null,
                null);
        given(httpClient.get(eq(GEO_SERVICE_ENDPOINT_WITH_IP), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        // when
        Timeout timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(1);
        final Future<GeoInfo> future = mediaNetGeoService.lookup(TEST_IP, defaultTimeout);

        // then
        assertThat(future.succeeded()).isFalse();
        assertThat(future.cause()).hasMessage("Failed to fetch IP info, status code = 500");
    }

    @Test
    public void lookupShouldFailInCaseOfInvalidGeoInfoBody() {
        // given
        HttpClientResponse httpClientResponse = HttpClientResponse.of(
                200,
                null,
                "abcd");
        given(httpClient.get(eq(GEO_SERVICE_ENDPOINT_WITH_IP), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        // when
        final Future<GeoInfo> future = mediaNetGeoService.lookup(TEST_IP, defaultTimeout);

        // then
        assertThat(future.succeeded()).isFalse();
        assertThat(future.cause()).hasMessage("Unable to parse geo info data");
    }
}
