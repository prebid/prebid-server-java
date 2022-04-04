package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticBidderImpExt;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticExtData;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticExtDataAdServer;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticWrapper;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticBidExt;
import org.prebid.server.bidder.pubmatic.model.response.VideoCreativeInfo;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class PubmaticBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.endpoint.com/translator?source=prebid-server";

    private PubmaticBidder pubmaticBidder;

    @Before
    public void setUp() {
        pubmaticBidder = new PubmaticBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PubmaticBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateBidfloorIfImpExtKadfloorIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .bidfloor(BigDecimal.TEN)
                .ext(givenExtImpWithKadfloor("invalid")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.TEN);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidfloorIfImpExtKadfloorIsValid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(givenExtImpWithKadfloor("12.5")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(12.5));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnBidfloorIfImpExtKadfloorIsValidAndResolvedWhitespace() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder.ext(givenExtImpWithKadfloor("  12.5  ")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(12.5));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfBidRequestExtWrapperIsInvalid() {
        // given
        final ObjectNode wrapperNode = mapper.createObjectNode()
                .set("wrapper", mapper.createObjectNode().set("version", TextNode.valueOf("invalid")));
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder().bidderparams(wrapperNode).build());

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(bidderError -> bidderError.getType().equals(BidderError.Type.bad_input)
                        && bidderError.getMessage().startsWith("Cannot deserialize value"));
    }

    @Test
    public void makeHttpRequestsShouldUseWrapperFromBidRequestExtIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(givenUpdatedBidRequestExt(1, 1)), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(givenUpdatedBidRequestExt(1, 1));
    }

    @Test
    public void makeHttpRequestsShouldMergeWrappersFromImpAndBidRequestExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(givenInitialBidRequestExt(123, null)),
                identity(),
                extBuilder -> extBuilder.wrapper(PubmaticWrapper.of(321, 456)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(givenUpdatedBidRequestExt(123, 456));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoBannerOrVideo() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput(
                        "Invalid MediaType. PubMatic only supports Banner and Video. Ignoring ImpID=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("invalid ad slot@"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid adSlot 'invalid ad slot@'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidSizes() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@300x200x100"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid size provided in adSlot 'slot@300x200x100'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidWidth() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@widthx200"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid width provided in adSlot 'slot@widthx200'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidHeight() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@300xHeight:1"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid height provided in adSlot 'slot@300xHeight:1'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetAudioToNullIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getAudio).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromAdSlot() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH).containsOnly(250);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW).containsOnly(300);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromFormatIfMissedOriginalsOrInAdSlot() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder()
                .format(singletonList(Format.builder().w(100).h(200).build()))
                .build()), extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH).containsOnly(200);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW).containsOnly(100);
    }

    @Test
    public void makeHttpRequestsShouldSetTagIdForBannerImpsWithSymbolsFromAdSlotBeforeAtSign() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("slot");
    }

    @Test
    public void makeHttpRequestsShouldSetTagIdForVideoImpsWithSymbolsFromAdSlotBeforeAtSign() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.banner(null).video(Video.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("slot");
    }

    @Test
    public void makeHttpRequestsShouldSetAdSlotAsTagIdIfAtSignIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                extImpBuilder -> extImpBuilder
                        .adSlot("adSlot"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("adSlot");
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtNullIfKeywordsAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtNullIfKeywordsAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.keywords(emptyList()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt).containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtFromKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .keywords(singletonList(
                                ExtImpPubmaticKeyVal.of("key2", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("key2", "value1,value2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtAddUnitKeyKeyWordFromDataAdSlotIfAdServerNameIsNotGam() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().build(),
                                PubmaticExtData.of("pbaAdSlot",
                                        PubmaticExtDataAdServer.of("adServerName", "adServerAdSlot"))
                        )))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("dfp_ad_unit_code", "pbaAdSlot");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtAddUnitKeyKeyWordFromAdServerAdSlotIfAdServerNameIsGam() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().build(),
                                PubmaticExtData.of("pbaAdSlot", PubmaticExtDataAdServer.of("gam", "adServerAdSlot"))
                        )))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("dfp_ad_unit_code", "adServerAdSlot");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtFromKeywordsSkippingKeysWithEmptyValues() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .keywords(asList(
                                ExtImpPubmaticKeyVal.of("key with empty value", emptyList()),
                                ExtImpPubmaticKeyVal.of("key2", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("key2", "value1,value2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldSetRequestExtFromWrapExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .wrapper(PubmaticWrapper.of(1, 1)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(givenUpdatedBidRequestExt(1, 1));
    }

    @Test
    public void makeHttpRequestsShouldNotChangeExtIfWrapExtIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(ExtRequest.empty()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsOnly(ExtRequest.empty());
    }

    @Test
    public void makeHttpRequestsShouldSetSitePublisherIdFromImpExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldUpdateSitePublisherIdFromImpExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder()
                        .publisher(Publisher.builder().id("anotherId").build())
                        .build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldSetTrimmedImpExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.publisherId("  pubId  "));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pubId");
    }

    @Test
    public void makeHttpRequestsShouldNotSetAppPublisherIdIfSiteIsNotNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .site(Site.builder().build())
                        .app(App.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getId)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetAppPublisherIdIfSiteIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldSUpdateAppPublisherIdExtPublisherIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("anotherId").build())
                        .build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test.endpoint.com/translator?source=prebid-server", HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfExtBidContainsBidTypeOne() throws JsonProcessingException {
        // given
        final ObjectNode bidType = mapper.createObjectNode().put("BidType", 1);
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(bidType))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidType).build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnXNativeBidIfExtBidContainsBidTypeTwo() throws JsonProcessingException {
        // given
        final ObjectNode bidType = mapper.createObjectNode().put("BidType", 2);
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(bidType))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidType).build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldFillExtBidPrebidVideoDurationIfDurationIsNotNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(PubmaticBidExt.of(null, VideoCreativeInfo.of(1)))))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.set("video", mapper.valueToTree(VideoCreativeInfo.of(1)));
        bidExt.set("prebid", mapper.valueToTree(ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(1, null)).build()));

        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldNotFillExtBidPrebidVideoDurationIfDurationIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(PubmaticBidExt.of(null, VideoCreativeInfo.of(null)))))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.set("video", mapper.valueToTree(VideoCreativeInfo.of(null)));

        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldNotFillExtBidPrebidVideoDurationIfVideoIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(PubmaticBidExt.of(null, null))))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(mapper.createObjectNode()).build(),
                        banner, "USD"));
    }

    @Test
    public void makeBidsShouldTakeOnlyFirstCatElement() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .cat(asList("cat1", "cat2")))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").cat(singletonList("cat1")).build(),
                        banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfExtBidContainsIllegalBidType() throws JsonProcessingException {
        // given
        final ObjectNode bidType = mapper.createObjectNode().put("BidType", 100);
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").ext(bidType))));

        // when
        final Result<List<BidderBid>> result = pubmaticBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").ext(bidType).build(), banner, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldReplaceDctrIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.dctr("dctr")
                        .keywords(singletonList(ExtImpPubmaticKeyVal.of("key_val", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, String> expectedKeyWords = singletonMap("key_val", "dctr");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.convertValue(expectedKeyWords, ObjectNode.class));
    }

    @Test
    public void makeHttpRequestsShouldReplacePmZoneIdIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.pmZoneId("pmzoneid")
                        .keywords(singletonList(ExtImpPubmaticKeyVal.of("pmZoneId", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, String> expectedKeyWords = singletonMap("pmZoneId", "pmzoneid");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.convertValue(expectedKeyWords, ObjectNode.class));
    }

    @Test
    public void makeHttpRequestsShouldReplacePmZoneIDOldKeyNameWithNew() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .keywords(singletonList(ExtImpPubmaticKeyVal.of("pmZoneID", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = pubmaticBidder.makeHttpRequests(bidRequest);

        // then
        final Map<String, String> expectedKeyWords = singletonMap("pmZoneId", "value1,value2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.convertValue(expectedKeyWords, ObjectNode.class));
    }

    private ObjectNode givenExtImpWithKadfloor(String kadfloor) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpPubmatic.builder().kadfloor(kadfloor).build()));
    }

    private static ExtRequest givenInitialBidRequestExt(Integer wrapperProfile, Integer wrapperVersion) {
        final ObjectNode wrapperNode = mapper.createObjectNode()
                .set("wrapper", mapper.valueToTree(PubmaticWrapper.of(wrapperProfile, wrapperVersion)));

        return ExtRequest.of(ExtRequestPrebid.builder().bidderparams(wrapperNode).build());
    }

    private static ExtRequest givenUpdatedBidRequestExt(Integer wrapperProfile, Integer wrapperVersion) {
        final ObjectNode wrapperNode = mapper.createObjectNode()
                .set("wrapper", mapper.valueToTree(PubmaticWrapper.of(wrapperProfile, wrapperVersion)));

        return jacksonMapper.fillExtension(ExtRequest.empty(), wrapperNode);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpPubmatic.ExtImpPubmaticBuilder, ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpPubmatic.ExtImpPubmaticBuilder, ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
                                Function<ExtImpPubmatic.ExtImpPubmaticBuilder,
                                        ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                extCustomizer.apply(ExtImpPubmatic.builder()
                                                .publisherId("pub id")
                                                .adSlot("slot@300x250"))
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
