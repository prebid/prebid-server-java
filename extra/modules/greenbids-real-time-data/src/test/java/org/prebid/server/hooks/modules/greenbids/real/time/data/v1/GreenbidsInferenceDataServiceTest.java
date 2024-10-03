package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHookTest.givenBanner;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHookTest.givenDevice;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHookTest.givenExtRequest;

public class GreenbidsInferenceDataServiceTest {

    @Mock
    private DatabaseReader dbReader;

    @Mock
    private Country country;

    private JacksonMapper jacksonMapper;

    private TestBidRequestProvider testBidRequestProvider;

    private GreenbidsInferenceDataService target;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        final ObjectMapper mapper = new ObjectMapper();
        jacksonMapper = new JacksonMapper(mapper);
        testBidRequestProvider = new TestBidRequestProvider(jacksonMapper);
        target = new GreenbidsInferenceDataService(dbReader, jacksonMapper.mapper());
    }

    @Test
    public void shouldReturnValidThrottlingMessages() throws IOException, GeoIp2Exception {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(testBidRequestProvider.givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final BidRequest bidRequest = testBidRequestProvider
                .givenBidRequest(request -> request, List.of(imp), device, null);

        final CountryResponse countryResponse = mock(CountryResponse.class);

        when(dbReader.country(any(InetAddress.class))).thenReturn(countryResponse);
        when(countryResponse.getCountry()).thenReturn(country);
        when(country.getName()).thenReturn("US");

        // when
        final List<ThrottlingMessage> throttlingMessages = target.extractThrottlingMessagesFromBidRequest(bidRequest);

        // then
        assertThat(throttlingMessages).isNotEmpty();
        assertThat(throttlingMessages.getFirst().getBidder()).isEqualTo("rubicon");
        assertThat(throttlingMessages.getFirst().getCountry()).isEqualTo("US");
    }

    @Test
    public void shouldHandleMissingIp() {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(testBidRequestProvider.givenImpExt())
                .banner(banner)
                .build();
        final Device device = givenDeviceWithoutIp(deviceBuilder -> deviceBuilder);
        final BidRequest bidRequest = testBidRequestProvider
                .givenBidRequest(request -> request, List.of(imp), device, null);

        // when
        final List<ThrottlingMessage> throttlingMessages = target.extractThrottlingMessagesFromBidRequest(bidRequest);

        // then
        assertThat(throttlingMessages).isNotEmpty();
        assertThat(throttlingMessages.getFirst().getCountry()).isEqualTo("");

    }

    @Test
    public void shouldThrowPreBidExceptionOnGeoIpFailure() throws IOException, GeoIp2Exception {
        // given
        final Banner banner = givenBanner();
        final Imp imp = Imp.builder()
                .id("adunitcodevalue")
                .ext(testBidRequestProvider.givenImpExt())
                .banner(banner)
                .build();
        final Double explorationRate = 0.0001;
        final Device device = givenDevice(deviceBuilder -> deviceBuilder);
        final ExtRequest extRequest = givenExtRequest(explorationRate);
        final BidRequest bidRequest = testBidRequestProvider.givenBidRequest(
                request -> request, List.of(imp), device, extRequest);

        when(dbReader.country(any(InetAddress.class))).thenThrow(new GeoIp2Exception("GeoIP failure"));

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
