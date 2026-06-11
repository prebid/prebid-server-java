package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.scientiamobile.wurfl.core.WURFLEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.AuctionRequestHeadersContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class WURFLDeviceDetectionRawAuctionRequestHookTest {

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

    @Mock(strictness = Mock.Strictness.LENIENT)
    private com.scientiamobile.wurfl.core.Device wurflDevice;

    private JacksonMapper mapper = new JacksonMapper(ObjectMapperProvider.mapper());

    private WURFLDeviceDetectionRawAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        auctionContext = AuctionContext.builder().account(account).build();

        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);
    }

    @Test
    public void codeShouldReturnCorrectHookCode() {
        // when
        final String result = target.code();

        // then
        assertThat(result).isEqualTo("wurfl-devicedetection-raw-auction-request");
    }

    @Test
    public void callShouldReturnNoActionWhenDeviceIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        given(payload.bidRequest()).willReturn(bidRequest);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnNoActionWhenDeviceHasWurflProperty() {
        // given
        final String ua = "Mozilla/5.0 (testPhone; CPU testPhone OS 1_0_2) Version/17.4.1 Mobile/12E GrandTest/604.1";
        final ExtDevicePrebid extDevicePrebid = ExtDevicePrebid.of(ExtDeviceInt.of(80, 80));
        final ExtDevice extDevice = ExtDevice.of(0, null, extDevicePrebid);
        final ObjectNode wurfl = mapper.mapper().createObjectNode();
        wurfl.put("wurfl_id", "test_phone_ver1");
        extDevice.addProperty("wurfl", wurfl);
        final Device device = Device.builder()
                .ua(ua)
                .ext(extDevice)
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);
        given(configProperties.getAllowedPublisherIds()).willReturn(Collections.emptySet());
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnNoActionWhenDeviceHasDeviceTypeAndHwv() {
        // given
        final String ua = "Mozilla/5.0 (testPhone; CPU testPhone OS 1_0_2) Version/17.4.1 Mobile/12E GrandTest/604.1";
        final Device device = Device.builder()
                .ua(ua)
                .hwv("test_phone_ver1")
                .devicetype(1)
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);
        given(configProperties.getAllowedPublisherIds()).willReturn(Collections.emptySet());
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        given(wurflDevice.getId()).willReturn("test_phone_ver1");
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void callShouldReturnActionUpdateWhenDeviceHasJustDeviceType() {
        // given
        final String ua = "Mozilla/5.0 (testPhone; CPU testPhone OS 1_0_2) Version/17.4.1 Mobile/12E GrandTest/604.1";
        given(configProperties.getAllowedPublisherIds()).willReturn(Collections.emptySet());
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);
        final Device device = Device.builder()
                .ua(ua)
                .devicetype(1)
                .build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        given(context.moduleContext()).willReturn(headersContext);
        given(wurflEngine.getDeviceForRequest(any(Map.class))).willReturn(wurflDevice);
        given(wurflDevice.getId()).willReturn("test_phone_ver1");

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    public void callShouldUpdateDeviceWhenWurflDeviceIsDetected() throws Exception {
        // given
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7_2) Version/17.4.1 Mobile/15E148 Safari/604.1";
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);

        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        given(context.moduleContext()).willReturn(headersContext);
        given(wurflEngine.getDeviceForRequest(any(Map.class))).willReturn(wurflDevice);
        given(wurflDevice.getId()).willReturn("apple_iphone_ver1");
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    public void shouldEnrichDeviceWhenAllowedPublisherIdsIsEmpty() throws Exception {
        // given
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7_2) Version/17.4.1 Mobile/15E148 Safari/604.1";
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);

        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        given(context.moduleContext()).willReturn(headersContext);
        given(wurflEngine.getDeviceForRequest(any(Map.class))).willReturn(wurflDevice);
        given(wurflDevice.getId()).willReturn("apple_iphone_ver1");
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(configProperties.getAllowedPublisherIds()).willReturn(Collections.emptySet());

        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    public void shouldEnrichDeviceWhenAccountIsAllowed() throws Exception {
        // given
        final String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_7_2) Version/17.4.1 Mobile/15E148 Safari/604.1";
        final Device device = Device.builder().ua(ua).build();
        final BidRequest bidRequest = BidRequest.builder().device(device).build();
        given(payload.bidRequest()).willReturn(bidRequest);

        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add("User-Agent", ua)
                .build();
        final AuctionRequestHeadersContext headersContext = AuctionRequestHeadersContext.from(headers);

        given(context.moduleContext()).willReturn(headersContext);
        given(wurflEngine.getDeviceForRequest(any(Map.class))).willReturn(wurflDevice);
        given(wurflDevice.getId()).willReturn("apple_iphone_ver1");
        given(wurflDevice.getCapability("brand_name")).willReturn("Apple");
        given(wurflDevice.getCapability("model_name")).willReturn("iPhone");
        given(account.getId()).willReturn("allowed-publisher");
        given(configProperties.getAllowedPublisherIds()).willReturn(Set.of("allowed-publisher",
                "another-allowed-publisher"));
        given(context.auctionContext()).willReturn(auctionContext);

        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }

    @Test
    public void shouldNotEnrichDeviceWhenPublisherIdIsNotAllowed() {
        // given
        given(context.auctionContext()).willReturn(auctionContext);
        given(account.getId()).willReturn("unknown-publisher");
        given(configProperties.getAllowedPublisherIds()).willReturn(Set.of("allowed-publisher"));
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void shouldNotEnrichDeviceWhenPublisherIdIsEmpty() {
        // given
        given(context.auctionContext()).willReturn(auctionContext);
        given(account.getId()).willReturn("");
        given(configProperties.getAllowedPublisherIds()).willReturn(Set.of("allowed-publisher"));
        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);
        target = new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties, mapper);

        // when
        final InvocationResult<AuctionRequestPayload> result = target.call(payload, context).result();

        // then
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }
}
