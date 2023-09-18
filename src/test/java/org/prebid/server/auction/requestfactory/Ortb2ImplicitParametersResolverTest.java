package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.identity.IdGenerator;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Ortb2ImplicitParametersResolverTest extends VertxTest {

    private static final String ENDPOINT = Endpoint.openrtb2_amp.value();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private TimeoutResolver timeoutResolver;
    @Mock
    private IpAddressHelper ipAddressHelper;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private JsonMerger jsonMerger;

    private Ortb2ImplicitParametersResolver target;

    private BidRequest defaultBidRequest;
    private HttpRequestContext httpRequest;

    public Ortb2ImplicitParametersResolver target(boolean shouldCacheOnlyWinningsBids) {
        return new Ortb2ImplicitParametersResolver(
                shouldCacheOnlyWinningsBids,
                true,
                "USD",
                singletonList("bad_app"),
                "https://external.url/",
                0,
                "datacenter-region",
                paramsExtractor,
                timeoutResolver,
                ipAddressHelper,
                idGenerator,
                jsonMerger,
                jacksonMapper);
    }

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();

        httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.empty())
                .build();

        given(idGenerator.generateId()).willReturn(null);
        given(timeoutResolver.limitToMax(any())).willReturn(2000L);

        target = target(false);
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsEmptyForIpv4() {
        // given
        givenImplicitParams("http://example.com", "example.com", "192.168.244.1", IpAddress.IP.v4, "UnitTest");

        // when
        final BidRequest result = target.resolve(defaultBidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder()
                .page("http://example.com")
                .domain("example.com")
                .publisher(Publisher.builder().domain("example.com").build())
                .ext(ExtSite.of(0, null))
                .build());
        assertThat(result.getDevice())
                .isEqualTo(Device.builder().ip("192.168.244.1").ua("UnitTest").build());
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsInvalidForIpv4() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ip("127.0.0.1").build())
                .build();

        given(ipAddressHelper.toIpAddress(eq("127.0.0.1"))).willReturn(null);

        givenImplicitParams("http://example.com", "example.com", "192.168.244.1", IpAddress.IP.v4, "UnitTest");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder()
                .page("http://example.com")
                .domain("example.com")
                .publisher(Publisher.builder().domain("example.com").build())
                .ext(ExtSite.of(0, null))
                .build());
        assertThat(result.getDevice())
                .isEqualTo(Device.builder().ip("192.168.244.1").ua("UnitTest").build());
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsEmptyForIpv6() {
        // given
        givenImplicitParams(
                "http://example.com",
                "example.com",
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                IpAddress.IP.v6,
                "UnitTest");

        // when
        final BidRequest result = target.resolve(defaultBidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder()
                .page("http://example.com")
                .domain("example.com")
                .publisher(Publisher.builder().domain("example.com").build())
                .ext(ExtSite.of(0, null))
                .build());
        assertThat(result.getDevice())
                .isEqualTo(Device.builder().ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334").ua("UnitTest").build());
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsInvalidForIpv6() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ipv6("::1").build())
                .build();

        given(ipAddressHelper.toIpAddress(eq("::1"))).willReturn(null);

        givenImplicitParams(
                "http://example.com",
                "example.com",
                "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
                IpAddress.IP.v6,
                "UnitTest");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder()
                .page("http://example.com")
                .domain("example.com")
                .publisher(Publisher.builder().domain("example.com").build())
                .ext(ExtSite.of(0, null))
                .build());
        assertThat(result.getDevice())
                .isEqualTo(Device.builder().ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334").ua("UnitTest").build());
    }

    @Test
    public void shouldSetAnonymizedIpv6FromField() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334").build())
                .build();

        given(ipAddressHelper.toIpAddress(eq("2001:0db8:85a3:0000:0000:8a2e:0370:7334")))
                .willReturn(IpAddress.of("2001:0db8:85a3:0000::", IpAddress.IP.v6));

        givenImplicitParams(
                "http://example.com",
                "example.com",
                "1111:2222:3333:4444:5555:6666:7777:8888",
                IpAddress.IP.v6,
                "UnitTest");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder()
                .page("http://example.com")
                .domain("example.com")
                .publisher(Publisher.builder().domain("example.com").build())
                .ext(ExtSite.of(0, null))
                .build());
        assertThat(result.getDevice())
                .isEqualTo(Device.builder().ipv6("2001:0db8:85a3:0000::").ua("UnitTest").build());
    }

    @Test
    public void shouldNotImplicitlyResolveIpIfIpv6IsPassed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334").build())
                .build();

        given(ipAddressHelper.toIpAddress(eq("2001:0db8:85a3:0000:0000:8a2e:0370:7334")))
                .willReturn(IpAddress.of("2001:0db8:85a3:0000::", IpAddress.IP.v6));

        // when
        target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        verify(paramsExtractor, never()).ipFrom(any(CaseInsensitiveMultiMap.class), any());
    }

    @Test
    public void shouldNotSetDeviceDntIfHeaderHasInvalidValue() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add("DNT", "invalid")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(defaultBidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getDnt()).isNull();
    }

    @Test
    public void shouldSetDeviceDntIfHeaderExists() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add("DNT", "1")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(defaultBidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getDnt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceDntIfHeaderExists() {
        // given
        final HttpRequestContext httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add("DNT", "0")
                        .build())
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().dnt(1).build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getDnt()).isZero();
    }

    @Test
    public void shouldNotSetDeviceLmtForIos14IfNoApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.0")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForNonIos() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("plan9")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForIosInvalidVersion() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("invalid-version")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForIosInvalidVersionMajor() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("invalid-major.0")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForIosInvalidVersionMinor() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.invalid-minor")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForIosMissingVersionMinor() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForIosLowerThan14() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("13.4")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14WithPatchVersion() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.0.patch-version")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor0() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.0")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor0AndEmptyIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.0")
                        .ifa("")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceLmtToOneForIos14Minor0AndEmptyIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(0)
                        .os("iOS")
                        .osv("14.0")
                        .ifa("")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor0AndZerosIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.0")
                        .ifa("00000000-0000-0000-0000-000000000000")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtZeroForIos14Minor0AndNonZerosIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.0")
                        .ifa("12345")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isZero();
    }

    @Test
    public void shouldSetDeviceLmtZeroForIos14Minor1AndNonZerosIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.1")
                        .ifa("12345")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isZero();
    }

    @Test
    public void shouldOverrideDeviceLmtToZeroForIos14Minor0AndNonZerosIfaWhenLmtAlreadySet() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(1)
                        .os("iOS")
                        .osv("14.0")
                        .ifa("12345")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isZero();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor1() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.1")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor1AndEmptyIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.1")
                        .ifa("")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceLmtToOneForIos14Minor1AndEmptyIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(0)
                        .os("iOS")
                        .osv("14.1")
                        .ifa("")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor1AndZerosIfa() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.1")
                        .ifa("00000000-0000-0000-0000-000000000000")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceLmtToZeroForIos14Minor1AndNonZerosIfaWhenLmtAlreadySet() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(1)
                        .os("iOS")
                        .osv("14.1")
                        .ifa("12345")
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isZero();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor2AndAtts0() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(0, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceLmtToOneForIos14Minor2AndAtts0() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(0)
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(0, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos14Minor2AndAtts1() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(1, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceLmtToOneForIos14Minor2AndAtts1() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(0)
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(1, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos15Minor0AndAtts0() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("15.0")
                        .ext(ExtDevice.of(0, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos15Minor0AndAtts1() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("15.0")
                        .ext(ExtDevice.of(1, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtOneForIos15Minor0AndAtts2() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("15.0")
                        .ext(ExtDevice.of(2, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldOverrideDeviceLmtToOneForIos14Minor2AndAtts2() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(0)
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(2, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isOne();
    }

    @Test
    public void shouldSetDeviceLmtZeroForIos14Minor2AndAtts3() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(3, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isZero();
    }

    @Test
    public void shouldOverrideDeviceLmtToZeroForIos14Minor2AndAtts3() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .lmt(1)
                        .os("iOS")
                        .osv("14.2")
                        .ext(ExtDevice.of(3, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isZero();
    }

    @Test
    public void shouldNotSetDeviceLmtForIos14Minor3AndAtts4() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.3")
                        .ext(ExtDevice.of(4, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldNotSetDeviceLmtForIos14Minor3AndAttsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .device(Device.builder()
                        .os("iOS")
                        .osv("14.3")
                        .ext(ExtDevice.of(null, null))
                        .build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getDevice().getLmt()).isNull();
    }

    @Test
    public void shouldUpdateImpsWithSecurityOneIfRequestIsSecuredAndImpSecurityNotDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().build())).build();
        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).flatExtracting(Imp::getSecure).containsOnly(1);
    }

    @Test
    public void shouldNotUpdateImpsWithSecurityOneIfRequestIsSecureAndImpSecurityIsZero() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().id("someImpId").secure(0).build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).isSameAs(imps);
    }

    @Test
    public void shouldUpdateImpsOnlyWithNotDefinedSecurityWithSecurityOneIfRequestIsSecure() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(Imp.builder().build(), Imp.builder().secure(0).build()))
                .build();
        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).extracting(Imp::getSecure).containsOnly(1, 0);
    }

    @Test
    public void shouldNotUpdateImpsWithSecurityOneIfRequestIsNotSecureAndImpSecurityIsNotDefined() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().id("someImpId").secure(1).build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).isSameAs(imps);
    }

    @Test
    public void shouldGenerateImpIdIfEmpty() {
        // given
        final List<Imp> imps = singletonList(Imp.builder().id(null).build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getId)
                .doesNotContainNull();
    }

    @Test
    public void shouldGenerateImpIdIfImpIdIsNotUnique() {
        // given
        final List<Imp> imps = List.of(
                Imp.builder().id("not_unique_id").build(),
                Imp.builder().id("not_unique_id").build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp().stream()
                .map(Imp::getId)
                .collect(Collectors.toSet()))
                .hasSize(2)
                .containsExactly("1", "2");
    }

    @Test
    public void shouldMoveBidderParametersToImpExtPrebidBidderAndMergeWithExisting() {
        // given
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .id("someImpId")
                        .ext(mapper.createObjectNode()
                                .<ObjectNode>set("bidder1", mapper.createObjectNode().put("param1", "value1"))
                                .<ObjectNode>set("bidder2", mapper.createObjectNode().put("param2", "value2"))
                                .<ObjectNode>set("context", mapper.createObjectNode().put("data", "datavalue"))
                                .<ObjectNode>set("all", mapper.createObjectNode().put("all-data", "all-value"))
                                .<ObjectNode>set("general", mapper.createObjectNode()
                                        .put("general-data", "general-value"))
                                .<ObjectNode>set("skadn", mapper.createObjectNode()
                                        .put("skadn-data", "skadn-value"))
                                .<ObjectNode>set("data", mapper.createObjectNode()
                                        .put("data-data", "data-value"))
                                .<ObjectNode>set("gpid", mapper.createObjectNode()
                                        .put("gpid-data", "gpid-value"))
                                .<ObjectNode>set("tid", mapper.createObjectNode()
                                        .put("tid-data", "tid-value"))
                                .set("prebid", mapper.createObjectNode()
                                        .<ObjectNode>set("bidder", mapper.createObjectNode()
                                                .set("bidder2", mapper.createObjectNode().put("param22", "value22")))
                                        .set("storedresult", mapper.createObjectNode().put("id", "storedreq1"))))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final Imp expectedImp = Imp.builder()
                .id("someImpId")
                .secure(1)
                .ext(mapper.createObjectNode()
                        .<ObjectNode>set("context", mapper.createObjectNode().put("data", "datavalue"))
                        .<ObjectNode>set("all", mapper.createObjectNode().put("all-data", "all-value"))
                        .<ObjectNode>set("general", mapper.createObjectNode()
                                .put("general-data", "general-value"))
                        .<ObjectNode>set("skadn", mapper.createObjectNode()
                                .put("skadn-data", "skadn-value"))
                        .<ObjectNode>set("data", mapper.createObjectNode()
                                .put("data-data", "data-value"))
                        .<ObjectNode>set("gpid", mapper.createObjectNode()
                                .put("gpid-data", "gpid-value"))
                        .<ObjectNode>set("tid", mapper.createObjectNode()
                                .put("tid-data", "tid-value"))
                        .set("prebid", mapper.createObjectNode()
                                .<ObjectNode>set("bidder", mapper.createObjectNode()
                                        .<ObjectNode>set(
                                                "bidder1", mapper.createObjectNode().put("param1", "value1"))
                                        .<ObjectNode>set(
                                                "bidder2", mapper.createObjectNode()
                                                        .put("param2", "value2")
                                                        .put("param22", "value22")))
                                .set("storedresult", mapper.createObjectNode().put("id", "storedreq1"))))
                .build();

        assertThat(result.getImp()).isEqualTo(singletonList(expectedImp));
    }

    @Test
    public void shouldPassExistingImpExtTidValue() {
        // given
        given(idGenerator.generateId()).willReturn("generatedID");
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .ext(mapper.createObjectNode().set("tid", new TextNode("tidValue")))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                Endpoint.openrtb2_auction.value(),
                false);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .extracting(JsonNode::asText)
                .containsExactly("tidValue");
    }

    @Test
    public void shouldReplaceExistingImpExtTidValueIfRequestHasAppliedStoreRequest() {
        // given
        given(idGenerator.generateId()).willReturn("generatedID");
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .ext(mapper.createObjectNode().set("tid", new TextNode("tidValue")))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                Endpoint.openrtb2_auction.value(),
                true);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .extracting(JsonNode::asText)
                .containsExactly("generatedID");
    }

    @Test
    public void shouldGenerateImpExtTidValueIfMissing() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .ext(mapper.createObjectNode().set("tid", MissingNode.getInstance()))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                ENDPOINT,
                false);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .extracting(JsonNode::asText)
                .containsExactly("generatedID");
    }

    @Test
    public void shouldReplaceUuidMacroForImpExtTidWithGeneratedValue() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .ext(mapper.createObjectNode().set("tid", new TextNode("prefix_{{UUID}}_suffix")))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .extracting(JsonNode::asText)
                .containsExactly("prefix_generatedID_suffix");
    }

    @Test
    public void shouldCreateImpExtTidIfMissing() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .ext(mapper.createObjectNode())
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                ENDPOINT,
                false);

        // then
        assertThat(result.getImp())
                .extracting(Imp::getExt)
                .extracting(ext -> ext.get("tid"))
                .extracting(JsonNode::asText)
                .containsExactly("generatedID");
    }

    @Test
    public void shouldPassExistingSourceTidValue() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .source(Source.builder().tid("tidValue").build())
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                ENDPOINT,
                false);

        // then
        assertThat(result.getSource())
                .extracting(Source::getTid)
                .isEqualTo("tidValue");
    }

    @Test
    public void shouldReplaceExistingSourceTidValueIfRequestHasAppliedStoredRequest() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final BidRequest bidRequest = BidRequest.builder()
                .source(Source.builder().tid("tidValue").build())
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                Endpoint.openrtb2_auction.value(),
                true);

        // then
        assertThat(result.getSource())
                .extracting(Source::getTid)
                .isEqualTo("generatedID");
    }

    @Test
    public void shouldGenerateSourceTidValueIfMissing() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final BidRequest bidRequest = BidRequest.builder()
                .source(Source.builder().tid(null).build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSource())
                .extracting(Source::getTid)
                .isEqualTo("generatedID");
    }

    @Test
    public void shouldReplaceUuidMacroForSourceTidWithGeneratedValue() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final BidRequest bidRequest = BidRequest.builder()
                .source(Source.builder().tid("prefix_{{UUID}}_suffix").build())
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest,
                httpRequest,
                ENDPOINT,
                false);

        // then
        assertThat(result.getSource())
                .extracting(Source::getTid)
                .isEqualTo("prefix_generatedID_suffix");
    }

    @Test
    public void shouldCreateSourceObjectWithGeneratedTidValue() {
        // given
        when(idGenerator.generateId()).thenReturn("generatedID");
        final BidRequest bidRequest = BidRequest.builder()
                .source(null)
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSource())
                .extracting(Source::getTid)
                .isEqualTo("generatedID");
    }

    @Test
    public void shouldMoveBidderParametersToImpExtPrebidBidderWhenImpExtPrebidAbsent() {
        // given
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .id("someImpId")
                        .ext(mapper.createObjectNode()
                                .<ObjectNode>set("bidder1", mapper.createObjectNode().put("param1", "value1"))
                                .set("bidder2", mapper.createObjectNode().put("param2", "value2")))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final Imp expectedImp = Imp.builder()
                .id("someImpId")
                .secure(1)
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode()
                                        .<ObjectNode>set(
                                                "bidder1", mapper.createObjectNode().put("param1", "value1"))
                                        .<ObjectNode>set(
                                                "bidder2", mapper.createObjectNode().put("param2", "value2")))))
                .build();
        assertThat(result.getImp()).isEqualTo(singletonList(expectedImp));
    }

    @Test
    public void shouldSetDealsOnlyIfNotSpecifiedAndPgDealsOnlyIsTrue() {
        final Imp imp = Imp.builder()
                .id("someImpId")
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .bidder(mapper.createObjectNode().putPOJO("someBidder", Map.of("pgdealsonly", true)))
                                .build(),
                        null)))
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final Imp expectedImp = Imp.builder()
                .id("someImpId")
                .secure(1)
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .bidder(mapper.createObjectNode().putPOJO(
                                        "someBidder",
                                        Map.of("pgdealsonly", true, "dealsonly", true)))
                                .build(),
                        null)))
                .build();
        assertThat(result.getImp()).isEqualTo(singletonList(expectedImp));
    }

    @Test
    public void shouldNotAffectDealsOnlyIfSpecifiedAndPgDealsOnlyIsTrue() {
        final Imp imp = Imp.builder()
                .id("someImpId")
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .bidder(mapper.createObjectNode().putPOJO(
                                        "someBidder",
                                        Map.of("pgdealsonly", true, "dealsonly", false)))
                                .build(),
                        null)))
                .build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final Imp expectedImp = Imp.builder()
                .id("someImpId")
                .secure(1)
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .bidder(mapper.createObjectNode().putPOJO(
                                        "someBidder",
                                        Map.of("pgdealsonly", true, "dealsonly", false)))
                                .build(),
                        null)))
                .build();
        assertThat(result.getImp()).isEqualTo(singletonList(expectedImp));
    }

    @Test
    public void shouldNotChangeImpExtWhenBidderParametersAreAtImpExtPrebidBidderOnly() {
        // given
        final List<Imp> imps = singletonList(
                Imp.builder()
                        .id("someImpId")
                        .secure(1)
                        .ext(mapper.createObjectNode()
                                .set("prebid", mapper.createObjectNode()
                                        .set("bidder", mapper.createObjectNode()
                                                .<ObjectNode>set(
                                                        "bidder1", mapper.createObjectNode().put("param1", "value1"))
                                                .<ObjectNode>set(
                                                        "bidder2", mapper.createObjectNode().put("param2", "value2")))))
                        .build());

        final BidRequest bidRequest = BidRequest.builder().imp(imps).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).isSameAs(imps);
    }

    @Test
    public void shouldNotSetFieldsFromHeadersIfRequestFieldsNotEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .page("http://test.com")
                        .domain("test.com")
                        .publisher(Publisher.builder().domain("test.com").build())
                        .ext(ExtSite.of(0, null))
                        .build())
                .device(Device.builder().ua("UnitTestUA").ip("56.76.12.3").build())
                .user(User.builder().id("userId").build())
                .cur(singletonList("USD"))
                .tmax(2000L)
                .at(1)
                .build();

        given(ipAddressHelper.toIpAddress(eq("56.76.12.3")))
                .willReturn(IpAddress.of("56.76.12.3", IpAddress.IP.v4));

        givenImplicitParams(
                "http://anotherexample.com", "anotherexample.com", "192.168.244.2", IpAddress.IP.v4, "UnitTest2");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result)
                .extracting(BidRequest::getSite, BidRequest::getDevice)
                .containsExactly(bidRequest.getSite(), bidRequest.getDevice());
    }

    @Test
    public void shouldSetSiteExtIfNoReferer() {
        // when
        final BidRequest result = target.resolve(defaultBidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite())
                .extracting(Site::getExt)
                .isEqualTo(ExtSite.of(0, null));
    }

    @Test
    public void shouldNotSetSitePageIfNoReferer() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(
                Site.builder().domain("home.com").ext(ExtSite.of(0, null)).build());
    }

    @Test
    public void shouldNotSetSitePageIfDomainCouldNotBeDerived() {
        // given
        given(paramsExtractor.refererFrom(any())).willReturn("http://not-valid-site");
        given(paramsExtractor.domainFrom(anyString())).willThrow(new PreBidException("Couldn't derive domain"));

        // when
        final BidRequest result = target.resolve(defaultBidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite().getPage()).isNull();
    }

    @Test
    public void shouldSetDomainFromPageInsteadOfReferer() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().page("http://page.site.com/page1.html").build())
                .build();

        given(paramsExtractor.refererFrom(any())).willReturn("http://any-site/referer.html");
        given(paramsExtractor.domainFrom(anyString())).willReturn("site.com");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        verify(paramsExtractor).domainFrom(eq("page.site.com"));

        assertThat(singleton(result.getSite()))
                .extracting(Site::getPage, Site::getDomain, site -> site.getPublisher().getDomain())
                .containsOnly(tuple("http://page.site.com/page1.html", "page.site.com", "site.com"));
    }

    @Test
    public void shouldSetSiteExtAmpIfSiteHasNoExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .page("http://test.com")
                        .domain("test.com")
                        .publisher(Publisher.builder().domain("test.com").build())
                        .build())
                .build();
        givenImplicitParams(
                "http://anotherexample.com", "anotherexample.com", "192.168.244.2", IpAddress.IP.v4, "UnitTest2");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(
                Site.builder()
                        .page("http://test.com")
                        .domain("test.com")
                        .publisher(Publisher.builder().domain("test.com").build())
                        .ext(ExtSite.of(0, null))
                        .build());
    }

    @Test
    public void shouldSetSiteExtAmpIfSiteExtHasNoAmp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .page("http://test.com")
                        .domain("test.com")
                        .publisher(Publisher.builder().domain("test.com").build())
                        .ext(ExtSite.of(null, null))
                        .build())
                .build();
        givenImplicitParams(
                "http://anotherexample.com", "anotherexample.com", "192.168.244.2", IpAddress.IP.v4, "UnitTest2");

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder()
                .page("http://test.com")
                .domain("test.com")
                .publisher(Publisher.builder().domain("test.com").build())
                .ext(ExtSite.of(0, null))
                .build());
    }

    @Test
    public void shouldSetSiteExtAmpIfNoReferer() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com").build())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSite()).isEqualTo(
                Site.builder().domain("test.com").page("http://test.com")
                        .ext(ExtSite.of(0, null)).build());
    }

    @Test
    public void shouldSetSourceTidIfNotDefined() {
        // given
        given(idGenerator.generateId()).willReturn("f6965ea7-f281-4eb9-9de2-560a52d954a3");

        final ExtRequest extRequest = jacksonMapper.fillExtension(
                ExtRequest.empty(),
                mapper.createObjectNode().putPOJO("schain", SupplyChain.of(1, null, "ver", null)));
        final BidRequest bidRequest = BidRequest.builder().ext(extRequest).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSource())
                .extracting(Source::getTid)
                .isEqualTo("f6965ea7-f281-4eb9-9de2-560a52d954a3");
    }

    @Test
    public void shouldSetSourceSchainIfNotDefinedAndExtSchainPresent() {
        // given
        final ExtRequest extRequest = jacksonMapper.fillExtension(
                ExtRequest.empty(),
                mapper.createObjectNode().putPOJO("schain", SupplyChain.of(1, null, "ver", null)));
        final BidRequest bidRequest = BidRequest.builder().ext(extRequest).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getSource())
                .extracting(Source::getSchain)
                .isEqualTo(SupplyChain.of(1, null, "ver", null));
    }

    @Test
    public void shouldSetDefaultAtIfInitialValueIsEqualsToZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder().at(0).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getAt()).isEqualTo(1);
    }

    @Test
    public void shouldSetDefaultAtIfInitialValueIsEqualsToNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder().at(null).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getAt()).isEqualTo(1);
    }

    @Test
    public void shouldSetCurrencyIfMissedInRequestAndPresentInAdServerCurrencyConfig() {
        // given
        final BidRequest bidRequest = BidRequest.builder().cur(null).build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getCur()).isEqualTo(singletonList("USD"));
    }

    @Test
    public void shouldSetTimeoutFromTimeoutResolver() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getTmax()).isEqualTo(2000L);
    }

    @Test
    public void shouldConvertStringPriceGranularityViewToCustom() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().pricegranularity(new TextNode("low")).build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))));
    }

    @Test
    public void shouldNotUpdateExtTargetingIfImpressionsAreMissed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .containsNull();
    }

    @Test
    public void shouldReturnFailedFutureWithInvalidRequestExceptionWhenStringPriceGranularityInvalid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().pricegranularity(new TextNode("invalid")).build())
                        .build()))
                .build();

        // when and then
        assertThatThrownBy(() -> target.resolve(bidRequest, httpRequest, ENDPOINT, false))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid string price granularity with value: invalid");
    }

    @Test
    public void shouldSetDefaultPriceGranularityIfPriceGranularityAndMediaTypePriceGranularityIsMissing() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build()).ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))));
    }

    @Test
    public void shouldNotSetDefaultPriceGranularityIfThereIsAMediaTypePriceGranularityForImpType() {
        // given
        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = ExtMediaTypePriceGranularity.of(
                mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))), null, null);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .mediatypepricegranularity(mediaTypePriceGranularity)
                                .build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly((JsonNode) null);
    }

    @Test
    public void shouldSetDefaultIncludeWinnersIfIncludeWinnersIsMissed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsOnly(true);
    }

    @Test
    public void shouldAddNewBidderToImpBidderParamsWhenRequestLevelHasNotSharedBidderWithParams() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().bidderparams(mapper.createObjectNode().set("bidder2",
                        mapper.createObjectNode().put("key2", "value2"))).build()))
                .imp(singletonList(Imp.builder()
                        .ext(mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode()
                                        .set("bidder1", mapper.createObjectNode().put("key1", "value1")))))
                        .build())).build();

        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .<ObjectNode>set("bidder2", mapper.createObjectNode().put("key2", "value2"))
                                .set("bidder1", mapper.createObjectNode().put("key1", "value1"))));

        assertThat(result.getImp()).extracting(Imp::getExt)
                .element(0).isNotNull().isEqualTo(expectedResult);
    }

    @Test
    public void shouldMergeImpAndRequestBidderParamsForSharedBidderWithImpPriority() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().bidderparams(mapper.createObjectNode().set("bidder1",
                        mapper.createObjectNode().put("key1", "value1-request").put("key2", "value2"))).build()))
                .imp(singletonList(Imp.builder()
                        .ext(mapper.createObjectNode()
                                .set("prebid", mapper.createObjectNode()
                                        .set("bidder", mapper.createObjectNode()
                                                .set("bidder1", mapper.createObjectNode().put("key1", "value1-imp")))))
                        .build())).build();

        given(jsonMerger.merge(any(), any())).willReturn(mapper.createObjectNode().put("key1", "value1-imp")
                .put("key2", "value2"));

        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .set("bidder1", mapper.createObjectNode().put("key1", "value1-imp")
                                        .put("key2", "value2"))));

        assertThat(result.getImp()).extracting(Imp::getExt).element(0).isNotNull().isEqualTo(expectedResult);
    }

    @Test
    public void shouldNotRemoveImpBidderParamsThatWasNotParticipateInMerge() {
        // given
        final ObjectNode impBidderParams = mapper.createObjectNode().set("bidder1",
                mapper.createObjectNode().put("key1", "value1"));
        impBidderParams.set("bidder2", mapper.createObjectNode().put("key1", "value1"));

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().bidderparams(mapper.createObjectNode().set("bidder1",
                        mapper.createObjectNode().put("key2", "value2"))).build()))
                .imp(singletonList(Imp.builder()
                        .ext(impBidderParams).build())).build();

        given(jsonMerger.merge(any(), any())).willReturn(mapper.createObjectNode().put("key1", "value1")
                .put("key2", "value2"));

        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        final ObjectNode expectedResult = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode()
                        .set("bidder", mapper.createObjectNode()
                                .<ObjectNode>set("bidder1", mapper.createObjectNode().put("key1", "value1")
                                        .put("key2", "value2"))
                                .set("bidder2", mapper.createObjectNode().put("key1", "value1"))));

        assertThat(result.getImp()).extracting(Imp::getExt)
                .element(0).isNotNull().isEqualTo(expectedResult);
    }

    @Test
    public void shouldNotAddContextAndPrebidAsBidderParamsIfDefinedInRequest() {
        // given
        final ObjectNode requestBidderParams = mapper.createObjectNode().set("prebid",
                mapper.createObjectNode().put("key1", "value1"));
        requestBidderParams.set("context", mapper.createObjectNode().put("key1", "value1"));

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().bidderparams(requestBidderParams).build()))
                .imp(singletonList(Imp.builder()
                        .ext(mapper.createObjectNode()
                                .set("prebid", mapper.createObjectNode()
                                        .set("bidder", mapper.createObjectNode()
                                                .set("bidder1", mapper.createObjectNode().put("key1", "value1")))))
                        .build())).build();

        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).extracting(Imp::getExt)
                .element(0).isNotNull().isEqualTo(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .set("bidder", mapper.createObjectNode()
                                        .set("bidder1", mapper.createObjectNode().put("key1", "value1")))));
    }

    @Test
    public void shouldNotMergeContextAndPrebidAsBidderParamsIfDefinedInRequest() {
        // given
        final ObjectNode requestBidderParams = mapper.createObjectNode().set("prebid",
                mapper.createObjectNode().put("key1", "value1-request"));
        requestBidderParams.set("context", mapper.createObjectNode().put("key1", "value1"));

        final ObjectNode impBidderParams = mapper.createObjectNode().set("prebid",
                mapper.createObjectNode().put("key2", "value2"));
        impBidderParams.set("context", mapper.createObjectNode().put("key2", "value2"));

        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().bidderparams(requestBidderParams).build()))
                .imp(singletonList(Imp.builder()
                        .ext(impBidderParams).build())).build();

        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getImp()).extracting(Imp::getExt).element(0).isNotNull().isEqualTo(impBidderParams);
    }

    @Test
    public void shouldSetDefaultIncludeBidderKeysIfIncludeBidderKeysIsMissed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsOnly(true);
    }

    @Test
    public void shouldSetDefaultIncludeBidderKeysToFalseIfIncludeBidderKeysIsMissedAndWinningonlyIsTrue() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .cache(ExtRequestPrebidCache.of(null, null, true))
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsOnly(false);
    }

    @Test
    public void shouldSetDefaultIncludeBidderKeysToFalseIfIncludeBidderKeysIsMissedAndWinningonlyIsTrueInConfig() {
        // given
        target = target(true);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result.getExt())
                .extracting(extBidRequest -> extBidRequest.getPrebid().getTargeting().getIncludebidderkeys())
                .isEqualTo(false);
    }

    @Test
    public void shouldSetCacheWinningonlyFromConfigWhenExtRequestPrebidIsNull() {
        // given
        target = target(true);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.empty())
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldSetCacheWinningonlyFromConfigWhenExtRequestPrebidCacheIsNull() {
        // given
        target = target(true);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldSetCacheWinningonlyFromConfigWhenCacheWinningonlyIsNull() {
        // given
        target = target(true);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .cache(ExtRequestPrebidCache.of(null, null, null))
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldNotChangeAnyOtherExtRequestPrebidCacheFields() {
        // given
        final ExtRequestPrebidCacheBids cacheBids = ExtRequestPrebidCacheBids.of(100, true);
        final ExtRequestPrebidCacheVastxml cacheVastxml = ExtRequestPrebidCacheVastxml.of(100, true);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .cache(ExtRequestPrebidCache.of(cacheBids, cacheVastxml, null))
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getBids, ExtRequestPrebidCache::getVastxml)
                .containsOnly(tuple(cacheBids, cacheVastxml));
    }

    @Test
    public void shouldNotChangeAnyOtherExtRequestPrebidTargetingFields() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .includebrandcategory(ExtIncludeBrandCategory.of(1, "publisher", true, null))
                                .truncateattrchars(10)
                                .build())
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebrandcategory, ExtRequestTargeting::getTruncateattrchars)
                .containsOnly(tuple(ExtIncludeBrandCategory.of(1, "publisher", true, null), 10));
    }

    @Test
    public void shouldSetCacheWinningonlyFromRequestWhenCacheWinningonlyIsPresent() {
        // given
        target = target(true);

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .cache(ExtRequestPrebidCache.of(null, null, false))
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .isEqualTo(false);
    }

    @Test
    public void shouldNotSetCacheWinningonlyFromConfigWhenCacheWinningonlyIsNullAndConfigValueIsFalse() {
        // given
        final ExtRequest extBidRequest = ExtRequest.of(ExtRequestPrebid.builder()
                .cache(ExtRequestPrebidCache.of(null, null, null))
                .build());

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(extBidRequest)
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .isNull();
    }

    @Test
    public void shouldCreateExtBidRequestPbsFromGivenEndpoint() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest, httpRequest, Endpoint.openrtb2_auction.value(), false);

        // then
        assertThat(result)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getServer)
                .extracting(ExtRequestPrebidServer::getEndpoint)
                .isEqualTo(Endpoint.openrtb2_auction.value());
    }

    @Test
    public void shouldSetRequestPrebidChannelWhenMissingInRequestAndSiteIsPresentAndEndpointIsNotAmp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().build())
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest, httpRequest, Endpoint.openrtb2_auction.value(), false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getChannel)
                .containsOnly(ExtRequestPrebidChannel.of("web"));
    }

    @Test
    public void shouldNotSetRequestPrebidChannelWhenMissingInRequestAndAppIsPresentAndEndpointIsNotAmp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .app(App.builder().build())
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest, httpRequest, Endpoint.openrtb2_auction.value(), false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getChannel)
                .containsOnly(ExtRequestPrebidChannel.of("app"));
    }

    @Test
    public void shouldThrowExceptionWhenRequestPrebidChannelIsPresentAndChannelNameIsBlank() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().build())
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().channel(ExtRequestPrebidChannel.of("")).build()))
                .build();

        // when and then
        assertThatThrownBy(() -> target.resolve(bidRequest, httpRequest, ENDPOINT, false))
                .isInstanceOf(PreBidException.class)
                .hasMessage("ext.prebid.channel.name can't be empty");
    }

    @Test
    public void shouldSetRequestPrebidChannelWhenMissingInRequestAndEndpointIsAmp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getChannel)
                .containsOnly(ExtRequestPrebidChannel.of("amp"));
    }

    @Test
    public void shouldNotSetRequestPrebidChannelWhenMissingInRequestAndNotSiteOrAppOrAmp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest result = target.resolve(
                bidRequest, httpRequest, Endpoint.openrtb2_auction.value(), false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getChannel)
                .containsOnly((ExtRequestPrebidChannel) null);
    }

    @Test
    public void shouldNotSetRequestPrebidChannelWhenPresentInRequestAndApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .channel(ExtRequestPrebidChannel.of("custom"))
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getChannel)
                .containsOnly(ExtRequestPrebidChannel.of("custom"));
    }

    @Test
    public void shouldSetRequestPrebidChannelToAppWhenMissingInRequestAndBothAppAndSitePresentAndEndpointIsNotAmp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .site(Site.builder().build())
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        // when
        final BidRequest request = target.resolve(
                bidRequest, httpRequest, Endpoint.openrtb2_auction.value(), false);

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getChannel)
                .containsOnly(ExtRequestPrebidChannel.of("app"));
    }

    @Test
    public void shouldPassExtPrebidDebugFlagIfPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(1);
    }

    @Test
    public void shouldPassExtPrebidServer() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .build();

        // when
        final BidRequest result = target.resolve(bidRequest, httpRequest, ENDPOINT, false);

        // then
        assertThat(result)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getServer)
                .isEqualTo(ExtRequestPrebidServer.of("https://external.url/", 0, "datacenter-region", ENDPOINT));
    }

    @Test
    public void shouldReturnFailedFutureWhenAppIdIsBlacklisted() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().id("bad_app").build())
                .build();

        // when and then
        assertThatExceptionOfType(BlacklistedAppException.class)
                .isThrownBy(() -> target.resolve(bidRequest, httpRequest, ENDPOINT, false))
                .withMessage("Prebid-server does not process requests from App ID: bad_app");
    }

    private void givenImplicitParams(String referer, String domain, String ip, IpAddress.IP ipVersion, String ua) {
        given(paramsExtractor.refererFrom(any())).willReturn(referer);
        given(paramsExtractor.domainFrom(anyString())).willReturn(domain);
        given(paramsExtractor.ipFrom(any(CaseInsensitiveMultiMap.class), any())).willReturn(singletonList(ip));
        given(ipAddressHelper.toIpAddress(eq(ip))).willReturn(IpAddress.of(ip, ipVersion));
        given(paramsExtractor.uaFrom(any())).willReturn(ua);
    }
}
