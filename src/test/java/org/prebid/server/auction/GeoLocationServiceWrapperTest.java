package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.auction.model.IpAddress.IP;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.requestfactory.Ortb2ImplicitParametersResolver;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountSettings;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class GeoLocationServiceWrapperTest extends VertxTest {

    private static final Timeout TIMEOUT = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()))
            .create(1000L);

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private Ortb2ImplicitParametersResolver resolver;
    @Mock
    private Metrics metrics;

    private GeoLocationServiceWrapper target;

    @Before
    public void before() {
        target = new GeoLocationServiceWrapper(geoLocationService, resolver, metrics);
    }

    @Test
    public void doLookupShouldFailWhenGeoLocationServiceIsNotConfigured() {
        // given
        target = new GeoLocationServiceWrapper(null, resolver, metrics);
        // when
        final Future<GeoInfo> result = target.doLookup("ip", null, TIMEOUT);

        // then
        assertThat(result).isFailed().withFailMessage("Geolocation lookup is skipped");
        verifyNoInteractions(metrics);
    }

    @Test
    public void doLookupShouldFailWhenRequestCountryIsProvided() {
        // when
        final Future<GeoInfo> result = target.doLookup("ip", "country", TIMEOUT);

        // then
        assertThat(result).isFailed().withFailMessage("Geolocation lookup is skipped");
        verifyNoInteractions(geoLocationService, metrics);
    }

    @Test
    public void doLookupShouldFailWhenRequestIpIsNotProvided() {
        // when
        final Future<GeoInfo> result = target.doLookup(null, null, TIMEOUT);

        // then
        assertThat(result).isFailed().withFailMessage("Geolocation lookup is skipped");
        verifyNoInteractions(geoLocationService, metrics);
    }

    @Test
    public void doLookupShouldFailAndUpdateMetricWhenGeoLookupFails() {
        // given
        given(geoLocationService.lookup("ip", TIMEOUT)).willReturn(Future.failedFuture("Bad IP"));

        // when
        final Future<GeoInfo> result = target.doLookup("ip", null, TIMEOUT);

        // then
        assertThat(result).isFailed().withFailMessage("Bad IP");
        verify(metrics).updateGeoLocationMetric(false);
    }

    @Test
    public void doLookupShouldReturnGeoInfoAndUpdateMetricWhenGeoLookupSucceeds() {
        // given
        final GeoInfo givenGeoInfo = GeoInfo.builder().vendor("vendor").build();
        given(geoLocationService.lookup("ip", TIMEOUT)).willReturn(Future.succeededFuture(givenGeoInfo));

        // when
        final Future<GeoInfo> result = target.doLookup("ip", null, TIMEOUT);

        // then
        assertThat(result).succeededWith(givenGeoInfo);
        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void lookupShouldReturnNothingWhenLookupIsEnabledInAccountAndGeoLocationServiceIsNotConfigured() {
        // given
        target = new GeoLocationServiceWrapper(null, resolver, metrics);

        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip("ip").build()).build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).isSucceeded().unwrap().isNull();
        verifyNoInteractions(metrics);
    }

    @Test
    public void lookupShouldReturnNothingWhenLookupIsEnabledInAccountAndRequestCountryIsProvided() {
        // given
        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder()
                        .device(Device.builder().ip("ip").geo(Geo.builder().country("UKR").build()).build())
                        .build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).isSucceeded().unwrap().isNull();
        verifyNoInteractions(geoLocationService, metrics);
    }

    @Test
    public void lookupShouldReturnNothingWhenLookupIsEnabledInAccountAndRequestIpIsNotProvided() {
        // given
        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip(null).build()).build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).isSucceeded().unwrap().isNull();
        verifyNoInteractions(geoLocationService, metrics);
    }

    @Test
    public void lookupShouldReturnNothingWhenLookupIsEnabledInAccountAndGeoLookupFails() {
        // given
        given(geoLocationService.lookup("ip", TIMEOUT)).willReturn(Future.failedFuture("Bad IP"));

        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip("ip").build()).build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).isSucceeded().unwrap().isNull();
        verify(metrics).updateGeoLocationMetric(false);
    }

    @Test
    public void lookupShouldReturnGeoInfoAndUpdateMetricWhenGeoLookupByDeviceSucceeds() {
        // given
        final GeoInfo givenGeoInfo = GeoInfo.builder().vendor("vendor").build();
        given(geoLocationService.lookup("ip", TIMEOUT)).willReturn(Future.succeededFuture(givenGeoInfo));

        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip("ip").build()).build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).succeededWith(givenGeoInfo);
        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void lookupShouldReturnGeoInfoAndUpdateMetricWhenGeoLookupByHeaderSucceeds() {
        // given
        final GeoInfo givenGeoInfo = GeoInfo.builder().vendor("vendor").build();
        given(geoLocationService.lookup("ip", TIMEOUT)).willReturn(Future.succeededFuture(givenGeoInfo));
        given(resolver.findIpFromRequest(any(HttpRequestContext.class))).willReturn(IpAddress.of("ip", IP.v4));
        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().build()).build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).succeededWith(givenGeoInfo);
        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void lookupShouldPreferGeoLookupFromDeviceIpWhenIpPresentInRequestAndHeader() {
        // given
        final GeoInfo givenGeoInfo = GeoInfo.builder().vendor("vendor").build();
        given(geoLocationService.lookup("deviceIp", TIMEOUT)).willReturn(Future.succeededFuture(givenGeoInfo));
        given(resolver.findIpFromRequest(any(HttpRequestContext.class))).willReturn(IpAddress.of("headerIp", IP.v4));
        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip("deviceIp").build()).build())
                .httpRequest(HttpRequestContext.builder().build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(true)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).succeededWith(givenGeoInfo);
        verify(metrics).updateGeoLocationMetric(true);
    }

    @Test
    public void lookupShouldReturnNothingWhenGeoLookupIsDisabledInAccount() {
        // given
        final AuctionContext givenContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().device(Device.builder().ip("ip").build()).build())
                .timeoutContext(TimeoutContext.of(100L, TIMEOUT, 2))
                .account(Account.builder().settings(AccountSettings.of(false)).build())
                .build();

        // when
        final Future<GeoInfo> result = target.lookup(givenContext);

        // then
        assertThat(result).isSucceeded().unwrap().isNull();
        verifyNoInteractions(geoLocationService, metrics);
    }

}
