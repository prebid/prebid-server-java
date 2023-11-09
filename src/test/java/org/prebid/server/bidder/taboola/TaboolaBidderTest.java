package org.prebid.server.bidder.taboola;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.taboola.ExtImpTaboola;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class TaboolaBidderTest extends VertxTest {

    private final TaboolaBidder target = new TaboolaBidder(
            "https://{{MediaType}}.bidder.taboola.com/OpenRTB/PS/auction/{{GvlID}}/{{PublisherID}}",
            1,
            jacksonMapper);

    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TaboolaBidder("incorrect.endpoint", 1, jacksonMapper));
    }

    @Test
    public void makeHttpRequestShouldSkipImpsWithWrongMediaType() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("1").banner(Banner.builder().build())),
                givenImp(imp -> imp.id("2").video(Video.builder().build())),
                givenImp(imp -> imp.id("3").audio(Audio.builder().build())),
                givenImp(imp -> imp.id("4").xNative(Native.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactlyInAnyOrder("1", "4");
    }

    @Test
    public void makeHttpRequestShouldReturnErrorIfImpExtIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, "invalid"))), identity()),
                givenBannerImp(identity(), identity()), // valid imp
                givenImp(imp -> imp
                                .video(Video.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, "invalid"))),
                        identity()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Cannot construct instance of");
        });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotModifyExtTagIdIfExtTagIdIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(imp -> imp.tagid("tagId"),
                extImp -> extImp.tagId("extTagId").lowerCaseTagId("lowercaseTagId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("extTagId");
    }

    @Test
    public void makeHttpRequestsShouldModifyExtTagIdIfExtTagIdIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(imp -> imp.tagid("tagId"),
                extImp -> extImp.tagId("").lowerCaseTagId("lowercaseTagId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("lowercaseTagId");
    }

    @Test
    public void makeHttpRequestsShouldModifyImpTagIdIfExtImpTagIdIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(imp -> imp.tagid("tagId"), extImp -> extImp.tagId("extTagId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("extTagId");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpBidFloorIfExtImpBidFloorIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(imp -> imp.bidfloor(BigDecimal.TEN), impExt -> impExt.bidFloor(BigDecimal.valueOf(-1))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.TEN);
    }

    @Test
    public void makeHttpRequestsShouldModifyImpBidFloorIfExtImpBidFloorIsValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(imp -> imp.bidfloor(BigDecimal.TEN), impExt -> impExt.bidFloor(BigDecimal.ONE)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImpBannerIfExtImpPositionIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity(), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(Banner.builder().build());
    }

    @Test
    public void makeHttpRequestsShouldModifyImpBannerIfExtImpPositionIsNotNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity(), extImp -> extImp.position(1)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(Banner.builder().pos(1).build());
    }

    @Test
    public void makeHttpRequestsShouldNotModifyBadvIfExtImpBadvIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.badv(singletonList("badv")),
                givenBannerImp(identity(), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getBadv)
                .containsExactly("badv");
    }

    @Test
    public void makeHttpRequestsShouldModifyBadvIfImpExtBadvIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.badv(singletonList("badv")),
                givenBannerImp(identity(), impExt -> impExt.bAdv(singletonList("extBadv"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getBadv)
                .containsExactly("extBadv");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyBcatIfImpExtBcatIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.bcat(singletonList("bcat")),
                givenBannerImp(identity(), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getBcat)
                .containsExactly("bcat");
    }

    @Test
    public void makeHttpRequestsShouldModifyBcatIfImpExtBcatIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.bcat(singletonList("bcat")),
                givenBannerImp(identity(), impExt -> impExt.bCat(singletonList("extBcat"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getBcat)
                .containsExactly("extBcat");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyExtIfImpExtPageTypeIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.empty()),
                givenBannerImp(identity(), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(ExtRequest.empty());
    }

    @Test
    public void makeHttpRequestsShouldModifyExtIfImpExtPageTypeIsNotEmpty() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("property", TextNode.valueOf("value"));

        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(extRequest),
                givenImp(imp -> imp.banner(Banner.builder().build()), impExt -> impExt.pageType("extPageType")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).element(0)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getProperties)
                .asInstanceOf(InstanceOfAssertFactories.map(String.class, JsonNode.class))
                .containsExactlyEntriesOf(Map.of("pageType", TextNode.valueOf("extPageType")));
    }

    @Test
    public void makeHttpRequestShouldContainProperUriWhenTypeIsBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(identity(), ext -> ext.publisherId("publisherId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://display.bidder.taboola.com/OpenRTB/PS/auction/1/publisherId");
    }

    @Test
    public void makeHttpRequestShouldContainProperUriWithEncodedPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(identity(), extImp -> extImp.publisherId("not/encoded")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://display.bidder.taboola.com/OpenRTB/PS/auction/1/not%2Fencoded");
    }

    @Test
    public void makeHttpRequestShouldContainProperUriWhenTypeIsNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.xNative(Native.builder().build()), ext -> ext.publisherId("publisherId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://native.bidder.taboola.com/OpenRTB/PS/auction/1/publisherId");
    }

    @Test
    public void makeHttpRequestShouldModifySiteDependsOnExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(
                        Site.builder()
                                .id("id")
                                .name("name")
                                .publisher(Publisher.builder().id("id").build())
                                .build()),
                givenBannerImp(identity(), ext -> ext.publisherId("extPublisherId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .allSatisfy(site -> {
                    assertThat(site.getId()).isEqualTo("extPublisherId");
                    assertThat(site.getName()).isEqualTo("extPublisherId");
                    assertThat(site.getPublisher())
                            .extracting(Publisher::getId)
                            .isEqualTo("extPublisherId");
                });
    }

    @Test
    public void makeHttpRequestShouldModifySiteDomainIfExtPublisherDomainIsNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("domain").build()),
                givenBannerImp(identity(), ext -> ext.publisherDomain("extDomain")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("extDomain");
    }

    @Test
    public void makeHttpRequestShouldNotModifySiteDomainIfExtPublisherDomainIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("domain").build()),
                givenBannerImp(identity(), ext -> ext.publisherDomain("")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("domain");
    }

    @Test
    public void makeHttpRequestShouldAddEmptyDomainIfNoOtherSources() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().build()),
                givenBannerImp(identity(), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("");
    }

    @Test
    public void makeHttpRequestShouldCreateSiteIfNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenBannerImp(identity(), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .doesNotContainNull();
    }

    @Test
    public void makeHttpRequestShouldUseDataFromLastImpExtForRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(identity(), ext -> ext.publisherId("1")),
                givenBannerImp(identity(), ext -> ext.publisherId("2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsExactly("2");
    }

    @Test
    public void makeHttpRequestShouldSplitInTwoSeparateRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenBannerImp(identity(), identity()),
                givenImp(imp -> imp.xNative(Native.builder().build()), identity()),
                givenBannerImp(identity(), identity()),
                givenImp(imp -> imp.xNative(Native.builder().build()), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getImp)
                .anySatisfy(imps -> assertThat(imps)
                        .hasSize(2)
                        .allSatisfy(imp -> assertThat(imp.getBanner()).isNotNull()))
                .anySatisfy(imps -> assertThat(imps)
                        .hasSize(2)
                        .allSatisfy(imp -> assertThat(imp.getXNative()).isNotNull()));
    }

    @Test
    public void makeBidsShouldReturnErrorOnInvalidResponseBody() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenCorrespondedImpHasInvalidMediaType() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(impCustomizer -> impCustomizer
                        .id("123")
                        .video(Video.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("Failed to find banner/native impression \"123\"");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithBannerTypeWhenImpContainsBothNativeAndBanner()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(impCustomizer -> impCustomizer
                        .id("123")
                        .xNative(Native.builder().build())
                        .banner(Banner.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .server(ExtRequestPrebidServer.of(null, 1, null, null))
                        .build())).imp(List.of(imps))).build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, identity());
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                UnaryOperator<ExtImpTaboola.ExtImpTaboolaBuilder> extImpCustomizer) {

        return impCustomizer
                .apply(Imp.builder().ext(mapper
                        .valueToTree(ExtPrebid.of(null, extImpCustomizer.apply(ExtImpTaboola.builder()).build()))))
                .build();
    }

    private static Imp givenBannerImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                      UnaryOperator<ExtImpTaboola.ExtImpTaboolaBuilder> extImpCustomizer) {

        return givenImp(
                innerImpCustomizer -> impCustomizer.apply(innerImpCustomizer.banner(Banner.builder().build())),
                extImpCustomizer);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bidCustomizer.apply(Bid.builder().impid("123")).build()))
                        .build()))
                .build();
    }
}
