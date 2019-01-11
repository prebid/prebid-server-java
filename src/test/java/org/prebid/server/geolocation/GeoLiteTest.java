package org.prebid.server.geolocation;

import io.vertx.core.Future;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.geolocation.model.GeoInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class GeoLiteTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void creationShouldThrowExceptionIfDatabaseArchiveNotFound() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> GeoLite.create("no_file"))
                .withMessage("No database file found with a file name: no_file");
    }

    @Test
    public void creationShouldThrowExceptionIfFileIsNotAnArchive() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> GeoLite.create("application.yaml"))
                .withMessage("IO Exception occurred while trying to read an archive/db file: Not in GZIP format");
    }

    @Test
    public void shouldReturnCountryISO() {
        //given
        final GeoLite geoLite = Mockito.mock(GeoLite.class);
        given(geoLite.lookup(anyString(), any())).willReturn(Future.succeededFuture(GeoInfo.of("ua")));

        //when
        final Future<GeoInfo> future = geoLite.lookup("Any IP", null);

        //then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result())
                .extracting(GeoInfo::getCountry)
                .containsOnly("ua");
    }
}
