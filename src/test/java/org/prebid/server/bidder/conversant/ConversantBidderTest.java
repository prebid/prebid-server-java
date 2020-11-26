package org.prebid.server.bidder.conversant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.conversant.ExtImpConversant;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class ConversantBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ConversantBidder conversantBidder;

    @Before
    public void setUp() {
        conversantBidder = new ConversantBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConversantBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSkipInvalidImpAndAddErrorIfImpHasNoBannerOrVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder.banner(null), identity()),
                        givenImp(identity(), identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "Invalid MediaType. Conversant supports only Banner and Video. Ignoring ImpID=123"));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfRequestHasSiteAndImpExtSiteIdIsNullOrEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().build())
                .imp(asList(
                        givenImp(identity(), builder -> builder.siteId(null)),
                        givenImp(identity(), builder -> builder.siteId(""))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Missing site id"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetSiteIdFromImpExtForSiteRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsOnly("site id");
    }

    @Test
    public void makeHttpRequestsShouldSetAppIdFromExtSiteIdIfAppIdIsNullOrEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.app(App.builder().id("").build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getId)
                .containsOnly("site id");
    }

    @Test
    public void makeHttpRequestsShouldSetSiteIdFromExtSiteIdIfSiteIdIsNullOrEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().id(null).build()),
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpConversant.builder().mobile(123).siteId("site id").build()))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsOnly("site id");
    }

    @Test
    public void makeHttpRequestsShouldSetSiteMobileFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().build()),
                identity(),
                extBuilder -> extBuilder.mobile(1));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getMobile)
                .containsOnly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetImpDisplaymanagerAndDisplaymanagerver() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanager, Imp::getDisplaymanagerver)
                .containsOnly(tuple("prebid-s2s", "1.0.1"));
    }

    @Test
    public void makeHttpRequestsShouldSetImpBidfloorFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.bidfloor(BigDecimal.valueOf(9.99)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsOnly(BigDecimal.valueOf(9.99));
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagIdFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extBuilder -> extBuilder.tagId("tag id"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("tag id");
    }

    @Test
    public void makeHttpRequestsShouldChangeImpSecureFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.secure(0),
                extBuilder -> extBuilder.secure(1));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure)
                .containsOnly(1);
    }

    @Test
    public void makeHttpRequestsShouldNotChangeImpSecure() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.secure(1),
                extBuilder -> extBuilder.secure(0));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure)
                .containsOnly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetImpBannerAndVideoPosFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().pos(1).build()),
                extBuilder -> extBuilder.position(5));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsOnly(tuple(
                        Banner.builder().pos(5).build(),
                        Video.builder().pos(5).build()));
    }

    @Test
    public void makeHttpRequestsShouldNotChangeVideoPosFromImpExtIfNotInRange() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().pos(7).build()),
                extBuilder -> extBuilder.position(9));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getPos)
                .containsOnly(7);
    }

    @Test
    public void makeHttpRequestsShouldNotSetVideoPosIfNotInRange() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().pos(10).build()),
                extBuilder -> extBuilder.position(9));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getPos)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetVideoMimesFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().build()),
                extBuilder -> extBuilder.mimes(singletonList("mime")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .flatExtracting(Video::getMimes)
                .containsOnly("mime");
    }

    @Test
    public void makeHttpRequestsShouldSetVideoMaxdurationFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().build()),
                extBuilder -> extBuilder.maxduration(1000));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getMaxduration)
                .containsOnly(1000);
    }

    @Test
    public void makeHttpRequestsShouldChangeVideoApiFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().build()),
                extBuilder -> extBuilder.api(asList(2, 5)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .flatExtracting(Video::getApi)
                .containsOnly(2, 5);
    }

    @Test
    public void makeHttpRequestsShouldPrioritizeVideoApiFromImpExtEvenIfInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().api(asList(1, 2)).build()),
                extBuilder -> extBuilder.api(asList(0, 8)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .flatExtracting(Video::getApi)
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldChangeVideoProtocolsFromImpExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().build()),
                extBuilder -> extBuilder.protocols(asList(1, 10)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .flatExtracting(Video::getProtocols)
                .containsOnly(1, 10);
    }

    @Test
    public void makeHttpRequestsShouldPrioritizeVideoProtocolsFromImpExtEvenIfInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.video(Video.builder().protocols(asList(1, 2)).build()),
                extBuilder -> extBuilder.protocols(asList(0, 12)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = conversantBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .flatExtracting(Video::getProtocols)
                .isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = conversantBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = conversantBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = conversantBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("123")
                        .banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = conversantBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfRequestImpHasVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.id("123")
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = conversantBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpConversant.ExtImpConversantBuilder,
                    ExtImpConversant.ExtImpConversantBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpConversant.ExtImpConversantBuilder,
                    ExtImpConversant.ExtImpConversantBuilder> extCustomizer) {

        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpConversant.ExtImpConversantBuilder,
                    ExtImpConversant.ExtImpConversantBuilder> extCustomizer) {

        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        extCustomizer.apply(ExtImpConversant.builder().siteId("site id")).build()))))
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
