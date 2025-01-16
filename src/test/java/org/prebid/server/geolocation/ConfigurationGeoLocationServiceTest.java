package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.geolocation.model.GeoInfoConfiguration;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ConfigurationGeoLocationServiceTest {

    @Mock
    private Timeout timeout;

    @Test
    public void lookupShouldReturnSucceededFuture() {
        // given
        final List<GeoInfoConfiguration> configs = singletonList(GeoInfoConfiguration.of(
                "192.",
                GeoInfo.builder()
                        .vendor(StringUtils.EMPTY)
                        .build()));

        final GeoLocationService target = new ConfigurationGeoLocationService(configs);

        // when
        final Future<GeoInfo> geoInfoFuture = target.lookup("192.168.0.1", timeout);

        // then
        assertThat(geoInfoFuture.succeeded()).isTrue();
        assertThat(geoInfoFuture.result()).isEqualTo(GeoInfo.builder()
                .vendor(ConfigurationGeoLocationService.VENDOR)
                .build());
    }

    @Test
    public void lookupShouldReturnFailedFuture() {
        // given
        final List<GeoInfoConfiguration> configs = emptyList();
        final GeoLocationService target = new ConfigurationGeoLocationService(configs);

        // when
        final Future<GeoInfo> geoInfoFuture = target.lookup("192.168.0.1", timeout);

        // then
        assertThat(geoInfoFuture.failed()).isTrue();
        assertThat(geoInfoFuture.cause()).satisfies(e -> assertThat(e.getMessage())
                .isEqualTo(ConfigurationGeoLocationService.class.getSimpleName() + ": Geo location lookup failed."));
    }
}
