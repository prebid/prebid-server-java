package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.scientiamobile.wurfl.core.WURFLEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.mock.WURFLDeviceMock;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.AuctionRequestHeadersContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WURFLDeviceDetectionRawAuctionRequestHookTest {

    @Mock
    private WURFLEngine wurflEngine;

    @Mock
    private WURFLDeviceDetectionConfigProperties configProperties;

    @Mock
    private AuctionRequestPayload payload;

    @Mock
    private AuctionInvocationContext context;

    private AuctionContext auctionContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Account account;

    private JacksonMapper mapper = new JacksonMapper(ObjectMapperProvider.mapper());

    private WURFLDeviceDetectionRawAuctionRequestHook target;

    @BeforeEach
    void setUp() {
        auctionContext = AuctionContext.builder().account(account).build();

        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);
    }

    @Test
    void codeShouldReturnCorrectHookCode() {
        // when
        final String result = target.code();

        // then
        assertThat(result).isEqualTo("wurfl-devicedetection-raw-auction-request");
    }

    @Test
    void callShouldReturnNoActionWhenDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        when(payload.bidRequest()).thenReturn(bidRequest);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    void callShouldUpdateDeviceWhenWurflDeviceIsDetected() {
        // given
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7_2) Version/17.4.1 Mobile/15E148 Safari/604.1";
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        when(payload.bidRequest()).thenReturn(bidRequest);

        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        // when
        when(context.moduleContext()).thenReturn(headersContext);
        final var wurflDevice = WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone();
        when(wurflEngine.getDeviceForRequest(any(Map.class))).thenReturn(wurflDevice);

        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    void shouldEnrichDeviceWhenAllowedPublisherIdsIsEmpty() {
        // given
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7_2) Version/17.4.1 Mobile/15E148 Safari/604.1";
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        when(payload.bidRequest()).thenReturn(bidRequest);

        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        // when
        when(context.moduleContext()).thenReturn(headersContext);
        final var wurflDevice = WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone();
        when(wurflEngine.getDeviceForRequest(any(Map.class))).thenReturn(wurflDevice);
        when(configProperties.getAllowedPublisherIds()).thenReturn(Collections.emptySet());
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    void shouldEnrichDeviceWhenAccountIsAllowed() {
        // given
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7_2) Version/17.4.1 Mobile/15E148 Safari/604.1";
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        when(payload.bidRequest()).thenReturn(bidRequest);

        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        // when
        when(context.moduleContext()).thenReturn(headersContext);
        final var wurflDevice = WURFLDeviceMock.WURFLDeviceMockFactory.mockIPhone();
        when(wurflEngine.getDeviceForRequest(any(Map.class))).thenReturn(wurflDevice);
        when(account.getId()).thenReturn("allowed-publisher");
        when(configProperties.getAllowedPublisherIds()).thenReturn(Set.of("allowed-publisher",
                "another-allowed-publisher"));
        when(context.auctionContext()).thenReturn(auctionContext);
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    void shouldNotEnrichDeviceWhenPublisherIdIsNotAllowed() {
        // given
        when(context.auctionContext()).thenReturn(auctionContext);
        when(account.getId()).thenReturn("unknown-publisher");
        when(configProperties.getAllowedPublisherIds()).thenReturn(Set.of("allowed-publisher"));
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    void shouldNotEnrichDeviceWhenPublisherIdIsEmpty() {
        // given
        when(context.auctionContext()).thenReturn(auctionContext);
        when(account.getId()).thenReturn("");
        when(configProperties.getAllowedPublisherIds()).thenReturn(Set.of("allowed-publisher"));
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }
}
