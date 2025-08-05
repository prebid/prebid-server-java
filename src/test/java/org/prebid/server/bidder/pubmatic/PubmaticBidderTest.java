package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticBidderImpExt;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticExtDataAdServer;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticWrapper;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticBidExt;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticBidResponse;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticExtBidResponse;
import org.prebid.server.bidder.pubmatic.model.response.VideoCreativeInfo;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodesBidder;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;
import org.prebid.server.proto.openrtb.ext.response.ExtIgiIgs;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class PubmaticBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.endpoint.com/translator?source=prebid-server";

    private final PubmaticBidder target = new PubmaticBidder(ENDPOINT_URL, jacksonMapper);

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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotUpdateBidfloorIfImpExtKadfloorIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .bidfloor(BigDecimal.TEN)
                .ext(givenExtImpWithKadfloor("invalid")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(12.5));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnMaxOfBidfloorAndKadfloorIfImpExtKadfloorIsValid() {
        // given
        final BidRequest bidRequest =
                givenBidRequest(impBuilder -> impBuilder
                        .ext(givenExtImpWithKadfloor("12.5"))
                        .bidfloor(BigDecimal.ONE));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.valueOf(12.5));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfBidRequestExtWrapperIsInvalid() {
        // given
        final ObjectNode pubmaticNode = mapper.createObjectNode();
        pubmaticNode.set("pubmatic", mapper.createObjectNode()
                .set("wrapper", mapper.createObjectNode()
                        .set("version", TextNode.valueOf("invalid"))));
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder().bidderparams(pubmaticNode).build());

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(bidderError -> bidderError.getType().equals(BidderError.Type.bad_input)
                        && bidderError.getMessage().startsWith("Cannot deserialize value"));
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestExtIfAcatFieldIsValidAndTrimWhitespace() {
        // given
        final ObjectNode pubmaticNode = mapper.createObjectNode();
        pubmaticNode.set("pubmatic", mapper.createObjectNode()
                .set("acat", mapper.createArrayNode()
                        .add("\tte st Value\t").add("test Value").add("Value")));

        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder().bidderparams(pubmaticNode).build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ExtRequest expectedExtRequest = ExtRequest.empty();
        expectedExtRequest.addProperty("acat",
                mapper.createArrayNode().add("te st Value").add("test Value").add("Value"));

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(expectedExtRequest);
    }

    @Test
    public void makeHttpRequestsShouldReturnAllowedBidderCodeWithPubmaticAdded() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(ExtRequestPrebidAlternateBidderCodes.of(true, Map.of("pubmatic",
                        ExtRequestPrebidAlternateBidderCodesBidder.of(true, Set.of("bidder1", "bidder2")))))
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .extracting(marketplace -> (ArrayNode) marketplace.path("allowedbidders"))
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .extracting(JsonNode::asText)
                .containsOnly("pubmatic", "bidder1", "bidder2");
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyPubmaticWhenPubmaticCodesAreDisabled() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(ExtRequestPrebidAlternateBidderCodes.of(true, Map.of("pubmatic",
                        ExtRequestPrebidAlternateBidderCodesBidder.of(false, Set.of("bidder1", "bidder2")))))
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .extracting(marketplace -> (ArrayNode) marketplace.path("allowedbidders"))
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .extracting(JsonNode::asText)
                .containsOnly("pubmatic");
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyAllWhenPubmaticCodesAreAbsent() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(ExtRequestPrebidAlternateBidderCodes.of(true, Map.of("pubmatic",
                        ExtRequestPrebidAlternateBidderCodesBidder.of(true, null))))
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .extracting(marketplace -> (ArrayNode) marketplace.path("allowedbidders"))
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .extracting(JsonNode::asText)
                .containsOnly("all");
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyAllWhenPubmaticCodesHasWildcard() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(ExtRequestPrebidAlternateBidderCodes.of(true, Map.of("pubmatic",
                        ExtRequestPrebidAlternateBidderCodesBidder.of(true, Set.of("*")))))
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .extracting(marketplace -> (ArrayNode) marketplace.path("allowedbidders"))
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .extracting(JsonNode::asText)
                .containsOnly("all");
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyPubmaticWhenPubmaticCodesAreAbsent() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(ExtRequestPrebidAlternateBidderCodes.of(true, Map.of()))
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .extracting(marketplace -> (ArrayNode) marketplace.path("allowedbidders"))
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .extracting(JsonNode::asText)
                .containsOnly("pubmatic");
    }

    @Test
    public void makeHttpRequestsShouldReturnOnlyPubmaticWhenAlternateBidderCodesAreDisabled() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(ExtRequestPrebidAlternateBidderCodes.of(false, Map.of()))
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .extracting(marketplace -> (ArrayNode) marketplace.path("allowedbidders"))
                .asInstanceOf(InstanceOfAssertFactories.iterable(JsonNode.class))
                .extracting(JsonNode::asText)
                .containsOnly("pubmatic");
    }

    @Test
    public void makeHttpRequestsShouldReturnNothingWhenAlternateBidderCodeIsAbsent() {
        // given
        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder()
                .alternateBidderCodes(null)
                .build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("marketplace"))
                .isNull();
    }

    @Test
    public void makeHttpRequestsShouldMergeWrappersFromImpAndBidRequestExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(givenBidRequestExt(123, null)),
                identity(),
                extBuilder -> extBuilder.wrapper(PubmaticWrapper.of(321, 456)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(expectedBidRequestExt(bidRequest.getExt(), 123, 456));
    }

    @Test
    public void makeHttpRequestsShouldNotReturnErrorIfNativePresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null).xNative(Native.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoBannerOrVideoOrNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput(
                        "Invalid MediaType. PubMatic only supports Banner, Video and Native. Ignoring ImpID=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseWrapperFromBidRequestExtIfPresent() {
        // given
        final ObjectNode pubmaticNode = mapper.createObjectNode()
                .set("pubmatic", mapper.createObjectNode()
                        .set("wrapper", mapper.valueToTree(PubmaticWrapper.of(21, 33))));

        final ExtRequest bidRequestExt = ExtRequest.of(ExtRequestPrebid.builder().bidderparams(pubmaticNode).build());
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(bidRequestExt), identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(expectedBidRequestExt(bidRequestExt, 21, 33));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("invalid ad slot@"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid adSlot 'invalid ad slot@'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidSizes() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@300x200x100"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid size provided in adSlot 'slot@300x200x100'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidWidth() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@widthx200"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid width provided in adSlot 'slot@widthx200'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAdSlotHasInvalidHeight() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot("slot@300xHeight:1"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Invalid height provided in adSlot 'slot@300xHeight:1'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetAudioToNullIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.audio(Audio.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getAudio)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromAdSlot() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH)
                .containsExactly(250);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW)
                .containsExactly(300);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromFormatIfMissedOriginalsOrInAdSlot() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder()
                .format(singletonList(Format.builder().w(100).h(200).build()))
                .build()), extImpPubmaticBuilder -> extImpPubmaticBuilder.adSlot(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getH)
                .containsExactly(200);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW)
                .containsExactly(100);
    }

    @Test
    public void makeHttpRequestsShouldSetTagIdForBannerImpsWithSymbolsFromAdSlotBeforeAtSign() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("slot");
    }

    @Test
    public void makeHttpRequestsShouldSetTagIdForVideoImpsWithSymbolsFromAdSlotBeforeAtSign() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.banner(null).video(Video.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("slot");
    }

    @Test
    public void makeHttpRequestsShouldSetAdSlotAsTagIdIfAtSignIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                extImpBuilder -> extImpBuilder
                        .adSlot("adSlot"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsExactly("adSlot");
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtNullIfKeywordsAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtNullIfKeywordsAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.keywords(emptyList()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsNull();
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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("key2", "value1,value2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtAddUnitKeyKeyWordFromDataAdSlotIfAdServerNameIsNotGam() {
        // given
        final ObjectNode extData = mapper.createObjectNode()
                .put("pbadslot", "pbaAdSlot")
                .set("adserver", mapper.valueToTree(PubmaticExtDataAdServer.of("adServerName", "adServerAdSlot")));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().build(),
                                extData,
                                null,
                                null
                        )))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("dfp_ad_unit_code", "pbaAdSlot");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtAddUnitKeyKeyWordFromAdServerAdSlotIfAdServerNameIsGam() {
        // given
        final ObjectNode extData = mapper.createObjectNode()
                .put("pbadslot", "pbaAdSlot")
                .set("adserver", mapper.valueToTree(PubmaticExtDataAdServer.of("gam", "adServerAdSlot")));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().build(),
                                extData,
                                null,
                                null
                        )))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("dfp_ad_unit_code", "adServerAdSlot");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtWithKeyValWithDctrAndExtDataExceptForPbaSlotAndAdServer() {
        // given
        final ObjectNode extData = mapper.createObjectNode()
                .put("pbadslot", "pbaAdSlot")
                .put("key1", "value")
                .set("adserver", mapper.valueToTree(PubmaticExtDataAdServer.of("gam", "adServerAdSlot")));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().dctr("dctr").build(),
                                extData,
                                null,
                                null
                        )))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("dfp_ad_unit_code", "adServerAdSlot");
        expectedImpExt.put("key_val", "dctr|key1=value");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtWithKeyValWithExtDataWhenDctrIsAbsent() {
        // given
        final ObjectNode extData = mapper.createObjectNode()
                .put("key1", "  value")
                .put("key2", 1)
                .put("key3", true)
                .put("key4", 3.42)
                .set("key5", mapper.createArrayNode().add("elem1").add("elem2"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().dctr(null).build(),
                                extData,
                                null,
                                null
                        )))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("key_val", "key1=value|key2=1|key3=true|key4=3.42|key5=elem1,elem2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtAddAE() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().build(), null, 1, null)))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode().put("ae", 1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldAddImpExtAddGpId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(PubmaticBidderImpExt.of(
                                ExtImpPubmatic.builder().build(), null, null, "gpId")))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode().put("gpid", "gpId");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.createObjectNode();
        expectedImpExt.put("key2", "value1,value2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldSetRequestExtFromWrapExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder
                        .wrapper(PubmaticWrapper.of(1, 1)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .containsExactly(expectedBidRequestExt(ExtRequest.empty(), 1, 1));
    }

    @Test
    public void makeHttpRequestsShouldNotChangeExtIfWrapExtIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.ext(ExtRequest.empty()),
                identity(),
                identity());

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
    public void makeHttpRequestsShouldSetSitePublisherIdFromImpExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("pub id");
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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldSetTrimmedImpExtPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().build()),
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.publisherId("  pubId  "));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("pubId");
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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldSetDisplayManagerFieldsFromAppExtPrebid() {
        // given
        final ExtApp extApp = ExtApp.of(ExtAppPrebid.of("ext-prebid-source", "ext-prebid-version"), null);
        extApp.addProperty("source", TextNode.valueOf("ext-source"));
        extApp.addProperty("version", TextNode.valueOf("ext-version"));

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().ext(extApp).build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanager, Imp::getDisplaymanagerver)
                .containsExactly(tuple("ext-prebid-source", "ext-prebid-version"));
    }

    @Test
    public void makeHttpRequestsShouldSetDisplayManagerFieldsFromAppExt() {
        // given
        final ExtApp extApp = ExtApp.of(ExtAppPrebid.of("ext-prebid-source", null), null);
        extApp.addProperty("source", TextNode.valueOf("ext-source"));
        extApp.addProperty("version", TextNode.valueOf("ext-version"));

        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.app(App.builder().ext(extApp).build()),
                identity(),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getDisplaymanager, Imp::getDisplaymanagerver)
                .containsExactly(tuple("ext-source", "ext-version"));
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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("pub id");
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test.endpoint.com/translator?source=prebid-server", HttpRequest::getUri);
        assertThat(result.getValue().getFirst().getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeHttpRequestsShouldReplaceDctrIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                extImpPubmaticBuilder -> extImpPubmaticBuilder.dctr("dctr")
                        .keywords(singletonList(ExtImpPubmaticKeyVal.of("key_val", asList("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

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
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Map<String, String> expectedKeyWords = singletonMap("pmZoneId", "value1,value2");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.convertValue(expectedKeyWords, ObjectNode.class));
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().getFirst().getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().getFirst().getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(null));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(1))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "banner")));

        assertThat(result.getBids()).containsExactly(BidderBid.of(
                Bid.builder().impid("123").mtype(1).ext(expectedBidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidderResponseShouldReturnVideoBidWhenInBannerVideoIsTrue() throws JsonProcessingException {
        // given
        final ObjectNode givenBidExt = mapper.createObjectNode().put("ibv", true);
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(
                        bidBuilder -> bidBuilder.impid("123").mtype(1).ext(givenBidExt))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedBidExt = mapper.createObjectNode()
                .put("ibv", true)
                .set("prebid", mapper.createObjectNode()
                        .set("meta", mapper.createObjectNode().put("mediaType", "video")));

        assertThat(result.getBids()).containsExactly(BidderBid.of(
                Bid.builder().impid("123").mtype(1).ext(expectedBidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidderResponseShouldReturnVideoBidIfExtBidContainsMtypeTwo() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(2))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "video")));

        assertThat(result.getBids()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").mtype(2).ext(expectedBidExt).build(), video, "USD"));
    }

    @Test
    public void makeBidderResponseShouldReturnAudioBidIfExtBidContainsMtype3() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(3))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "audio")));

        assertThat(result.getBids()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").mtype(3).ext(expectedBidExt).build(), audio, "USD"));
    }

    @Test
    public void makeBidderResponseShouldReturnXNativeBidIfExtBidContainsMtypeFour() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(4))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "native")));

        assertThat(result.getBids()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").mtype(4).ext(expectedBidExt).build(), xNative, "USD"));
    }

    @Test
    public void makeBidderResponseShouldFillExtBidPrebidVideoDurationIfDurationIsNotNull()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(2)
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(VideoCreativeInfo.of(1), null, null, null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.set("video", mapper.valueToTree(VideoCreativeInfo.of(1)));
        bidExt.set("prebid", mapper.valueToTree(ExtBidPrebid.builder()
                .meta(ExtBidPrebidMeta.builder().mediaType("video").build())
                .video(ExtBidPrebidVideo.of(1, null))
                .build()));
        assertThat(result.getBids()).containsExactly(
                BidderBid.of(Bid.builder().mtype(2).impid("123").ext(bidExt).build(), video, "USD"));
    }

    @Test
    public void makeBidderResponseShouldNotFillExtBidPrebidVideoDurationIfDurationIsNull()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(VideoCreativeInfo.of(null), null, null, null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.set("video", mapper.valueToTree(VideoCreativeInfo.of(null)));
        bidExt.set("prebid", mapper.valueToTree(ExtBidPrebid.builder()
                .meta(ExtBidPrebidMeta.builder().mediaType("banner").build())
                .build()));

        assertThat(result.getBids()).containsExactly(
                BidderBid.of(Bid.builder().impid("123").mtype(1).ext(bidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidderResponseShouldNotFillExtBidPrebidVideoDurationIfVideoIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(null, null, null, null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "banner")));

        assertThat(result.getBids()).containsExactly(BidderBid.of(
                Bid.builder().impid("123").mtype(1).ext(expectedBidExt).build(), banner, "USD"));
    }

    @Test
    public void makeBidderResponseShouldFillDealPriorityData() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(null, 12, null, null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getDealPriority)
                .containsExactly(12);
    }

    @Test
    public void makeBidderResponseShouldFillSeat() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(null, 12, "marketplace", null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getSeat)
                .containsExactly("marketplace");
    }

    @Test
    public void makeBidderResponseShouldParseNativeAdmData() throws JsonProcessingException {
        // given
        final ObjectNode admNode = mapper.createObjectNode();
        final ObjectNode nativeNode = mapper.createObjectNode();
        nativeNode.put("property1", "value1");
        admNode.set("native", nativeNode);
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(4)
                                .adm(admNode.toString())
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(null, 12, null, null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly("{\"property1\":\"value1\"}");
    }

    @Test
    public void makeBidderResponseShouldTakeOnlyFirstCatElement() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .cat(asList("cat1", "cat2")))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "banner")));

        assertThat(result.getBids()).containsExactly(BidderBid.of(
                Bid.builder()
                        .impid("123")
                        .mtype(1)
                        .cat(singletonList("cat1"))
                        .ext(expectedBidExt).build(),
                banner,
                "USD"));
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBidIfExtBidContainsIllegalBidType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123").mtype(100))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("failed to parse bid mtype (100) for impression id 123"));
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnFledgeAuctionConfig() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(bidBuilder -> bidBuilder.impid("imp_id"));
        final ObjectNode auctionConfig = mapper.createObjectNode();
        final PubmaticBidResponse bidResponseWithFledge = PubmaticBidResponse.builder()
                .cur(bidResponse.getCur())
                .seatbid(bidResponse.getSeatbid())
                .ext(PubmaticExtBidResponse.of(Map.of("imp_id", auctionConfig)))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponseWithFledge));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final ObjectNode expectedBidExt = mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                .set("meta", mapper.createObjectNode().put("mediaType", "banner")));

        assertThat(result.getBids()).containsExactly(BidderBid.of(
                Bid.builder().impid("imp_id").mtype(1).ext(expectedBidExt).build(), banner, "USD"));

        final ExtIgi igi = ExtIgi.builder()
                .igs(singletonList(ExtIgiIgs.builder().impId("imp_id").config(auctionConfig).build()))
                .build();

        assertThat(result.getIgi()).containsExactly(igi);
    }

    @Test
    public void makeBidderResponseShouldNotModifyAdmWhenNativeNodeIsNull() throws JsonProcessingException {
        // given
        final ObjectNode admNode = mapper.createObjectNode().put("otherField", "value");
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .adm(admNode.toString())
                                .ext(mapper.valueToTree(
                                        PubmaticBidExt.of(null, 12, null, null))))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(admNode.toString());
    }

    @Test
    public void makeBidsShouldFail() throws JsonProcessingException {
        //given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.generic("Deprecated adapter method invoked"));
    }

    private ObjectNode givenExtImpWithKadfloor(String kadfloor) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpPubmatic.builder().kadfloor(kadfloor).build()));
    }

    private static ExtRequest givenBidRequestExt(Integer wrapperProfile, Integer wrapperVersion) {
        final ObjectNode pubmaticNode = mapper.createObjectNode()
                .set("pubmatic", mapper.createObjectNode()
                        .set("wrapper", mapper.valueToTree(PubmaticWrapper.of(wrapperProfile, wrapperVersion))));

        return ExtRequest.of(ExtRequestPrebid.builder().bidderparams(pubmaticNode).build());
    }

    private static ExtRequest expectedBidRequestExt(ExtRequest originalExtRequest,
                                                    Integer wrapperProfile,
                                                    Integer wrapperVersion) {

        final ObjectNode wrapperNode = mapper.createObjectNode()
                .set("wrapper", mapper.valueToTree(PubmaticWrapper.of(wrapperProfile, wrapperVersion)));

        final ExtRequest extRequestWithoutPrebid = jacksonMapper.fillExtension(
                ExtRequest.empty(),
                originalExtRequest.getProperties());

        return jacksonMapper.fillExtension(extRequestWithoutPrebid, wrapperNode);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                              UnaryOperator<ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                              UnaryOperator<ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

        return givenBidRequest(identity(), impCustomizer, extCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                UnaryOperator<ExtImpPubmatic.ExtImpPubmaticBuilder> extCustomizer) {

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

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder().mtype(1)).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
