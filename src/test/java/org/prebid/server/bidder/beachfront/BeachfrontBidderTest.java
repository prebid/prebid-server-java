package org.prebid.server.bidder.beachfront;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.beachfront.model.BeachfrontBannerRequest;
import org.prebid.server.bidder.beachfront.model.BeachfrontRequests;
import org.prebid.server.bidder.beachfront.model.BeachfrontResponseSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontSize;
import org.prebid.server.bidder.beachfront.model.BeachfrontSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoDevice;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoImp;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class BeachfrontBidderTest extends VertxTest {

    private BeachfrontBidder beachfrontBidder;

    @Before
    public void setUp() {
        beachfrontBidder = new BeachfrontBidder("http://banner-beachfront.com",
                "http://video-beachfront.com?exchange_id=");
    }

    @Test
    public void makeHttpRequestsShouldReturnVideoRequestWhenAtLeastOneImpIsVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().banner(Banner.builder().build()).build(),
                        Imp.builder().video(Video.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appId", 1f))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BeachfrontRequests::getVideoRequest)
                .isNotNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnPopulatedVideoRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").video(Video.builder().w(300).h(400).build())
                        .secure(1)
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .user(User.builder().id("userId").buyeruid("buyerId").build())
                .device(Device.builder().ua("ua").ip("127.0.0.1").build())
                .app(App.builder().domain("appDomain").id("appId").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsOnly(
                "http://video-beachfront.com?exchange_id=appIdExt&prebidserver");

        assertThat(result.getValue()).
                flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));

        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(Json.mapper.writeValueAsString(
                BeachfrontVideoRequest.builder().isPrebid(true).appId("appIdExt")
                        .imp(singletonList(BeachfrontVideoImp.of(BeachfrontSize.of(300, 400), 1f, 0, "impId", 1)))
                        .site(Site.builder().domain("appDomain").page("appId").build())
                        .device(BeachfrontVideoDevice.of("ua", "127.0.0.1", "1"))
                        .user(User.builder().id("userId").buyeruid("buyerId").build())
                        .cur(singletonList("USD"))
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldSetSecuredZeroForVideoImpIfNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .flatExtracting(BeachfrontVideoRequest::getImp)
                .extracting(BeachfrontVideoImp::getSecure)
                .containsOnly(0);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageIfNoValidVideoImpInReasonOfMissingExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build()).ext(null).build())).build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("Beachfront parameters section is missing"),
                BidderError.badInput("No valid impressions were found"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageIfNoValidVideoImpInReasonOfNotValidExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").video(Video.builder().build()).ext(Json.mapper.createObjectNode()
                        .put("bidder", 4))
                        .build())).build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("ignoring imp id=impId, error while decoding"
                        + " impExt, err: Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request."
                        + "beachfront.ExtImpBeachfront` (although at least one Creator exists): no int/Int-argument"
                        + " constructor/factory method to deserialize from Number value (4)\n"
                        + " at [Source: UNKNOWN; line: -1, column: -1] (through reference chain:"
                        + " org.prebid.server.proto.openrtb.ext.ExtPrebid[\"bidder\"])"),
                BidderError.badInput("No valid impressions were found"));
    }

    @Test
    public void makeHttpRequestsShouldTakeDomainFromAppWhenAppAndSiteBothPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .app(App.builder().domain("appDomain").id("appId").build())
                .site(Site.builder().domain("siteDomain").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getSite)
                .extracting(Site::getDomain)
                .containsOnly("appDomain");
    }

    @Test
    public void makeHttpRequestsShouldTakeDomainFromSiteDomainIfAppIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .site(Site.builder().page("http://domain.com/list").domain("domain.com").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getSite)
                .extracting(Site::getDomain)
                .containsOnly("domain.com");
    }

    @Test
    public void makeHttpRequestsShouldTakeDomainSitePageIfAppAndPageDomainAreAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .site(Site.builder().page("http://domain.com/list").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getSite)
                .extracting(Site::getDomain)
                .containsOnly("domain.com");
    }

    @Test
    public void makeHttpRequestsShouldNotSetVideoRequestSiteIfBothAppAndSiteAreAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getSite)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnVideoRequestWithAppIdFromLastVideoImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().video(Video.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt1", 1f))))
                                .build(),
                        Imp.builder().video(Video.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt2", 1f))))
                                .build()))
                .site(Site.builder().page("http://domain.com/list").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getAppId)
                .containsOnly("appIdExt2");
    }

    @Test
    public void makeHttpRequestsShouldReturnVideoRequestWithTwoBidsAndError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").video(Video.builder().w(300).h(400).build()).secure(1)
                                .ext(Json.mapper.valueToTree(Json.mapper.createObjectNode().put("bidder", 4)))
                                .build(),
                        Imp.builder().id("impId2").video(Video.builder().w(100).h(200).build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt1", 1f))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badInput("ignoring imp id=impId1, error while decoding"
                + " impExt, err: Cannot construct instance of `org.prebid.server.proto.openrtb.ext.request."
                + "beachfront.ExtImpBeachfront` (although at least one Creator exists): no int/Int-argument"
                + " constructor/factory method to deserialize from Number value (4)\n"
                + " at [Source: UNKNOWN; line: -1, column: -1] (through reference chain:"
                + " org.prebid.server.proto.openrtb.ext.ExtPrebid[\"bidder\"])"));

        assertThat(result.getValue())
                .extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .containsOnly(BeachfrontVideoRequest.builder().isPrebid(true).appId("appIdExt1")
                        .imp(asList(BeachfrontVideoImp.of(BeachfrontSize.of(300, 400), null, null, null, 1),
                                BeachfrontVideoImp.of(BeachfrontSize.of(100, 200), 1.0f, 1, "impId2", 0)))
                        .cur(singletonList("USD")).build());
    }

    @Test
    public void makeHttpRequestsShouldReturnVideoRequestWithoutErrorsWhenAudioAndNativeImpsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().audio(Audio.builder().build()).build(),
                        Imp.builder().xNative(Native.builder().build()).build(),
                        Imp.builder().id("impId2").video(Video.builder().w(100).h(200).build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt1", 1f))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getImp)
                .hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWithoutRequestWhenAudioImpPresentWithoutVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").audio(Audio.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("Beachfront doesn't support audio Imps. Ignoring Imp ID=impId",
                        "No valid impressions were found");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWithoutRequestWhenNativeImpPresentWithoutVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").xNative(Native.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("Beachfront doesn't support native Imps. Ignoring Imp ID=impId",
                        "No valid impressions were found");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsWithoutRequestWhenNativeAndAudioImpsPresentWithoutVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").xNative(Native.builder().build()).build(),
                        Imp.builder().id("impId2").audio(Audio.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("Beachfront doesn't support audio Imps. Ignoring Imp ID=impId2",
                        "Beachfront doesn't support native Imps. Ignoring Imp ID=impId1",
                        "No valid impressions were found");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnBannerRequestWithPopulatedFields() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("request id")
                .imp(singletonList(Imp.builder().id("impId1").bidfloor(BigDecimal.valueOf(1.0)).banner(Banner.builder().format(asList(
                        Format.builder().w(100).h(200).build(), Format.builder().w(300).h(400).build())).build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .secure(1).build()))
                .device(Device.builder().ip("127.0.0.1").model("model").os("os").dnt(5).ua("ua").build())
                .user(User.builder().buyeruid("buyeruid").id("userId").build())
                .app(App.builder().domain("rubicon.com").id("appId").build())

                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsOnly(
                "http://banner-beachfront.com");

        assertThat(result.getValue()).
                flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsExactly(Json.mapper.writeValueAsString(
                BeachfrontBannerRequest.builder()
                        .slots(singletonList(BeachfrontSlot.of("impId1", "appIdExt", BigDecimal.valueOf(1.0),
                                asList(BeachfrontSize.of(100, 200), BeachfrontSize.of(300, 400)))))
                        .domain("rubicon.com").page("appId").deviceOs("os").deviceModel("model")
                        .ua("ua").dnt(5).user(User.builder().id("userId").buyeruid("buyeruid").build())
                        .adapterName("BF_PREBID_S2S").adapterVersion("0.2.2")
                        .ip("127.0.0.1").secure(1).requestId("request id")
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldNotSetBannerRequestUserIfUserIsAbsent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontBannerRequest.class))
                .extracting(BeachfrontBannerRequest::getUser)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerRequestSecureFromLastImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                                .secure(1).build(),
                        Imp.builder().banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                                .secure(0).build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontBannerRequest.class))
                .extracting(BeachfrontBannerRequest::getSecure)
                .containsOnly(0);
    }

    @Test
    public void makeHttpRequestsShouldNotSetBannerRequestSecureIfNoSecureInImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody)
                .extracting(s -> Json.mapper.readValue(s, BeachfrontBannerRequest.class))
                .extracting(BeachfrontBannerRequest::getSecure)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageWithoutRequestIfSingleBannerImpIsInvalid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId").banner(Banner.builder().build())
                        .ext(Json.mapper.createObjectNode().put("bidder", 5))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("ignoring imp id=impId, error while decoding impExt, err: Cannot construct instance of"
                                + " `org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront`"
                                + " (although at least one Creator exists): no int/Int-argument constructor/factory"
                                + " method to deserialize from Number value (5)\n"
                                + " at [Source: UNKNOWN; line: -1, column: -1] (through reference chain:"
                                + " org.prebid.server.proto.openrtb.ext.ExtPrebid[\"bidder\"])",
                        "No valid impressions were found");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorMessageWithRequestWhenInvalidAndValidBannerImpPresent()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").banner(Banner.builder()
                        .format(singletonList(Format.builder().w(100).h(200).build())).build())
                        .bidfloor(BigDecimal.valueOf(1.0))
                        .ext(Json.mapper.createObjectNode().put("bidder", 5))
                        .build(), Imp.builder().id("impId2").banner(Banner.builder()
                        .format(singletonList(Format.builder().w(200).h(300).build())).build())
                        .bidfloor(BigDecimal.valueOf(2))
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f)))).build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("ignoring imp id=impId1, error while decoding impExt, err: Cannot construct instance of"
                        + " `org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront`"
                        + " (although at least one Creator exists): no int/Int-argument constructor/factory"
                        + " method to deserialize from Number value (5)\n"
                        + " at [Source: UNKNOWN; line: -1, column: -1] (through reference chain:"
                        + " org.prebid.server.proto.openrtb.ext.ExtPrebid[\"bidder\"])");

        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsOnly(Json.mapper.writeValueAsString(
                BeachfrontBannerRequest.builder()
                        .slots(asList(
                                BeachfrontSlot.of(null, null, BigDecimal.valueOf(1.0),
                                        singletonList(BeachfrontSize.of(100, 200))),
                                BeachfrontSlot.of("impId2", "appIdExt", BigDecimal.valueOf(2),
                                        singletonList(BeachfrontSize.of(200, 300)))))
                        .adapterName("BF_PREBID_S2S")
                        .adapterVersion("0.2.2")
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldReturnBannerRequestWithoutSizeWhenFormatIsEmpty()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build())).build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsOnly(Json.mapper.writeValueAsString(
                BeachfrontBannerRequest.builder()
                        .slots(singletonList(BeachfrontSlot.of(null, "appIdExt", null,
                                singletonList(BeachfrontSize.of(null, null)))))
                        .adapterName("BF_PREBID_S2S")
                        .adapterVersion("0.2.2")
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldReturnDomainAndPageFromAppWhenAppAndSiteAreBothPresent()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .app(App.builder().domain("rubiconapp.com").id("appId").build())
                .site(Site.builder().page("http://rubiconpage.com/adunits").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsOnly(Json.mapper.writeValueAsString(
                BeachfrontBannerRequest.builder().slots(singletonList(BeachfrontSlot.of(null, "appIdExt", null,
                        singletonList(BeachfrontSize.of(null, null))))).domain("rubiconapp.com").page("appId").adapterName("BF_PREBID_S2S")
                        .adapterVersion("0.2.2").build()));
    }

    @Test
    public void makeHttpRequestsShouldReturnBannerRequestWithDomainAndPageFromSite() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f))))
                        .build()))
                .site(Site.builder().page("http://rubiconpage.com/adunits").build())
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).extracting(HttpRequest::getBody).containsOnly(Json.mapper.writeValueAsString(
                BeachfrontBannerRequest.builder().slots(singletonList(BeachfrontSlot.of(null, "appIdExt", null,
                        singletonList(BeachfrontSize.of(null, null))))).domain("rubiconpage.com")
                        .page("http://rubiconpage.com/adunits").adapterName("BF_PREBID_S2S").adapterVersion("0.2.2")
                        .build()));
    }

    @Test
    public void makeHttpRequestsShouldReturnBannerRequestWithErrorWhenNativeAndAudioArePresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpBeachfront.of("appIdExt", 1f)))).build(),
                        Imp.builder().id("impId2").audio(Audio.builder().build()).build(),
                        Imp.builder().id("impId3").xNative(Native.builder().build()).build()))
                .build();

        // when
        final Result<List<HttpRequest<BeachfrontRequests>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("Beachfront doesn't support audio Imps. Ignoring Imp ID=impId2",
                        "Beachfront doesn't support native Imps. Ignoring Imp ID=impId3");
        assertThat(result.getValue()).isNotEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsFromVideoResponseWithUpdatedFields() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().seatbid(singletonList(
                SeatBid.builder().bid(singletonList(Bid.builder().h(100).w(200).id("bidId")
                        .price(BigDecimal.ONE).nurl("\"nurl1\"nurl2\"nurl3").impid("impId").crid("crid")
                        .build())).build())).build());

        final BeachfrontVideoRequest beachfrontVideoRequest = BeachfrontVideoRequest.builder()
                .imp(singletonList(BeachfrontVideoImp.of(BeachfrontSize.of(300, 400), null, null, "impIdReq", 1))).build();

        final BidRequest bidRequest = BidRequest.builder().id("bidRequestId")
                .imp(singletonList(Imp.builder().video(Video.builder().build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, beachfrontVideoRequest);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .id("bidRequestId").impid("impIdReq").price(BigDecimal.ONE).w(300).h(400).crid("nurl2")
                .nurl("\"nurl1\"nurl2\"nurl3").build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnEmptyBidsListWhenSeatBidIsNullForVideoResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build());

        final BeachfrontVideoRequest beachfrontVideoRequest = BeachfrontVideoRequest.builder()
                .imp(singletonList(BeachfrontVideoImp.of(BeachfrontSize.of(300, 400), null, null, "impIdReq", 1))).build();

        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().video(Video.builder()
                .build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, beachfrontVideoRequest);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).extracting(BidderBid::getBid).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidsOnlyFromFirstSeatBidForVideoResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().seatbid(asList(
                SeatBid.builder().bid(singletonList(Bid.builder().build())).build(),
                SeatBid.builder().bid(singletonList(Bid.builder().build())).build())).build());

        final BeachfrontVideoRequest beachfrontVideoRequest = BeachfrontVideoRequest.builder()
                .imp(singletonList(BeachfrontVideoImp.of(BeachfrontSize.of(300, 400), null, null, null, 0))).build();

        final BidRequest bidRequest = BidRequest.builder().id("bidRequestId")
                .imp(singletonList(Imp.builder().video(Video.builder().build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, beachfrontVideoRequest);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .id("bidRequestId").w(300).h(400).build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBidsWithNullCridWhenNurlParsingThrowsExceptionForVideoResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().seatbid(singletonList(
                SeatBid.builder().bid(singletonList(Bid.builder().nurl("notParsed").build())).build())).build());

        final BeachfrontVideoRequest beachfrontVideoRequest = BeachfrontVideoRequest.builder()
                .imp(singletonList(BeachfrontVideoImp.of(BeachfrontSize.of(300, 400), null, null, null, 0))).build();

        final BidRequest bidRequest = BidRequest.builder().id("bidRequestId")
                .imp(singletonList(Imp.builder().video(Video.builder().build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, beachfrontVideoRequest);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder()
                .id("bidRequestId").w(300).h(400).crid(null).nurl("notParsed").build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyIsNull() {
        // given
        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(null, null);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsOnly("Received a null response from beachfront");
    }

    @Test
    public void makeBidsShouldReturnPopulatedBannerBid() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(singletonList(BeachfrontResponseSlot.builder()
                .adm("crid1\"crid2\"crid3").h(200).w(300).price(1.0f).slot("slotId").build()));

        final BidRequest bidRequest = BidRequest.builder().id("reqId").imp(singletonList(
                Imp.builder().banner(Banner.builder().build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, null);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().id("reqId").impid("slotId")
                        .price(BigDecimal.valueOf(1.0f)).adm("crid1\"crid2\"crid3").crid("crid2").w(300).h(200).build(),
                BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorsWithoutBidsWhenBannerResponseCantBeDecoded() {
        // given
        final String response = Json.mapper.createObjectNode().put("w", "invalid").toString();

        final BidRequest bidRequest = BidRequest.builder().id("reqId").imp(singletonList(
                Imp.builder().banner(Banner.builder().build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, null);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).extracting(BidderError::getMessage)
                .containsOnly("Cannot deserialize value of type `java.lang.Integer` from String \"invalid\": not a"
                        + " valid Integer value\n at [Source: (String)\"{\"w\":\"invalid\"}\"; line: 1, column: 6] "
                        + "(through reference chain: java.lang.Object[0]->org.prebid.server.bidder.beachfront.model."
                        + "BeachfrontResponseSlot[\"w\"])", "Failed to process the beachfront response");
    }

    @Test
    public void makeBidsShouldReturnBannerBidsWithNullCridWhenAdmDoNoHaveSecondElement()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(singletonList(BeachfrontResponseSlot.builder()
                .adm("crid1").build()));

        final BidRequest bidRequest = BidRequest.builder().id("reqId").imp(singletonList(
                Imp.builder().banner(Banner.builder().build()).build())).build();

        final HttpCall<BeachfrontRequests> httpCall = givenHttpCall(response, null);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(Bid.builder().id("reqId")
                .adm("crid1").crid(null).build(), BidType.banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        // given, when and then
        assertThat(beachfrontBidder.extractTargeting(Json.mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static HttpCall<BeachfrontRequests> givenHttpCall(String body, BeachfrontVideoRequest
            beachfrontVideoRequest) {
        return HttpCall.success(
                HttpRequest.<BeachfrontRequests>builder()
                        .method(HttpMethod.POST)
                        .body(body)
                        .payload(BeachfrontRequests.of(null, beachfrontVideoRequest))
                        .build(),
                HttpResponse.of(200, null, body), null);
    }
}
