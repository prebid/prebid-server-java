package org.prebid.server.bidder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.MediaType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class HttpAdapterRequesterTest {

    private static final String BIDDER = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpAdapterConnector httpAdapterConnector;
    @Mock
    private Adapter<?, ?> adapter;
    @Mock
    private Usersyncer usersyncer;

    private HttpAdapterRequester httpAdapterRequester;

    private Timeout timeout;

    @Before
    public void setUp() {
        // given
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500L);

        httpAdapterRequester = new HttpAdapterRequester(BIDDER, adapter, usersyncer, httpAdapterConnector);
    }

    @Test
    public void shouldNotSendRequestIfAccountIdIsMissed() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput(
                        "bidrequest.site.publisher.id or bidrequest.app.publisher.id required for legacy bidders."))));
    }

    @Test
    public void shouldNotSendRequestIfAccountIdInSitePublisherIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder().site(Site.builder()
                .publisher(Publisher.builder().id("").build()).build()).build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput(
                        "bidrequest.site.publisher.id or bidrequest.app.publisher.id required for legacy bidders."))));
    }

    @Test
    public void shouldNotSendRequestIfTransactionIdIsMissed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput("bidrequest.source.tid required for legacy bidders."))));
    }

    @Test
    public void shouldNotSendRequestIfTransactionIdIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("").build())
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput("bidrequest.source.tid required for legacy bidders."))));
    }

    @Test
    public void shouldNotSendRequestIfSecureFlagMixingInImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(asList(Imp.builder().secure(0).build(), Imp.builder().secure(1).build()))
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput(
                        "bidrequest.imp[i].secure must be consistent for legacy bidders. Mixing 0 and 1 are not " +
                                "allowed."))));
    }

    @Test
    public void shouldNotSendRequestIfImpListIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(emptyList())
                .tmax(1000L)
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput("There no imps in bidRequest for bidder rubicon"))));
    }

    @Test
    public void shouldNotSendRequestIfAllImpsHasInvalidMediaType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().secure(0).build()))
                .tmax(1000L)
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput("legacy bidders can only bid on banner and video ad units"))));
    }

    @Test
    public void shouldNotSendRequestIfSizesWasNotDefinedInBanner() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().secure(0).banner(Banner.builder().topframe(1).build()).build()))
                .tmax(1000L)
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput("legacy bidders should have at least one defined size Format"))));
    }

    @Test
    public void shouldNotSendRequestWithVideoIfVideoSizesAreNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().secure(0).video(Video.builder().build()).build()))
                .tmax(1000L)
                .build();

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        verifyZeroInteractions(httpAdapterConnector);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(emptyList(), emptyList(), singletonList(
                BidderError.badInput("legacy bidders should have at least one defined size Format"))));
    }

    @Test
    public void shouldCreateAdUnitIfOnlyVideoMediaTypeWasDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().secure(0).video(Video.builder().w(400).h(200).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .tmax(1000L)
                .build();

        given(usersyncer.cookieFamilyName()).willReturn("someCookieFamily");

        given(httpAdapterConnector.call(any(), any(), any(), any())).willReturn(Future.succeededFuture
                (AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.video).build()), null)));

        // when
        httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        final ArgumentCaptor<AdapterRequest> bidderArgumentCaptor = ArgumentCaptor.forClass(AdapterRequest.class);
        verify(httpAdapterConnector).call(eq(adapter), any(), bidderArgumentCaptor.capture(), any());
        final AdapterRequest adapterRequest = bidderArgumentCaptor.getValue();

        assertThat(adapterRequest.getAdUnitBids().get(0)).isEqualTo(AdUnitBid.builder()
                .bidderCode("rubicon")
                .sizes(singletonList(Format.builder().w(400).h(200).build()))
                .topframe(0)
                .video(org.prebid.server.proto.request.Video.builder().build())
                .mediaTypes(new HashSet<>(singletonList(MediaType.video)))
                .params(Json.mapper.createObjectNode())
                .build());
    }

    @Test
    public void shouldCreateAdUnitWithAllSizesFromVideoAndBannerIfBothAreDefined() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().secure(0)
                        .video(Video.builder().w(400).h(200).build())
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(150).build())).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .tmax(1000L)
                .build();

        given(usersyncer.cookieFamilyName()).willReturn("someCookieFamily");

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.video).build()), null)));

        // when
        httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        final ArgumentCaptor<AdapterRequest> bidderArgumentCaptor = ArgumentCaptor.forClass(AdapterRequest.class);
        verify(httpAdapterConnector).call(eq(adapter), any(), bidderArgumentCaptor.capture(), any());

        final AdapterRequest adapterRequest = bidderArgumentCaptor.getValue();
        assertThat(adapterRequest.getAdUnitBids().get(0)).isEqualTo(AdUnitBid.builder()
                .bidderCode("rubicon")
                .sizes(asList(Format.builder().w(400).h(200).build(), Format.builder().w(300).h(150).build()))
                .topframe(0)
                .video(org.prebid.server.proto.request.Video.builder().build())
                .mediaTypes(new HashSet<>(asList(MediaType.video, MediaType.banner)))
                .params(Json.mapper.createObjectNode())
                .build());
    }

    @Test
    public void shouldRespondWithBidAndErrorMessageIfFirstImpIsValidAndSecondIsNot() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(asList(Imp.builder().secure(0).build(),
                        Imp.builder().banner(Banner.builder().topframe(1)
                                .format(singletonList(Format.builder().build())).build())
                                .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder",
                                        Json.mapper.createObjectNode()))
                                .build()))
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.banner).build()), null)));

        // when
        final Future<BidderSeatBid> result = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(BidderSeatBid.of(
                singletonList(BidderBid.of(com.iab.openrtb.response.Bid.builder().build(), BidType.banner, null)),
                singletonList(ExtHttpCall.builder().build()),
                singletonList(BidderError.badInput("legacy bidders can only bid on banner and video ad units"))));
    }

    @Test
    public void shouldReturnErrorInResponseIfMediaTypeInResponseIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().banner(Banner.builder().topframe(1)
                        .format(singletonList(Format.builder().build())).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder",
                                Json.mapper.createObjectNode())).build()))
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().build()), null)));

        // when
        final Future<BidderSeatBid> futureResult = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        assertThat(futureResult.succeeded()).isTrue();
        assertThat(futureResult.result()).isEqualTo(BidderSeatBid.of(emptyList(), singletonList(ExtHttpCall
                .builder().build()), singletonList(BidderError.badServerResponse(
                "Media Type is not defined for Bid"))));
    }

    @Test
    public void shouldReturnErrorsFromBothBidderAndResponseCreation() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(asList(Imp.builder().secure(0).build(),
                        Imp.builder().banner(Banner.builder().topframe(1)
                                .format(singletonList(Format.builder().build())).build())
                                .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder",
                                        Json.mapper.createObjectNode()))
                                .build()))
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().build()), null)));

        // when
        final Future<BidderSeatBid> futureResult = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        assertThat(futureResult.succeeded()).isTrue();
        assertThat(futureResult.result()).isEqualTo(BidderSeatBid.of(emptyList(), singletonList(ExtHttpCall
                .builder().build()), asList(
                BidderError.badInput("legacy bidders can only bid on banner and video ad units"),
                BidderError.badServerResponse("Media Type is not defined for Bid"))));
    }

    @Test
    public void shouldAddAdnxCookieIfUserIdIsNotNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("publisherId").build()).build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder().secure(0).banner(Banner.builder().topframe(1)
                        .format(singletonList(Format.builder().build())).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .user(User.builder().id("someId").build())
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.banner).build()), null)));

        // when
        httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        final PreBidRequestContext preBidRequestContext = capturePreBidRequestContext();
        assertThat(preBidRequestContext.getUidsCookie().uidFrom("adnxs")).isEqualTo("someId");
    }

    @Test
    public void shouldTakeAccountIdFromSitePublisherIfSiteIsNotNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("sitePublisherId").build()).build())
                .imp(singletonList(Imp.builder().secure(0).banner(Banner.builder().topframe(1)
                        .format(singletonList(Format.builder().build())).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .source(Source.builder().tid("TransactionId").build())
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.banner).build()), null)));

        // when
        httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        final PreBidRequestContext preBidRequestContext = capturePreBidRequestContext();
        assertThat(preBidRequestContext.getPreBidRequest().getAccountId()).isEqualTo("sitePublisherId");
    }

    @Test
    public void shouldTakeAccountIdFromAppPublisherIfSiteIsNullAndAppIsNotNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().publisher(Publisher.builder().id("appPublisherId").build()).build())
                .imp(singletonList(Imp.builder().secure(0).banner(Banner.builder().topframe(1)
                        .format(singletonList(Format.builder().build())).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .source(Source.builder().tid("TransactionId").build())
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.banner).build()), null)));

        // when
        httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        final PreBidRequestContext preBidRequestContext = capturePreBidRequestContext();
        assertThat(preBidRequestContext.getPreBidRequest().getAccountId()).isEqualTo("appPublisherId");
    }

    @Test
    public void shouldSendRequestWithCorrectlyFilledPreBidRequestContextAndBidder() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .domain("domain").page("page").publisher(Publisher.builder().id("publisherId").build())
                        .build())
                .source(Source.builder().tid("TransactionId").build())
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().topframe(1).format(singletonList(
                                Format.builder().h(100).w(200).build())).build())
                        .video(Video.builder()
                                .mimes(singletonList("mime"))
                                .minduration(100)
                                .maxduration(1000)
                                .startdelay(1)
                                .skip(1)
                                .playbackmethod(singletonList(1))
                                .protocols(singletonList(1))
                                .build())
                        .id("impId")
                        .instl(2)
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .tmax(100L)
                .test(1)
                .device(Device.builder().ip("ip").build())
                .user(User.builder().buyeruid("buyeruid").build())
                .build();

        given(usersyncer.cookieFamilyName()).willReturn("someCookieFamily");

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder().build())).build(),
                        singletonList(Bid.builder().mediaType(MediaType.banner).build()), null)));

        // when
        httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        final PreBidRequestContext preBidRequestContext = capturePreBidRequestContext();
        assertThat(preBidRequestContext.getPreBidRequest()).isEqualTo(
                PreBidRequest.builder()
                        .accountId("publisherId")
                        .tid("TransactionId")
                        .secure(0)
                        .timeoutMillis(100L)
                        .device(Device.builder().ip("ip").build())
                        .user(User.builder().buyeruid("buyeruid").build())
                        .build());

        // compare fields separately because uidCookie's expiration time is different in expected and actual objects
        assertThat(preBidRequestContext.getReferer()).isEqualTo("page");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("domain");
        assertThat(preBidRequestContext.getTimeout()).isSameAs(timeout);
        assertThat(preBidRequestContext.getUidsCookie().uidFrom("someCookieFamily")).isEqualTo("buyeruid");

        final ArgumentCaptor<AdapterRequest> bidderArgumentCaptor = ArgumentCaptor.forClass(AdapterRequest.class);
        verify(httpAdapterConnector).call(eq(adapter), any(), bidderArgumentCaptor.capture(), any());

        final AdapterRequest adapterRequest = bidderArgumentCaptor.getValue();
        assertThat(adapterRequest).isEqualTo(AdapterRequest.of(BIDDER, singletonList(AdUnitBid.builder()
                .bidderCode(BIDDER)
                .bidId("impId")
                .sizes(singletonList(Format.builder().w(200).h(100).build()))
                .topframe(1)
                .instl(2)
                .adUnitCode("impId")
                .video(org.prebid.server.proto.request.Video.builder()
                        .mimes(singletonList("mime"))
                        .minduration(100)
                        .maxduration(1000)
                        .startdelay(1)
                        .skippable(1)
                        .playbackMethod(1)
                        .protocols(singletonList(1))
                        .build())
                .mediaTypes(new HashSet<>(asList(MediaType.video, MediaType.banner)))
                .params(Json.mapper.createObjectNode())
                .build())));
    }

    @Test
    public void shouldReturnCorrectBidderSeatResponse() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().publisher(Publisher.builder().id("appPublisherId").build()).build())
                .imp(singletonList(Imp.builder().secure(0).banner(Banner.builder().topframe(1)
                        .format(singletonList(Format.builder().build())).build())
                        .ext((ObjectNode) Json.mapper.createObjectNode().set("bidder", Json.mapper.createObjectNode()))
                        .build()))
                .source(Source.builder().tid("TransactionId").build())
                .tmax(1000L)
                .build();

        given(httpAdapterConnector.call(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(AdapterResponse.of(
                        BidderStatus.builder().debug(singletonList(BidderDebug.builder()
                                .requestBody("requestBody")
                                .requestUri("requestUri")
                                .responseBody("responseBody")
                                .statusCode(2)
                                .build()))
                                .build(),
                        singletonList(Bid.builder().mediaType(MediaType.banner).code("code").creativeId("creativeId")
                                .price(BigDecimal.ONE).nurl("nurl").adm("adm").width(100).height(200).dealId("dealId")
                                .build()),
                        null)));

        // when
        final Future<BidderSeatBid> futureResult = httpAdapterRequester.requestBids(bidRequest, timeout);

        // then
        assertThat(futureResult.result()).isEqualTo(BidderSeatBid.of(singletonList(
                BidderBid.of(com.iab.openrtb.response.Bid.builder()
                        .impid("code").crid("creativeId").price(BigDecimal.ONE).nurl("nurl").adm("adm").w(100).h(200)
                        .dealid("dealId").build(), BidType.banner, null)), singletonList(ExtHttpCall.builder()
                        .responsebody("responseBody").requestbody("requestBody").status(2).uri("requestUri").build()),
                emptyList()));
    }

    private PreBidRequestContext capturePreBidRequestContext() {
        final ArgumentCaptor<PreBidRequestContext> preBidRequestContextArgumentCaptor = ArgumentCaptor
                .forClass(PreBidRequestContext.class);
        verify(httpAdapterConnector)
                .call(eq(adapter), eq(usersyncer), any(), preBidRequestContextArgumentCaptor.capture());
        return preBidRequestContextArgumentCaptor.getValue();
    }

}
