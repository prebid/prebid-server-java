package org.prebid.server.bidder.dmx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.dmx.model.DmxPublisherExtId;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.dmx.ExtImpDmx;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class DmxBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private DmxBidder dmxBidder;

    @Before
    public void setUp() {
        dmxBidder = new DmxBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DmxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenUserAndAppIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.app(null).user(null), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("No user id or app id found. Could not send request to DMX."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestContainsNoIdentifierIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .app(App.builder().id(null).build())
                        .device(Device.builder().ifa(null).build())
                        .user(User.builder().id(null).ext(ExtUser.builder().eids(emptyList()).build()).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("This request contained no identifier"));
    }

    @Test
    public void makeHttpRequestsShouldNotReturnErrorWhenRequestContainsNoAppIdentifierButHaveUser() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .app(App.builder().id(null).build())
                        .device(Device.builder().ifa(null).build())
                        .user(User.builder().id("uid").ext(ExtUser.builder().eids(emptyList()).build()).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtPublisherIdAndMemberIdAreBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .memberId("")
                                .publisherId("")
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing Params for auction to be send"));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpWhenBannerFormatIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid, Imp::getSecure, Imp::getBidfloor)
                .containsOnly(tuple("dmxId", 1, BigDecimal.ONE));
    }

    @Test
    public void makeHttpRequestsShouldGetSizeForBannerFromFirstFormatIfAnyOfBannerSizesAreMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH, Banner::getW)
                .containsOnly(tuple(500, 300));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpWhenVideoIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(null).video(Video.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getTagid, Imp::getSecure, Imp::getBidfloor)
                .containsOnly(tuple("dmxId", 1, BigDecimal.ONE));
    }

    @Test
    public void makeHttpRequestsShouldSkipImpWhenExtDmxAndExtTagIdAndImpTagIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("")
                                .dmxId("")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpTagIdFromTagIdWhenExtTagIdIsPresentAndDmxIdIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("tagId")
                                .dmxId("")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("tagId");
    }

    @Test
    public void makeHttpRequestsShouldUpdateImpTagIdFromDmxIdWhenExtTagIdAndDmxIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("tagId")
                                .dmxId("dmxId")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("dmxId");
    }

    @Test
    public void makeHttpRequestsShouldEnrichVideoWithNeededProtocolsIfProtocolsAreMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().protocols(null).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .flatExtracting(Video::getProtocols)
                .containsAll(Arrays.asList(2, 3, 5, 6, 7, 8));
    }

    @Test
    public void makeHttpRequestsShouldUpdateAppPublisherWhenAppAndExtImpPublisherIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .flatExtracting(App::getPublisher)
                .containsExactly(expectedPublisher("publisherId", true));
    }

    @Test
    public void makeHttpRequestsShouldUpdateAppPublisherWhenAppAndMemberIdPresentAndImpExtPubIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .memberId("memberId")
                                .publisherId(null)
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .flatExtracting(App::getPublisher)
                .containsExactly(expectedPublisher("memberId", true));
    }

    @Test
    public void makeHttpRequestsShouldReplaceAppIdWithDeviceIfa() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .app(App.builder().id(null).build())
                        .device(Device.builder().ifa("ifa").build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .flatExtracting(App::getId)
                .containsExactly("ifa");
    }

    @Test
    public void makeHttpRequestsShouldUpdateSitePublisherWhenSiteAndExtImpPublisherIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.site(Site.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .flatExtracting(Site::getPublisher)
                .containsExactly(expectedPublisher("publisherId", false));
    }

    @Test
    public void makeHttpRequestsShouldUpdateSitePublisherWhenSiteAndMemberIdPresentAndImpExtPubIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().publisher(Publisher.builder().build()).build()),
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .memberId("memberId")
                                .publisherId(null)
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .flatExtracting(Site::getPublisher)
                .containsExactly(expectedPublisher("memberId", true));
    }

    @Test
    public void makeHttpRequestsShouldUpdateSitePublisherWithoutExtWhenSiteAndMemberIdPresentAndImpExtPubIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().build()),
                builder -> builder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .memberId("memberId")
                                .publisherId(null)
                                .build()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .flatExtracting(Site::getPublisher)
                .containsExactly(expectedPublisher("memberId", false));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = dmxBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri()).isEqualTo("https://test.endpoint.com?sellerid=sellerId");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBidForVideo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(null).video(Video.builder().build()));

        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder
                                .impid("123")
                                .adm("<Impression><![CDATA[https://gce-sc]]></Impression>"
                                        + "<Impression><![CDATA[https://us-east]]></Impression>")
                                .nurl("nurl"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall, bidRequest);

        // then
        final String adm = "<Impression><![CDATA[https://gce-sc]]></Impression>"
                + "<Impression><![CDATA[nurl]]></Impression>"
                + "<Impression><![CDATA[https://us-east]]></Impression>";
        final BidderBid expectedBidderBid = BidderBid.of(Bid.builder()
                        .impid("123")
                        .adm(adm)
                        .nurl("nurl")
                        .build(),
                video,
                "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull().hasSize(1).first().isEqualTo(expectedBidderBid);
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").adm("adm"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall,
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").adm("adm").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnResponseWithErrorWhenIdIsNotFound() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("12"))));

        // when
        final Result<List<BidderBid>> result = dmxBidder.makeBids(httpCall, BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build());

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("Failed to find impression 12"));
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .app(App.builder().id("appId").build())
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Publisher expectedPublisher(String extPublisherId, boolean isAddExt) {
        final DmxPublisherExtId dmxPublisherExtId = DmxPublisherExtId.of(extPublisherId);
        final ObjectNode encodedPublisherExt = mapper.valueToTree(dmxPublisherExtId);
        final ExtPublisher extPublisher = ExtPublisher.empty();
        extPublisher.addProperty("dmx", encodedPublisherExt);

        return Publisher.builder()
                .id(extPublisherId)
                .ext(isAddExt ? extPublisher : null)
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(500).build())).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpDmx.builder()
                                .tagId("tagId")
                                .dmxId("dmxId")
                                .memberId("memberId")
                                .publisherId("publisherId")
                                .sellerId("sellerId")
                                .bidFloor(BigDecimal.ONE)
                                .build()))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
