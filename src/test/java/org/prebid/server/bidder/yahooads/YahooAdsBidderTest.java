package org.prebid.server.bidder.yahooads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.yahooads.ExtImpYahooAds;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

@ExtendWith(MockitoExtension.class)
public class YahooAdsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private YahooAdsBidder target;

    @BeforeEach
    public void setUp() {
        target = new YahooAdsBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new YahooAdsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("imp #0: Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenDcnIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYahooAds.of("", null)))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #0: missing param dcn"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPosIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYahooAds.of("dcn", "")))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #0: missing param pos"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateARequestForEachImpAndSkipImpsWithErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder.id("imp1")),
                        givenImp(impBuilder -> impBuilder.id("imp2")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYahooAds.of("dcn", ""))))),
                        givenImp(impBuilder -> impBuilder.id("imp3"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #1: missing param pos"));
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getId)
                .containsOnly("imp1", "imp3");
    }

    @Test
    public void makeHttpRequestsShouldAlwaysSetImpTagIdFromImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getTagid)
                .containsExactly("pos");
    }

    @Test
    public void makeHttpRequestsShouldSetSiteIdIfSiteIsPresentInTheRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsExactly("dcn");
    }

    @Test
    public void makeHttpRequestsShouldSetAppIdIfAppIsPresentInTheRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                bidRequestBuilder -> bidRequestBuilder.site(null).app(App.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getId)
                .containsExactly("dcn");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerWidthIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().w(0).h(100).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid sizes provided for Banner 0x100"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerHeightIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().w(100).h(0).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid sizes provided for Banner 100x0"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerHasNoFormats() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badInput("No sizes provided for Banner"));
    }

    @Test
    public void makeHttpRequestsSetFirstImpressionBannerWidthAndHeightWhenFromFirstFormatIfTheyAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(250).h(300).build())).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(250, 300));
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.site(null).device(Device.builder().ua("UA").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getHeaders())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("User-Agent", "UA"),
                        tuple("x-openrtb-version", "2.6"),
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidImpIdIsNotPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .banner(Banner.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("321"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("Unknown ad unit code '321'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipNotBannerImpAndReturnBannerBidWhenBannerPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(asList(Imp.builder().id("123").build(),
                                Imp.builder().banner(Banner.builder().build()).id("321").build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(asList(Bid.builder().impid("123").build(),
                                        Bid.builder().impid("321").build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("321").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldSkipNotSupportedImpAndReturnVideoBidWhenVideoPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(asList(Imp.builder().id("123").build(),
                                Imp.builder().video(Video.builder().build()).id("321").build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(asList(Bid.builder().impid("123").build(),
                                        Bid.builder().impid("321").build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("321").build(), video, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldPreserveTopLevel26RegsAndExtTypedFields() {
        final ExtRegsDsa dsa = ExtRegsDsa.of(2, 2, 3, emptyList());
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .gdpr(1)
                        .usPrivacy("1YNN")
                        .gpp("gppconsent")
                        .gppSid(List.of(6))
                        .coppa(1)
                        .ext(ExtRegs.of(null, null, "1", dsa))
                        .build()).device(Device.builder().ua("UA").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGdpr()).isEqualTo(1);
        assertThat(regs.getUsPrivacy()).isEqualTo("1YNN");
        assertThat(regs.getGpp()).isEqualTo("gppconsent");
        assertThat(regs.getGppSid()).containsExactly(6);
        assertThat(regs.getCoppa()).isEqualTo(1);
        assertThat(regs.getExt()).isNotNull();
        assertThat(regs.getExt().getGpc()).isEqualTo("1");
        assertThat(regs.getExt().getDsa()).isEqualTo(dsa);
    }

    @Test
    public void makeHttpRequestsShouldPromoteLegacyExtGppGppSidAndCoppaToTopLevel() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("gpp", TextNode.valueOf("legacy_gpp_value"));
        final ArrayNode sidArray = mapper.createArrayNode();
        sidArray.add(6);
        sidArray.add(8);
        bidRequest.getRegs().getExt().addProperty("gpp_sid", sidArray);
        bidRequest.getRegs().getExt().addProperty("coppa", IntNode.valueOf(1));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGpp()).isEqualTo("legacy_gpp_value");
        assertThat(regs.getGppSid()).containsExactly(6, 8);
        assertThat(regs.getCoppa()).isEqualTo(1);
        assertThat(regs.getExt()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldPromoteOnlyGppFromExtAndStripIt() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("gpp", TextNode.valueOf("only_gpp"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGpp()).isEqualTo("only_gpp");
        assertThat(regs.getGppSid()).isNull();
        assertThat(regs.getCoppa()).isNull();
        assertThat(regs.getExt()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldPreserveTopLevelGdprWhilePromotingGppFromExt() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .gdpr(1)
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("gpp", TextNode.valueOf("mixed_gpp"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGdpr()).isEqualTo(1);
        assertThat(regs.getGpp()).isEqualTo("mixed_gpp");
        assertThat(regs.getExt()).isNull();
    }

    @Test
    public void makeHttpRequestsShouldKeepGpcAndUnrelatedExtPropertyAfterPromotion() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .ext(ExtRegs.of(null, null, "1", null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("gpp", TextNode.valueOf("with_gpc"));
        bidRequest.getRegs().getExt().addProperty("unrelated", TextNode.valueOf("keep_me"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGpp()).isEqualTo("with_gpc");
        assertThat(regs.getExt()).isNotNull();
        assertThat(regs.getExt().getGpc()).isEqualTo("1");
        assertThat(regs.getExt().getProperty("gpp")).isNull();
        assertThat(regs.getExt().getProperty("unrelated").asText()).isEqualTo("keep_me");
    }

    @Test
    public void makeHttpRequestsShouldNotPromoteWhenExtPropertyHasWrongType() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("gpp", IntNode.valueOf(99));
        bidRequest.getRegs().getExt().addProperty("gpp_sid", TextNode.valueOf("not_array"));
        bidRequest.getRegs().getExt().addProperty("coppa", TextNode.valueOf("not_int"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGpp()).isNull();
        assertThat(regs.getGppSid()).isNull();
        assertThat(regs.getCoppa()).isNull();
        assertThat(regs.getExt()).isNotNull();
        assertThat(regs.getExt().getProperty("gpp").asInt()).isEqualTo(99);
        assertThat(regs.getExt().getProperty("gpp_sid").asText()).isEqualTo("not_array");
        assertThat(regs.getExt().getProperty("coppa").asText()).isEqualTo("not_int");
    }

    @Test
    public void makeHttpRequestsShouldLeaveMalformedExtValueInExtWhenSiblingFieldIsPromoted() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("coppa", IntNode.valueOf(1));
        bidRequest.getRegs().getExt().addProperty("gpp", IntNode.valueOf(99));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getCoppa()).isEqualTo(1);
        assertThat(regs.getGpp()).isNull();
        assertThat(regs.getExt()).isNotNull();
        assertThat(regs.getExt().getProperty("coppa")).isNull();
        assertThat(regs.getExt().getProperty("gpp").asInt()).isEqualTo(99);
    }

    @Test
    public void makeHttpRequestsShouldNotPromoteGppSidWhenArrayHasNonIntegerElement() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        final ArrayNode mixed = mapper.createArrayNode();
        mixed.add(7);
        mixed.add("foo");
        mixed.add(8);
        bidRequest.getRegs().getExt().addProperty("gpp_sid", mixed);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        // whole array treated as malformed: not promoted, left in ext untouched (nothing dropped)
        assertThat(regs.getGppSid()).isNull();
        assertThat(regs.getExt()).isNotNull();
        final JsonNode keptSid = regs.getExt().getProperty("gpp_sid");
        assertThat(keptSid.isArray()).isTrue();
        assertThat(keptSid).hasSize(3);
        assertThat(keptSid.get(0).asInt()).isEqualTo(7);
        assertThat(keptSid.get(1).asText()).isEqualTo("foo");
        assertThat(keptSid.get(2).asInt()).isEqualTo(8);
    }

    @Test
    public void makeHttpRequestsShouldKeepExtGppWhenTopLevelGppAlreadySetEvenIfSiblingIsPromoted() {
        // gpp is present at BOTH top-level and in ext; a sibling (coppa) is promoted from ext.
        // gpp is not promoted (top-level wins), so its ext copy must be left untouched -
        // the strip decision does not depend on the sibling rebuild.
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .gpp("top-level-gpp")
                        .ext(ExtRegs.of(null, null, null, null))
                        .build()).device(Device.builder().ua("UA").build()));
        bidRequest.getRegs().getExt().addProperty("gpp", TextNode.valueOf("ext-gpp"));
        bidRequest.getRegs().getExt().addProperty("coppa", IntNode.valueOf(1));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        // coppa promoted and removed from ext
        assertThat(regs.getCoppa()).isEqualTo(1);
        // gpp top-level untouched; ext gpp left in place (not promoted, not stripped)
        assertThat(regs.getGpp()).isEqualTo("top-level-gpp");
        assertThat(regs.getExt()).isNotNull();
        assertThat(regs.getExt().getProperty("coppa")).isNull();
        assertThat(regs.getExt().getProperty("gpp").asText()).isEqualTo("ext-gpp");
    }

    @Test
    public void makeHttpRequestsShouldShortCircuitWhenRegsHasNoExt() {
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.regs(Regs.builder()
                        .gdpr(0)
                        .gpp("already_top")
                        .build()).device(Device.builder().ua("UA").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        final Regs regs = result.getValue().getFirst().getPayload().getRegs();
        assertThat(regs.getGdpr()).isEqualTo(0);
        assertThat(regs.getGpp()).isEqualTo("already_top");
        assertThat(regs.getExt()).isNull();
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                        .site(Site.builder().id("123").build())
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .tagid("tagId")
                        .banner(Banner.builder().w(100).h(100).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpYahooAds.of("dcn", "pos")))))
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

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
