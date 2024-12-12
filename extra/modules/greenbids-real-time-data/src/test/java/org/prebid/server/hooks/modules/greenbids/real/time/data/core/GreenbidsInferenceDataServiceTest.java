package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.hooks.modules.greenbids.real.time.data.config.DatabaseReaderFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;
import org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider;

import java.io.IOException;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenDevice;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenImpExt;

@ExtendWith(MockitoExtension.class)
public class GreenbidsInferenceDataServiceTest {

    @Mock(strictness = LENIENT)
    private DatabaseReaderFactory databaseReaderFactory;

    @Mock
    private DatabaseReader databaseReader;

    @Mock
    private Country country;

    @Mock
    private CountryCodeMapper countryCodeMapper;

    private GreenbidsInferenceDataService target;

    @BeforeEach
    public void setUp() {
        when(databaseReaderFactory.getDatabaseReader()).thenReturn(databaseReader);
        target = new GreenbidsInferenceDataService(
                databaseReaderFactory, TestBidRequestProvider.MAPPER, countryCodeMapper);
    }

    @Test
    public void extractThrottlingMessagesFromBidRequestShouldReturnValidThrottlingMessagesWhenGeoIsNull()
            throws IOException, GeoIp2Exception {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(identity());
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device);

        final CountryResponse countryResponse = mock(CountryResponse.class);

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer expectedHourBucket = timestamp.getHour();
        final Integer expectedMinuteQuadrant = (timestamp.getMinute() / 15) + 1;

        when(databaseReader.country(any(InetAddress.class))).thenReturn(countryResponse);
        when(countryResponse.getCountry()).thenReturn(country);
        when(country.getName()).thenReturn("United States");

        // when
        final List<ThrottlingMessage> throttlingMessages = target.extractThrottlingMessagesFromBidRequest(bidRequest);

        // then
        assertThat(throttlingMessages).isNotEmpty();
        assertThat(throttlingMessages)
                .extracting(ThrottlingMessage::getBidder)
                .containsExactly("rubicon", "appnexus", "pubmatic");

        throttlingMessages.forEach(message -> {
            assertThat(message.getAdUnitCode()).isEqualTo("adunitcodevalue");
            assertThat(message.getCountry()).isEqualTo("United States");
            assertThat(message.getHostname()).isEqualTo("www.leparisien.fr");
            assertThat(message.getDevice()).isEqualTo("PC");
            assertThat(message.getHourBucket()).isEqualTo(String.valueOf(expectedHourBucket));
            assertThat(message.getMinuteQuadrant()).isEqualTo(String.valueOf(expectedMinuteQuadrant));
        });
    }

    @Test
    public void extractThrottlingMessagesFromBidRequestShouldReturnValidThrottlingMessagesWhenGeoDefined() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(identity(), "FRA");
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device, null);

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer expectedHourBucket = timestamp.getHour();
        final Integer expectedMinuteQuadrant = (timestamp.getMinute() / 15) + 1;

        when(countryCodeMapper.mapToAlpha2("FRA")).thenReturn("FR");

        // when
        final List<ThrottlingMessage> throttlingMessages = target.extractThrottlingMessagesFromBidRequest(bidRequest);

        // then
        assertThat(throttlingMessages).isNotEmpty();
        assertThat(throttlingMessages)
                .extracting(ThrottlingMessage::getBidder)
                .containsExactly("rubicon", "appnexus", "pubmatic");

        throttlingMessages.forEach(message -> {
            assertThat(message.getAdUnitCode()).isEqualTo("adunitcodevalue");
            assertThat(message.getCountry()).isEqualTo("France");
            assertThat(message.getHostname()).isEqualTo("www.leparisien.fr");
            assertThat(message.getDevice()).isEqualTo("PC");
            assertThat(message.getHourBucket()).isEqualTo(String.valueOf(expectedHourBucket));
            assertThat(message.getMinuteQuadrant()).isEqualTo(String.valueOf(expectedMinuteQuadrant));
        });
    }

    @Test
    public void extractThrottlingMessagesFromBidRequestShouldHandleMissingIp() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDeviceWithoutIp(identity());
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device);

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer expectedHourBucket = timestamp.getHour();
        final Integer expectedMinuteQuadrant = (timestamp.getMinute() / 15) + 1;

        // when
        final List<ThrottlingMessage> throttlingMessages = target.extractThrottlingMessagesFromBidRequest(bidRequest);

        // then
        assertThat(throttlingMessages).isNotEmpty();
        assertThat(throttlingMessages)
                .extracting(ThrottlingMessage::getBidder)
                .containsExactly("rubicon", "appnexus", "pubmatic");

        throttlingMessages.forEach(message -> {
            assertThat(message.getAdUnitCode()).isEqualTo("adunitcodevalue");
            assertThat(message.getCountry()).isEqualTo(StringUtils.EMPTY);
            assertThat(message.getHostname()).isEqualTo("www.leparisien.fr");
            assertThat(message.getDevice()).isEqualTo("PC");
            assertThat(message.getHourBucket()).isEqualTo(String.valueOf(expectedHourBucket));
            assertThat(message.getMinuteQuadrant()).isEqualTo(String.valueOf(expectedMinuteQuadrant));
        });
    }

    @Test
    public void extractThrottlingMessagesFromBidRequestShouldThrowPreBidExceptionWhenGeoIpFails()
            throws IOException, GeoIp2Exception {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(identity());
        final BidRequest bidRequest = givenBidRequest(request -> request, List.of(imp), device);

        when(databaseReader.country(any(InetAddress.class))).thenThrow(new GeoIp2Exception("GeoIP failure"));

        // when & then
        assertThatThrownBy(() -> target.extractThrottlingMessagesFromBidRequest(bidRequest))
                .isInstanceOf(PreBidException.class)
                .hasMessageContaining("Failed to fetch country from geoLite DB");
    }

    private Device givenDeviceWithoutIp(UnaryOperator<Device.DeviceBuilder> deviceCustomizer) {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        return deviceCustomizer.apply(Device.builder().ua(userAgent)).build();
    }
}
