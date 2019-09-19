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
import com.iab.openrtb.request.Geo;
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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.rubicon.proto.RubiconAppExt;
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
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestRubicon;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestRubiconDebug;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEidUidExt;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubicon.ExtImpRubiconBuilder;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtImpRubiconDebug;
import org.prebid.server.proto.openrtb.ext.request.rubicon.RubiconVideoParams;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class RubiconBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://rubiconproject.com/exchange.json?trk=prebid";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final List<String> SUPPORTED_VENDORS = Arrays.asList("activeview", "adform",
            "comscore", "doubleverify", "integralads", "moat", "sizmek", "whiteops");

    private RubiconBidder rubiconBidder;

    @Before
    public void setUp() {
        rubiconBidder = new RubiconBidder(ENDPOINT_URL, USERNAME, PASSWORD, SUPPORTED_VENDORS);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RubiconBidder("invalid_url", USERNAME, PASSWORD, SUPPORTED_VENDORS));
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
                        tuple(HttpUtil.AUTHORIZATION_HEADER.toString(), "Basic dXNlcm5hbWU6cGFzc3dvcmQ="),
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "prebid-server/1.0"));
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
    public void makeHttpRequestsShouldOverrideBannerFormatWithRubiconSizes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(
                                        Format.builder().w(300).h(250).build(),
                                        Format.builder().w(300).h(600).build()))
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRubicon.builder()
                                .sizes(singletonList(15)).build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner).doesNotContainNull()
                .flatExtracting(Banner::getFormat).hasSize(1)
                .containsOnly(Format.builder().w(300).h(250).build());
    }

    @Test
    public void makeHttpRequestsShouldCreateBannerRequestIfImpHasBannerAndVideoButNoRequiredVideoFieldsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsOnly(tuple(
                        Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(250).build()))
                                .ext(mapper.valueToTree(
                                        RubiconBannerExt.of(RubiconBannerExtRp.of(15, null, "text/html"))))
                                .build(),
                        null)); // video is removed
    }

    @Test
    public void makeHttpRequestsShouldCreateVideoRequestIfImpHasBannerAndVideoButAllRequiredVideoFieldsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build())
                        .video(Video.builder().mimes(singletonList("mime1")).protocols(singletonList(1))
                                .maxduration(60).linearity(2).api(singletonList(3)).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).doesNotContainNull()
                .extracting(Imp::getBanner, Imp::getVideo)
                .containsOnly(tuple(
                        null, // banner is removed
                        Video.builder().mimes(singletonList("mime1")).protocols(singletonList(1))
                                .maxduration(60).linearity(2).api(singletonList(3)).build()));
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
                        .ext(mapper.valueToTree(RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.of(mapper.valueToTree(
                                        Visitor.of(singletonList("new"), singletonList("iphone"))), null, null, null))
                                .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldNotFillUserExtRpWhenVisitorAndInventoryIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().id("id").build()),
                builder -> builder.video(Video.builder().build()),
                builder -> builder
                        .visitor(mapper.createObjectNode())
                        .inventory(mapper.createObjectNode()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .containsOnly(User.builder().id("id").build());
    }

    @Test
    public void makeHttpRequestsShouldFillUserExtIfUserAndDigiTrustPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(
                        mapper.valueToTree(ExtUser.builder()
                                .digitrust(ExtUserDigiTrust.of("id", 123, 0))
                                .build()))
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
                        .ext(mapper.valueToTree(RubiconUserExt.builder()
                                .digitrust(ExtUserDigiTrust.of("id", 123, 0))
                                .build()))
                        .build());
    }

    @Test
    public void makeHttpRequestShouldFillUserIfUserAndConsentArePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder().ext(
                        mapper.valueToTree(ExtUser.builder().consent("consent").build()))
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
                        .ext(mapper.valueToTree(RubiconUserExt.builder().consent("consent").build()))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldCopyUserKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.user(User.builder().keywords("user keyword").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords)
                .containsOnly("user keyword");
    }

    @Test
    public void makeHttpRequestShouldCopyUserGenderYobAndGeoToUserExtRp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .gender("M")
                        .yob(2000)
                        .geo(Geo.builder().build())
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
                .extracting(User::getExt)
                .containsOnly(mapper.valueToTree(RubiconUserExt.builder()
                        .rp(RubiconUserExtRp.of(null, "M", 2000, Geo.builder().build()))
                        .build()));
    }

    @Test
    public void makeHttpRequestShouldCopyUserExtDataFieldsToUserExtRp() {
        // given
        final ObjectNode userExtDataNode = mapper.createObjectNode().put("property", "value");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder().data(userExtDataNode).build()))
                        .gender("M").build()),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedNode = mapper.createObjectNode();
        userExtDataNode.put("gender", "M");
        expectedNode.set("rp", userExtDataNode);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser).doesNotContainNull()
                .extracting(User::getExt)
                .containsOnly(expectedNode);
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
                .startsWith("Error decoding bidRequest.user.ext: Cannot deserialize instance of `java.lang.String`");
        assertThat(result.getValue()).isEmpty();
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
                        .ext(mapper.valueToTree(RubiconUserExt.builder()
                                .rp(RubiconUserExtRp.of(mapper.valueToTree(
                                        Visitor.of(singletonList("new"), singletonList("iphone"))), null, null, null))
                                .build()))
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
    public void shouldCreateUserExtTpIdWithAdServerEidSource() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(
                                                ExtUserEidUid.of("adServerUid", ExtUserEidUidExt.of("TDID"))))))
                                .build()))
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> mapper.treeToValue(request.getUser().getExt(), RubiconUserExt.class))
                .containsOnly(RubiconUserExt.builder()
                        .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                singletonList(ExtUserEidUid.of("adServerUid", ExtUserEidUidExt.of("TDID"))))))
                        .tpid(singletonList(ExtUserTpIdRubicon.of("tdid", "adServerUid")))
                        .build());
    }

    @Test
    public void shouldNotCreateUserExtTpIdWithAdServerEidSourceIfEidUidExtMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(ExtUserEidUid.of("id", null)))))
                                .build()))
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> mapper.treeToValue(request.getUser().getExt(), RubiconUserExt.class))
                .containsOnly(RubiconUserExt.builder()
                        .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                singletonList(ExtUserEidUid.of("id", null)))))
                        .tpid(null)
                        .build());
    }

    @Test
    public void shouldNotCreateUserExtTpIdWithAdServerEidSourceIfExtRtiPartnerMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                        singletonList(ExtUserEidUid.of("id", ExtUserEidUidExt.of(null))))))
                                .build()))
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> mapper.treeToValue(request.getUser().getExt(), RubiconUserExt.class))
                .containsOnly(RubiconUserExt.builder()
                        .eids(singletonList(ExtUserEid.of("adserver.org", null,
                                singletonList(ExtUserEidUid.of("id", ExtUserEidUidExt.of(null))))))
                        .tpid(null)
                        .build());
    }

    @Test
    public void shouldNotCreateUserExtTpIdWithUnknownEidSource() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .eids(singletonList(ExtUserEid.of("unknownSource", null,
                                        singletonList(ExtUserEidUid.of("id", ExtUserEidUidExt.of("eidUidId"))))))
                                .build()))
                        .build()),
                builder -> builder.video(Video.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(request -> mapper.treeToValue(request.getUser().getExt(), RubiconUserExt.class))
                .containsOnly(RubiconUserExt.builder()
                        .eids(singletonList(ExtUserEid.of("unknownSource", null,
                                singletonList(ExtUserEidUid.of("id", ExtUserEidUidExt.of("eidUidId"))))))
                        .tpid(null)
                        .build());
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
        rp.set("rp", mapper.createObjectNode().put("pixelratio", Double.valueOf("4.2")));

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
                        .ext(mapper.valueToTree(RubiconSiteExt.of(RubiconSiteExtRp.of(3001), null)))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldPassSiteExtAmpIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.site(Site.builder().ext(Json.mapper.valueToTree(ExtSite.of(1, null))).build()),
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
                        .ext(mapper.valueToTree(RubiconSiteExt.of(RubiconSiteExtRp.of(3001), 1)))
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
                        .ext(mapper.valueToTree(RubiconAppExt.of(RubiconSiteExtRp.of(3001))))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldSuppressCurrenciesIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.cur(singletonList("ANY")),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getCur).hasSize(1).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSuppressExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder().build(), null))),
                builder -> builder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull()
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getExt).hasSize(1).containsNull();
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
    public void makeHttpRequestsShouldCopyImpExtContextDataFieldsToRubiconImpExtRpTarget() {
        // given
        final ObjectNode impExtContextDataNode = mapper.createObjectNode().put("property", "value");
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(null, null, impExtContextDataNode)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(impExtContextDataNode);
    }

    @Test
    public void makeHttpRequestsShouldCopySiteExtDataFieldsToRubiconImpExtRpTarget() {
        // given
        final ObjectNode siteExtDataNode = mapper.createObjectNode().put("property", "value");
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().ext(mapper.valueToTree(ExtSite.of(0, siteExtDataNode))).build())
                        .ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(siteExtDataNode);
    }

    @Test
    public void makeHttpRequestsShouldCopyAppExtDataFieldsToRubiconImpExtRpTarget() {
        // given
        final ObjectNode appExtDataNode = mapper.createObjectNode().put("property", "value");
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .app(App.builder().ext(mapper.valueToTree(ExtApp.of(null, appExtDataNode))).build())
                        .ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(appExtDataNode);
    }

    @Test
    public void makeHttpRequestsShouldCopySiteExtDataAndImpExtContextDataFieldsToRubiconImpExtRpTarget()
            throws IOException {
        // given
        final ObjectNode siteExtDataNode = mapper.createObjectNode().put("site", "value1");
        final ObjectNode impExtContextDataNode = mapper.createObjectNode().put("imp_ext", "value2");

        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().ext(mapper.valueToTree(ExtSite.of(0, siteExtDataNode))).build())
                        .ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(null, null, impExtContextDataNode)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"imp_ext\":\"value2\",\"site\":\"value1\"}"));
    }

    @Test
    public void makeHttpRequestsShouldCopyAppExtDataAndImpExtContextDataFieldsToRubiconImpExtRpTarget()
            throws IOException {
        // given
        final ObjectNode appExtDataNode = mapper.createObjectNode().put("app", "value1");
        final ObjectNode impExtContextDataNode = mapper.createObjectNode().put("imp_ext", "value2");

        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .app(App.builder().ext(mapper.valueToTree(ExtApp.of(null, appExtDataNode))).build())
                        .ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(null, null, impExtContextDataNode)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"imp_ext\":\"value2\",\"app\":\"value1\"}"));
    }

    @Test
    public void makeHttpRequestsShouldCopyImpExtContextKeywordsToRubiconImpExtRpTargetKeywords() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of("imp ext context keywords", null, null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"keywords\":\"imp ext context keywords\"}"));
    }

    @Test
    public void makeHttpRequestsShouldCopyImpExtContextSearchToRubiconImpExtRpTargetSearch() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(null, "imp ext search", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"search\":\"imp ext search\"}"));
    }

    @Test
    public void makeHttpRequestsShouldCopySiteSearchToRubiconImpExtRpTargetSearch() throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().search("site search").build())
                        .ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"search\":\"site search\"}"));
    }

    @Test
    public void makeHttpRequestsShouldTakePrecedenceImpExtContextSearchOverSiteSearchAndCopyToRubiconImpExtRpTarget()
            throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().search("site search").build())
                        .ext(givenExtBidRequestWithRubiconFirstPartyData()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        final ObjectNode impExt = bidRequest.getImp().get(0).getExt();
        impExt.set("context", mapper.valueToTree(ExtImpContext.of(null, "imp ext search", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(objectNode -> mapper.convertValue(objectNode, RubiconImpExt.class))
                .extracting(RubiconImpExt::getRp)
                .extracting(RubiconImpExtRp::getTarget)
                .containsOnly(mapper.readTree("{\"search\":\"imp ext search\"}"));
    }

    @Test
    public void makeHttpRequestsShouldCopySiteKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.site(Site.builder().keywords("site keyword").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords)
                .containsOnly("site keyword");
    }

    @Test
    public void makeHttpRequestsShouldCopyAppKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder.app(App.builder().keywords("app keyword").build()),
                impBuilder -> impBuilder.video(Video.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getApp)
                .extracting(App::getKeywords)
                .containsOnly("app keyword");
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
    public void makeHttpRequestsShouldReturnErrorIfNoImpFormat() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(builder -> builder.video(Video.builder().build())),
                        givenImp(builder -> builder.banner(Banner.builder()
                                .format(null).w(300).h(250)
                                .build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .containsOnly(BidderError.badInput("rubicon imps must have at least one imp.format element"));
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
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("No valid sizes"));
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSizeIdsNotFound() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpRubicon.builder()
                                .sizes(singletonList(3)).build())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rubiconBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Bad request.imp[].ext.rubicon.sizes"));
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
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasNoVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasBannerAndVideoButNoRequiredVideoFieldsPresent()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder
                        .banner(Banner.builder().build())
                        .video(Video.builder().build())),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfRequestImpHasBannerAndVideoButAllRequiredVideoFieldsPresent()
            throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder
                        .banner(Banner.builder().build())
                        .video(Video.builder().mimes(singletonList("mime1")).protocols(singletonList(1))
                                .maxduration(60).linearity(2).api(singletonList(3)).build())),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfRequestImpHasVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.video(Video.builder().build())),
                givenBidResponse(ONE));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(ONE).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldNotReturnImpIfPriceLessOrEqualToZero() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()),
                givenBidResponse(ZERO));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, givenBidRequest(identity()));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithPriceFromCpmOverrideInRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(mapper.valueToTree(
                ExtBidRequest.of(null, ExtRequestRubicon.of(ExtRequestRubiconDebug.of(5.015f))))),
                builder -> builder.video(Video.builder().build()),
                identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(ZERO));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().price(BigDecimal.valueOf(5.015f)).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidWithPriceFromCpmOverrideInImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.id("impId1"),
                builder -> builder.debug(ExtImpRubiconDebug.of(5.015f)));

        final String bidResponse = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("impId1")
                                .price(ZERO)
                                .build()))
                        .build()))
                .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), bidResponse);

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("impId1").price(BigDecimal.valueOf(5.015f)).build(),
                        banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidWithPriceFromCpmOverrideInImpOverRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(mapper.valueToTree(
                ExtBidRequest.of(null, ExtRequestRubicon.of(ExtRequestRubiconDebug.of(1.048f))))),
                builder -> builder.id("impId1"),
                builder -> builder.debug(ExtImpRubiconDebug.of(5.015f)));

        final String bidResponse = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder()
                                .impid("impId1")
                                .price(ZERO)
                                .build()))
                        .build()))
                .build());

        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), bidResponse);

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("impId1").price(BigDecimal.valueOf(5.015f)).build(),
                        banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidWithBidIdFieldFromBidResponseIfZero() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString((BidResponse.builder()
                        .bidid("bidid1") // returned bidid from XAPI
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("0").price(ONE).build()))
                                .build()))
                        .build())));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().id("bidid1").price(ONE).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidWithOriginalBidIdFieldFromBidResponseIfNotZero() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString((BidResponse.builder()
                        .bidid("bidid1") // returned bidid from XAPI
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("non-zero").price(ONE).build()))
                                .build()))
                        .build())));

        // when
        final Result<List<BidderBid>> result = rubiconBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().id("non-zero").price(ONE).build(), banner, "USD"));
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

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
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

    private static ObjectNode givenExtBidRequestWithRubiconFirstPartyData() {
        return mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                .data(ExtRequestPrebidData.of(singletonList("rubicon")))
                .build(), null));
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
