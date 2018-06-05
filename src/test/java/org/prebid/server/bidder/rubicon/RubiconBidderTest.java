package org.prebid.server.bidder.rubicon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Metric;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExt;
import org.prebid.server.bidder.rubicon.proto.RubiconBannerExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExt;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconImpExtRpTrack;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExt;
import org.prebid.server.bidder.rubicon.proto.RubiconPubExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExt;
import org.prebid.server.bidder.rubicon.proto.RubiconSiteExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconTargeting;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExt;
import org.prebid.server.bidder.rubicon.proto.RubiconTargetingExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExt;
import org.prebid.server.bidder.rubicon.proto.RubiconUserExtRp;
import org.prebid.server.bidder.rubicon.proto.RubiconVideoExt;
import org.prebid.server.bidder.rubicon.proto.RubiconVideoExtRp;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon.ExtImpRubiconBuilder;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.*;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class RubiconBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://rubiconproject.com/exchange.json?trk=prebid";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    private final MetaInfo rubiconMetaInfo = new RubiconMetaInfo(true);

    private RubiconBidder rubiconBidder;

    @Before
    public void setUp() {
        rubiconBidder = new RubiconBidder(ENDPOINT_URL, USERNAME, PASSWORD, rubiconMetaInfo);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RubiconBidder("invalid_url", USERNAME, PASSWORD,
                rubiconMetaInfo));
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(
                Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).element(0).isNotNull()
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns(ENDPOINT_URL, HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpHeaders.AUTHORIZATION.toString(), "Basic dXNlcm5hbWU6cGFzc3dvcmQ="),
                        tuple(HttpHeaders.CONTENT_TYPE.toString(), "application/json;charset=utf-8"),
                        tuple(HttpHeaders.ACCEPT.toString(), "application/json"),
                        tuple(HttpHeaders.USER_AGENT.toString(), "prebid-server/1.0"));
    }

    @Test
    public void makeHttpRequestsShouldFillImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build()),
                builder -> builder
                        .zoneId(4001)
                        .inventory(mapper.valueToTree(Inventory.of(singletonList("5-star"), singletonList("tech")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class))
                .containsOnly(RubiconImpExt.of(RubiconImpExtRp.of(4001,
                        mapper.valueToTree(Inventory.of(singletonList("5-star"), singletonList("tech"))),
                        RubiconImpExtRpTrack.of("", "")), null));
    }

    @Test
    public void makeHttpRequestsShouldFillBannerExtWithAltSizeIdsIfMoreThanOneSize() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(Banner.builder()
                .format(asList(
                        Format.builder().w(250).h(360).build(),
                        Format.builder().w(300).h(250).build(),
                        Format.builder().w(300).h(600).build()))
                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .extracting(Banner::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class))
                .extracting(RubiconBannerExt::getRp).doesNotContainNull()
                .extracting(RubiconBannerExtRp::getSizeId, RubiconBannerExtRp::getAltSizeIds)
                .containsOnly(tuple(15, asList(10, 32)));
    }

    @Test
    public void makeHttpRequestsShouldTolerateInvalidSizes() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.banner(Banner.builder()
                .format(asList(
                        Format.builder().w(123).h(456).build(),
                        Format.builder().w(789).h(123).build(),
                        Format.builder().w(300).h(250).build()))
                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .extracting(Banner::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconBannerExt.class))
                .extracting(RubiconBannerExt::getRp).doesNotContainNull()
                .extracting(RubiconBannerExtRp::getSizeId)
                .containsOnly(15);
    }

    @Test
    public void makeHttpRequestsShouldFillVideoExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build()),
                builder -> builder.video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getVideo).doesNotContainNull()
                .extracting(Video::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconVideoExt.class))
                .containsOnly(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14)));
    }

    @Test
    public void makeHttpRequestsShouldFillVideoExtOnlyIfBothVideoAndBannerPresentInImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder().build()),
                builder -> builder
                        .video(RubiconVideoParams.builder().skip(5).skipdelay(10).sizeId(14).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .containsOnly(Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder()
                                .ext(mapper.valueToTree(RubiconVideoExt.of(5, 10, RubiconVideoExtRp.of(14))))
                                .build())
                        .ext(mapper.valueToTree(RubiconImpExt.of(
                                RubiconImpExtRp.of(null, null, RubiconImpExtRpTrack.of("", "")), null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldFillUserExtIfUserAndVisitorPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.visitor(mapper.valueToTree(
                        Visitor.of(singletonList("new"), singletonList("iphone")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(mapper.valueToTree(RubiconUserExt.of(RubiconUserExtRp.of(mapper.valueToTree(
                                Visitor.of(singletonList("new"), singletonList("iphone")))), null, null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldFillUserExtIfUserAndDigiTrustPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(
                        mapper.valueToTree(ExtUser.of(
                                ExtUserPrebid.of(emptyMap()), null, ExtUserDigiTrust.of("id", 123, 0))))
                        .build()),
                builder -> builder.video(Video.builder().build()),
                identity());
        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(mapper.valueToTree(
                                RubiconUserExt.of(null, null, ExtUserDigiTrust.of("id", 123, 0))))
                        .build());
    }

    @Test
    public void makeHttpRequestShouldFillUserIfUserAndConsentArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(
                        mapper.valueToTree(ExtUser.of(null, "consentValue", null)))
                        .build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(mapper.valueToTree(
                                RubiconUserExt.of(null, "consentValue", null)))
                        .build());
    }

    @Test
    public void makeHttpRequestShouldFailWithPreBidExceptionIfUserExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext((ObjectNode) mapper.createObjectNode()
                        .set("consent", mapper.createObjectNode())).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("Cannot deserialize instance of `java.lang.String`");
        assertThat(result.getValue()).hasSize(0);
    }

    @Test
    public void makeHttpRequestsShouldCreateUserIfVisitorPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.visitor(mapper.valueToTree(
                        Visitor.of(singletonList("new"), singletonList("iphone")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder()
                        .ext(mapper.valueToTree(
                                RubiconUserExt.of(RubiconUserExtRp.of(mapper.valueToTree(
                                        Visitor.of(singletonList("new"), singletonList("iphone")))), null, null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldNotCreateUserIfVisitorAndDigiTrustAndConsentNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .containsOnly((User) null);
    }

    @Test
    public void makeHttpRequestShouldFillRegsIfRegsAndGdprArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(50)))),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getRegs).doesNotContainNull()
                .containsOnly(Regs.of(null, mapper.valueToTree(ExtRegs.of(50))));
    }

    @Test
    public void makeHttpRequestsShouldFillDeviceExtIfDevicePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.device(Device.builder().pxratio(BigDecimal.valueOf(4.2)).build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then

        // created manually, because mapper creates Double ObjectNode instead of BigDecimal
        // for floating point numbers (affects testing only)
        final ObjectNode rp = mapper.createObjectNode();
        rp.set("rp", mapper.createObjectNode().put("pixelratio", new Double("4.2")));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getDevice).doesNotContainNull()
                .containsOnly(Device.builder()
                        .pxratio(BigDecimal.valueOf(4.2))
                        .ext(rp)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldFillSiteExtIfSitePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.accountId(2001).siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite).doesNotContainNull()
                .containsOnly(Site.builder()
                        .publisher(Publisher.builder()
                                .ext(mapper.valueToTree(RubiconPubExt.of(RubiconPubExtRp.of(2001))))
                                .build())
                        .ext(mapper.valueToTree(RubiconSiteExt.of(RubiconSiteExtRp.of(3001))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldFillAppExtIfAppPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.app(App.builder().build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder.accountId(2001).siteId(3001));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp).doesNotContainNull()
                .containsOnly(App.builder()
                        .publisher(Publisher.builder()
                                .ext(mapper.valueToTree(RubiconPubExt.of(RubiconPubExtRp.of(2001))))
                                .build())
                        .ext(mapper.valueToTree(RubiconSiteExt.of(RubiconSiteExtRp.of(3001))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateRequestPerImp() {
        // given
        final Imp imp = givenImp(builder -> builder.video(Video.builder().build()));
        final BidRequest bidRequest = BidRequest.builder().imp(asList(imp, imp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedBidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(RubiconImpExt.of(
                                RubiconImpExtRp.of(null, null, RubiconImpExtRpTrack.of("", "")), null)))
                        .build()))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsOnly(expectedBidRequest, expectedBidRequest);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(builder -> builder.video(Video.builder().build())),
                        Imp.builder()
                                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidSizes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(builder -> builder.video(Video.builder().build())),
                        givenImp(builder -> builder.banner(Banner.builder()
                                .format(singletonList(Format.builder().w(123).h(456).build()))
                                .build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).extracting(BidderError::getMessage).containsOnly("No valid sizes");
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfResponseStatusIs204() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, 204, null);

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseStatusIsNot200Or204() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, 302, null);

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("Unexpected status code: 302. Run with request.test = 1 for more info");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, 200, "invalid");

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unrecognized token");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasNoVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                200, givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfRequestImpHasVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.video(Video.builder().build())),
                200, givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldNotReturnImpIfPriceLessOrEqualToZero() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                200, givenBidResponse(ZERO));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldProcessMetricsAndFillViewabilityVendor() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.video(Video.builder().build())
                        .metric(asList(Metric.builder().vendor("somebody").type("viewability").value(0.9f).build(),
                                Metric.builder().vendor("moat").type("viewability").value(0.3f).build(),
                                Metric.builder().vendor("comscore").type("unsupported").value(0.5f).build(),
                                Metric.builder().vendor("activeview").type("viewability").value(0.6f).build(),
                                Metric.builder().vendor("somebody").type("unsupported").value(0.7f).build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .flatExtracting(Imp::getMetric).doesNotContainNull()
                .containsOnly(Metric.builder().type("viewability").value(0.9f).vendor("somebody").build(),
                        Metric.builder().type("viewability").value(0.3f).vendor("seller-declared").build(),
                        Metric.builder().type("unsupported").value(0.5f).vendor("comscore").build(),
                        Metric.builder().type("viewability").value(0.6f).vendor("seller-declared").build(),
                        Metric.builder().type("unsupported").value(0.7f).vendor("somebody").build());
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getExt).doesNotContainNull()
                .extracting(ext -> mapper.treeToValue(ext, RubiconImpExt.class))
                .containsOnly(RubiconImpExt.of(RubiconImpExtRp.of(null,
                        NullNode.getInstance(),
                        RubiconImpExtRpTrack.of("", "")), asList("moat.com", "doubleclickbygoogle.com")));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForEmptyExtension() {
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode())).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForInvalidExtension() {
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().put("rp", 1))).isEmpty();
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().putObject("rp").put("targeting", 1)))
                .isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForNullRp() {
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().putObject("rp"))).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnEmptyMapForNullTargeting() {
        assertThat(rubiconBidder.extractTargeting(mapper.createObjectNode().putObject("rp").putObject("targeting")))
                .isEmpty();
    }

    @Test
    public void extractTargetingShouldIgnoreEmptyTargetingValuesList() {
        // given
        final ObjectNode extBidBidder = mapper.valueToTree(RubiconTargetingExt.of(
                RubiconTargetingExtRp.of(singletonList(RubiconTargeting.of("rpfl_1001", emptyList())))));

        // when and then
        assertThat(rubiconBidder.extractTargeting(extBidBidder)).isEmpty();
    }

    @Test
    public void extractTargetingShouldReturnNotEmptyTargetingMap() {
        // given
        final ObjectNode extBidBidder = mapper.valueToTree(RubiconTargetingExt.of(
                RubiconTargetingExtRp.of(singletonList(
                        RubiconTargeting.of("rpfl_1001", asList("2_tier0100", "3_tier0100"))))));

        // when and then
        assertThat(rubiconBidder.extractTargeting(extBidBidder)).containsOnly(entry("rpfl_1001", "2_tier0100"));
    }

    private static BidRequest givenBidRequest(Function<BidRequestBuilder, BidRequestBuilder> bidRequestCustomizer,
                                              Function<ImpBuilder, ImpBuilder> impCustomizer,
                                              Function<ExtImpRubiconBuilder, ExtImpRubiconBuilder> extCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<ImpBuilder, ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(Function<ImpBuilder, ImpBuilder> impCustomizer,
                                              Function<ExtImpRubiconBuilder, ExtImpRubiconBuilder> extCustomizer) {
        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impCustomizer,
                                Function<ExtImpRubiconBuilder, ExtImpRubiconBuilder> extCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, extCustomizer.apply(ExtImpRubicon.builder()).build()))))
                .build();
    }

    private static Imp givenImp(Function<ImpBuilder, ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, identity());
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, int statusCode, String body) {
        return HttpCall.full(
                HttpRequest.of(null, null, null, null, bidRequest),
                HttpResponse.of(statusCode, null, body),
                null);
    }

    private static String givenBidResponse(BigDecimal price) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .price(price)
                                .build()))
                        .build()))
                .build());
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static final class Inventory {

        List<String> rating;

        List<String> prodtype;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static final class Visitor {

        List<String> ucat;

        List<String> search;
    }
}
