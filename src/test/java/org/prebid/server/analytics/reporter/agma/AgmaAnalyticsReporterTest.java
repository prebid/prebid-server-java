package org.prebid.server.analytics.reporter.agma;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iabtcf.decoder.TCString;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.agma.model.AgmaAnalyticsProperties;
import org.prebid.server.analytics.reporter.agma.model.AgmaEvent;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static io.vertx.core.http.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AgmaAnalyticsReporterTest extends VertxTest {

    private static final String VALID_CONSENT =
            "CQEXy8AQEXy8APoABABGBFEAAACAAAAAAAAAIxQAQIxAAAAA.QIxQAQIxAAAA.IAAA";
    private static final String CONSENT_WITHOUT_PURPOSE_9 =
            "CQEXy8AQEXy8APoABABGBFEAAACAAAAAAAAAIxQAQIxAAAAA.QIxQAQIxAAAA.IAAA";
    private static final String CONSENT_WITHOUT_VENDOR =
            "CQEXy8AQEXy8APoABABGBFEAAACAAAAAAAAAIwwAQIwgAAAA.QJJQAQJJAAAA.IAAA";

    private static final TCString PARSED_VALID_CONSENT = TCString.decode(VALID_CONSENT);

    @Mock(strictness = Mock.Strictness.LENIENT)
    private Vertx vertx;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HttpClient httpClient;

    @Mock
    private PrebidVersionProvider versionProvider;

    @Captor
    private ArgumentCaptor<MultiMap> headersCaptor;

    private Clock clock;

    private AgmaAnalyticsReporter target;

    @BeforeEach
    public void setUp() {
        final AgmaAnalyticsProperties properties = AgmaAnalyticsProperties.builder()
                .url("http://endpoint.com")
                .gzip(false)
                .bufferSize(100000)
                .bufferTimeoutMs(10000L)
                .maxEventsCount(0)
                .httpTimeoutMs(1000L)
                .accounts(Map.of(
                        "publisherId", "accountCode",
                        "unknown_publisherId", "anotherCode"))
                .build();

        clock = Clock.fixed(Instant.parse("2024-09-03T10:00:00Z"), ZoneId.of("UTC+05:00"));

        given(versionProvider.getNameVersionRecord()).willReturn("pbs_version");
        given(vertx.setTimer(anyLong(), any())).willReturn(1L, 2L);
        given(httpClient.request(eq(POST), anyString(), any(), anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), "")));
        given(httpClient.request(eq(POST), anyString(), any(), any(byte[].class), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), "")));

        target = new AgmaAnalyticsReporter(properties, versionProvider, jacksonMapper, clock, httpClient, vertx);
    }

    @Test
    public void processEventShouldSendEventWhenEventIsAuctionEvent() {
        // given
        final Site givenSite = Site.builder().publisher(Publisher.builder().id("publisherId").build()).build();
        final App givenApp = App.builder().build();
        final Device givenDevice = Device.builder().build();
        final User givenUser = User.builder().build();

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder()
                                .id("requestId")
                                .site(givenSite)
                                .app(givenApp)
                                .device(givenDevice)
                                .user(givenUser)
                                .build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("auction")
                .accountCode("accountCode")
                .requestId("requestId")
                .app(givenApp)
                .site(givenSite)
                .device(givenDevice)
                .user(givenUser)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                headersCaptor.capture(),
                eq(expectedEventPayload),
                eq(1000L));

        assertThat(headersCaptor.getValue())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("content-type", "application/json"),
                        tuple("x-prebid", "pbs_version"));

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldSendEventWhenEventIsVideoEvent() {
        // given
        final Site givenSite = Site.builder().publisher(Publisher.builder().id("publisherId").build()).build();
        final App givenApp = App.builder().build();
        final Device givenDevice = Device.builder().build();
        final User givenUser = User.builder().build();

        final VideoEvent videoEvent = VideoEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder()
                                .id("requestId")
                                .site(givenSite)
                                .app(givenApp)
                                .device(givenDevice)
                                .user(givenUser)
                                .build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(videoEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("video")
                .accountCode("accountCode")
                .requestId("requestId")
                .app(givenApp)
                .site(givenSite)
                .device(givenDevice)
                .user(givenUser)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                headersCaptor.capture(),
                eq(expectedEventPayload),
                eq(1000L));

        assertThat(headersCaptor.getValue())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("content-type", "application/json"),
                        tuple("x-prebid", "pbs_version"));

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldSendEventWhenEventIsAmpEvent() {
        // given
        final Site givenSite = Site.builder().publisher(Publisher.builder().id("publisherId").build()).build();
        final App givenApp = App.builder().build();
        final Device givenDevice = Device.builder().build();
        final User givenUser = User.builder().build();

        final AmpEvent ampEvent = AmpEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder()
                                .id("requestId")
                                .site(givenSite)
                                .app(givenApp)
                                .device(givenDevice)
                                .user(givenUser)
                                .build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(ampEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("amp")
                .accountCode("accountCode")
                .requestId("requestId")
                .app(givenApp)
                .site(givenSite)
                .device(givenDevice)
                .user(givenUser)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                headersCaptor.capture(),
                eq(expectedEventPayload),
                eq(1000L));

        assertThat(headersCaptor.getValue())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("content-type", "application/json"),
                        tuple("x-prebid", "pbs_version"));

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldNotSendAnythingWhenEventIsNotAuctionAmpOrVideo() {
        // given
        final NotificationEvent notificationEvent = NotificationEvent.builder().build();

        // when
        final Future<Void> result = target.processEvent(notificationEvent);

        // then
        assertThat(result.succeeded()).isTrue();
        verifyNoInteractions(httpClient);
    }

    @Test
    public void processEventShouldSendEventWhenConsentIsValidButWasParsedFromUserExt() {
        // given
        final Site givenSite = Site.builder().publisher(Publisher.builder().id("publisherId").build()).build();
        final App givenApp = App.builder().build();
        final Device givenDevice = Device.builder().build();
        final User givenUser = User.builder().ext(ExtUser.builder().consent(VALID_CONSENT).build()).build();

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder()
                                .id("requestId")
                                .site(givenSite)
                                .app(givenApp)
                                .device(givenDevice)
                                .user(givenUser)
                                .build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("auction")
                .accountCode("accountCode")
                .requestId("requestId")
                .app(givenApp)
                .site(givenSite)
                .device(givenDevice)
                .user(givenUser)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                any(),
                eq(expectedEventPayload),
                eq(1000L));

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldNotSendAnythingWhenVendorIsNotAllowed() {
        // given
        final User givenUser = User.builder().ext(ExtUser.builder().consent(CONSENT_WITHOUT_VENDOR).build()).build();
        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .bidRequest(BidRequest.builder().user(givenUser).build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        verifyNoInteractions(httpClient);
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldNotSendAnythingWhenPurposeIsNotAllowed() {
        // given
        final User givenUser = User.builder().ext(ExtUser.builder().consent(CONSENT_WITHOUT_PURPOSE_9).build()).build();
        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .bidRequest(BidRequest.builder().user(givenUser).build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        verifyNoInteractions(httpClient);
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldNotSendAnythingWhenAccountsDoesNotHaveConfiguredPublisher() {
        // given
        final AgmaAnalyticsProperties properties = AgmaAnalyticsProperties.builder()
                .url("http://endpoint.com")
                .gzip(false)
                .bufferSize(100000)
                .bufferTimeoutMs(10000L)
                .maxEventsCount(0)
                .httpTimeoutMs(1000L)
                .accounts(Map.of("unknown_publisherId", "anotherCode"))
                .build();

        target = new AgmaAnalyticsReporter(properties, versionProvider, jacksonMapper, clock, httpClient, vertx);

        // given
        final Site givenSite = Site.builder().publisher(Publisher.builder().id("publisherId").build()).build();

        final AmpEvent ampEvent = AmpEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .bidRequest(BidRequest.builder().site(givenSite).build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(ampEvent);

        // then
        verifyNoInteractions(httpClient);
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void processEventShouldSendWhenAccountsHasConfiguredAppsOrSites() {
        // given
        final AgmaAnalyticsProperties properties = AgmaAnalyticsProperties.builder()
                .url("http://endpoint.com")
                .gzip(false)
                .bufferSize(100000)
                .bufferTimeoutMs(10000L)
                .maxEventsCount(0)
                .httpTimeoutMs(1000L)
                .accounts(Map.of("publisherId_bundleId", "accountCode"))
                .build();

        target = new AgmaAnalyticsReporter(properties, versionProvider, jacksonMapper, clock, httpClient, vertx);

        // given
        final App givenApp = App.builder().bundle("bundleId")
                .publisher(Publisher.builder().id("publisherId").build()).build();
        final Device givenDevice = Device.builder().build();
        final User givenUser = User.builder().build();

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder()
                                .id("requestId")
                                .app(givenApp)
                                .app(givenApp)
                                .device(givenDevice)
                                .user(givenUser)
                                .build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("auction")
                .accountCode("accountCode")
                .requestId("requestId")
                .app(givenApp)
                .device(givenDevice)
                .user(givenUser)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                any(),
                eq(expectedEventPayload),
                eq(1000L));
    }

    @Test
    public void processEventShouldSendWhenAccountsHasConfiguredAppsOrSitesOnly() {
        // given
        final AgmaAnalyticsProperties properties = AgmaAnalyticsProperties.builder()
                .url("http://endpoint.com")
                .gzip(false)
                .bufferSize(100000)
                .bufferTimeoutMs(10000L)
                .maxEventsCount(0)
                .httpTimeoutMs(1000L)
                .accounts(Map.of("_mySite", "accountCode"))
                .build();

        target = new AgmaAnalyticsReporter(properties, versionProvider, jacksonMapper, clock, httpClient, vertx);

        // given
        final Site givenSite = Site.builder().id("mySite").build();
        final Device givenDevice = Device.builder().build();
        final User givenUser = User.builder().build();

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder()
                                .id("requestId")
                                .site(givenSite)
                                .device(givenDevice)
                                .user(givenUser)
                                .build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("auction")
                .accountCode("accountCode")
                .requestId("requestId")
                .site(givenSite)
                .device(givenDevice)
                .user(givenUser)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                any(),
                eq(expectedEventPayload),
                eq(1000L));
    }

    @Test
    public void processEventShouldSendEncodingGzipHeaderAndCompressedPayload() {
        // given
        final AgmaAnalyticsProperties properties = AgmaAnalyticsProperties.builder()
                .url("http://endpoint.com")
                .gzip(true)
                .bufferSize(100000)
                .bufferTimeoutMs(10000L)
                .maxEventsCount(0)
                .httpTimeoutMs(1000L)
                .accounts(Map.of("publisherId", "accountCode"))
                .build();

        target = new AgmaAnalyticsReporter(properties, versionProvider, jacksonMapper, clock, httpClient, vertx);

        final Site givenSite = Site.builder().publisher(Publisher.builder().id("publisherId").build()).build();

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .privacyContext(PrivacyContext.of(
                                null, TcfContext.builder().consent(PARSED_VALID_CONSENT).build()))
                        .timeoutContext(TimeoutContext.of(clock.millis(), null, 1))
                        .bidRequest(BidRequest.builder().site(givenSite).build())
                        .build())
                .build();

        // when
        final Future<Void> result = target.processEvent(auctionEvent);

        // then
        final AgmaEvent expectedEvent = AgmaEvent.builder()
                .eventType("auction")
                .accountCode("accountCode")
                .site(givenSite)
                .startTime(ZonedDateTime.parse("2024-09-03T15:00:00+05:00"))
                .build();

        final String expectedEventPayload = "[" + jacksonMapper.encodeToString(expectedEvent) + "]";

        verify(httpClient).request(
                eq(POST),
                eq("http://endpoint.com"),
                headersCaptor.capture(),
                aryEq(gzip(expectedEventPayload)),
                eq(1000L));

        assertThat(headersCaptor.getValue())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("content-type", "application/json"),
                        tuple("content-encoding", "gzip"),
                        tuple("x-prebid", "pbs_version"));

        assertThat(result.succeeded()).isTrue();
    }

    private static byte[] gzip(String value) {
        try (ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj)) {

            gzip.write(value.getBytes(StandardCharsets.UTF_8));
            gzip.finish();

            return obj.toByteArray();
        } catch (IOException e) {
            return new byte[]{};
        }
    }
}
