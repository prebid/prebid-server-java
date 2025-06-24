package org.prebid.server.bidder.sparteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;

public class SparteoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.sparteo.com/endpoint";

    private SparteoBidder sparteoBidder;

    @BeforeEach
    public void setUp() {
        sparteoBidder = new SparteoBidder(ENDPOINT_URL, jacksonMapper);
    }

    private ObjectNode createBidExtWithType(String bidType) {
        final ObjectNode bidExt = jacksonMapper.mapper().createObjectNode();
        final ObjectNode prebidNode = jacksonMapper.mapper().createObjectNode();
        prebidNode.put("type", bidType);
        bidExt.set("prebid", prebidNode);
        return bidExt;
    }

    private ObjectNode createBidExtWithEmptyPrebid() {
        final ObjectNode bidExt = jacksonMapper.mapper().createObjectNode();
        bidExt.set("prebid", jacksonMapper.mapper().createObjectNode());
        return bidExt;
    }

    private ObjectNode createBidExtWithoutPrebid() {
        return jacksonMapper.mapper().createObjectNode();
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SparteoBidder("invalid_url", jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void creationShouldFailOnNullMapper() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SparteoBidder(ENDPOINT_URL, null));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCannotBeParsed() {
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.valueToTree(ExtPrebid.of(null, "invalid"))).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage())
                            .startsWith("ignoring imp id=null, error processing ext: Cannot construct instance");
                });
        assertThat(result.getValue())
                .isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAllImpsFilteredOut() {
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        Imp.builder()
                        .id("imp1")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, "invalid")))
                        .build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getValue())
                .isEmpty();
        assertThat(result.getErrors())
                .hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWithErrorsAndValueIfSomeImpsAreInvalid() {
        final ObjectNode validExt = jacksonMapper.mapper().createObjectNode();
        validExt.set("bidder", jacksonMapper.mapper().createObjectNode().put("key", "value"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().id("imp1").ext(mapper.valueToTree(ExtPrebid.of(null, "invalid"))).build(),
                        Imp.builder().id("imp2").ext(validExt).build()
                ))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> assertThat(error.getMessage())
                        .startsWith("ignoring imp id=imp1"));
        assertThat(result.getValue())
                .hasSize(1);
        final ObjectNode modifiedImpExt = result.getValue().get(0).getPayload().getImp().get(0).getExt();
        assertThat(modifiedImpExt
                .get("sparteo")
                .get("params")
                .get("key")
                .asText())
                .isEqualTo("value");
    }

    @Test
    public void makeHttpRequestsShouldProcessImpWithBidderExtAndSetNetworkIdOnSite() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode()
                .put("networkId", "testNetworkId")
                .put("customParam", "customValue"));

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().build()).build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        assertThat(result.getValue())
                .hasSize(1);
        final HttpRequest<BidRequest> httpRequest = result.getValue().get(0);
        assertThat(httpRequest.getMethod())
                .isEqualTo(HttpMethod.POST);
        assertThat(httpRequest.getUri())
                .isEqualTo(ENDPOINT_URL);

        final BidRequest outgoingRequest = httpRequest.getPayload();
        assertThat(outgoingRequest.getImp())
                .hasSize(1);
        final ObjectNode modifiedImpExt = outgoingRequest.getImp().get(0).getExt();
        assertThat(modifiedImpExt
                .get("sparteo")
                .get("params")
                .get("networkId")
                .asText())
                .isEqualTo("testNetworkId");
        assertThat(modifiedImpExt
                .get("sparteo")
                .get("params")
                .get("customParam")
                .asText())
                .isEqualTo("customValue");
        assertThat(modifiedImpExt.has("bidder"))
                .isFalse();

        final ExtPublisher publisherExt = (ExtPublisher) outgoingRequest.getSite().getPublisher().getExt();
        assertThat(publisherExt
                .getProperties()
                .get("params")
                .get("networkId")
                .asText())
                .isEqualTo("testNetworkId");
    }

    @Test
    public void makeHttpRequestsShouldUseFirstNetworkIdWhenMultipleImpsHaveIt() {
        final ObjectNode impExt1 = jacksonMapper.mapper().createObjectNode();
        impExt1.set("bidder", jacksonMapper.mapper().createObjectNode().put("networkId", "id1"));
        final ObjectNode impExt2 = jacksonMapper.mapper().createObjectNode();
        impExt2.set("bidder", jacksonMapper.mapper().createObjectNode().put("networkId", "id2"));

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().build()).build())
                .imp(asList(
                        Imp.builder().id("imp1").ext(impExt1).build(),
                        Imp.builder().id("imp2").ext(impExt2).build()
                ))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        final ExtPublisher publisherExt =
                (ExtPublisher) result.getValue().get(0).getPayload().getSite().getPublisher().getExt();
        assertThat(publisherExt
                .getProperties()
                .get("params")
                .get("networkId")
                .asText())
                .isEqualTo("id1");
    }

    @Test
    public void makeHttpRequestsShouldMergeBidderParamsIntoSparteoParams() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode()
                .put("paramFromBidder", "value1"));
        final ObjectNode sparteoNode = impExt.putObject("sparteo");
        sparteoNode.putObject("params").put("paramFromSparteo", "value2");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        final ObjectNode modifiedImpExt = result.getValue().get(0).getPayload().getImp().get(0).getExt();
        final JsonNode paramsNode = modifiedImpExt.get("sparteo").get("params");
        assertThat(paramsNode
                .get("paramFromBidder")
                .asText())
                .isEqualTo("value1");
        assertThat(paramsNode
                .get("paramFromSparteo")
                .asText())
                .isEqualTo("value2");
    }

    @Test
    public void makeHttpRequestsShouldOverwriteSparteoParamsWithBidderParamsIfKeysConflict() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode()
                .put("conflictingParam", "bidderValue"));
        final ObjectNode sparteoNode = impExt.putObject("sparteo");
        sparteoNode.putObject("params").put("conflictingParam", "sparteoValue");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        final ObjectNode modifiedImpExt = result.getValue().get(0).getPayload().getImp().get(0).getExt();
        final JsonNode paramsNode = modifiedImpExt.get("sparteo").get("params");
        assertThat(paramsNode
                .get("conflictingParam")
                .asText())
                .isEqualTo("bidderValue");
    }

    @Test
    public void makeHttpRequestsShouldHandleMissingSiteOrPublisherGracefully() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode().put("networkId", "testNetworkId"));

        final BidRequest bidRequestNoSite = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();
        final BidRequest bidRequestNoPublisher = BidRequest.builder()
                .site(Site.builder().build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> resultNoSite = sparteoBidder
                .makeHttpRequests(bidRequestNoSite);
        final Result<List<HttpRequest<BidRequest>>> resultNoPublisher = sparteoBidder
                .makeHttpRequests(bidRequestNoPublisher);

        assertThat(resultNoSite.getErrors())
                .isEmpty();
        assertThat(resultNoSite
                .getValue()
                .get(0)
                .getPayload()
                .getSite())
                .isNull();

        assertThat(resultNoPublisher.getErrors())
                .isEmpty();
        assertThat(resultNoPublisher
                .getValue()
                .get(0)
                .getPayload()
                .getSite()
                .getPublisher())
                .isNull();
    }

    @Test
    public void makeHttpRequestsShouldHandlePublisherWithExistingExtAndParams() throws JsonProcessingException {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode().put("networkId", "testNetworkId"));

        final ObjectNode publisherExtNode = jacksonMapper.mapper().createObjectNode();
        publisherExtNode.putObject("params").put("existingParam", "existingValue");
        final ExtPublisher extPublisher = jacksonMapper.mapper().convertValue(publisherExtNode, ExtPublisher.class);

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().ext(extPublisher).build()).build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        final BidRequest requestPayload = result.getValue().get(0).getPayload();
        final ExtPublisher modifiedPublisherExt = (ExtPublisher) requestPayload.getSite().getPublisher().getExt();

        final JsonNode params = modifiedPublisherExt.getProperties().get("params");
        assertThat(params
                .get("networkId")
                .asText())
                .isEqualTo("testNetworkId");
        assertThat(params
                .get("existingParam")
                .asText())
                .isEqualTo("existingValue");
    }

    @Test
    public void makeHttpRequestsShouldHandlePublisherWithExistingExtWithoutParams() throws JsonProcessingException {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode().put("networkId", "testNetworkId"));

        final ObjectNode publisherExtNode = jacksonMapper.mapper().createObjectNode();
        publisherExtNode.put("otherField", "otherValue");
        final ExtPublisher extPublisher = jacksonMapper.mapper().convertValue(publisherExtNode, ExtPublisher.class);

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().ext(extPublisher).build()).build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();

        final BidRequest requestPayload = result.getValue().get(0).getPayload();
        final ExtPublisher modifiedPublisherExt = (ExtPublisher) requestPayload.getSite().getPublisher().getExt();

        assertThat(modifiedPublisherExt
                .getProperties().get("params")
                .get("networkId")
                .asText())
                .isEqualTo("testNetworkId");
        assertThat(modifiedPublisherExt
                .getProperties()
                .get("otherField")
                .asText())
                .isEqualTo("otherValue");
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfHttpResponseStatusCodeIs204() {
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 204, "");
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        assertThat(result.getValue())
                .isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfHttpResponseStatusCodeIsNot200AndNot204() {
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 400, "Bad Request");
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getValue())
                .isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType())
                            .isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .isEqualTo("HTTP status 400 returned from Sparteo");
                });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCannotBeDecoded() {
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, "invalid_json");
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getValue())
                .isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType())
                            .isEqualTo(BidderError.Type.bad_server_response);

                    final String expectedMessageStart = "Failed to decode Sparteo response: "
                            + "Failed to decode: Unrecognized token 'invalid_json'";
                    assertThat(error.getMessage())
                            .startsWith(expectedMessageStart);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(null));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        assertThat(result.getValue())
                .isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        final BidResponse bidResponse = BidResponse.builder().seatbid(Collections.emptyList()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        assertThat(result.getValue())
                .isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        final Bid bid = Bid.builder()
                .impid("imp1")
                .price(BigDecimal.valueOf(1.23))
                .adm("adm-banner")
                .ext(createBidExtWithType(BidType.banner.getName()))
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfully() throws JsonProcessingException {
        final Bid bid = Bid.builder()
                .impid("imp2")
                .price(BigDecimal.valueOf(2.34))
                .adm("adm-video")
                .ext(createBidExtWithType(BidType.video.getName()))
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.video, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        final Bid bid = Bid.builder()
                .impid("imp3")
                .price(BigDecimal.valueOf(3.45))
                .adm("adm-native")
                .ext(createBidExtWithType(BidType.xNative.getName()))
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("GBP")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.xNative, "GBP"));
    }

    @Test
    public void makeBidsShouldReturnErrorForAudioBidAndProcessOther() throws JsonProcessingException {
        final Bid audioBid = Bid.builder()
                .impid("impAudio")
                .price(BigDecimal.valueOf(1.0))
                .ext(createBidExtWithType(BidType.audio.getName()))
                .build();

        final Bid bannerBid = Bid.builder()
                .impid("impBanner")
                .price(BigDecimal.valueOf(2.0))
                .ext(createBidExtWithType(BidType.banner.getName()))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(asList(audioBid, bannerBid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        String.format("Audio bid type not supported by this adapter for impression id: impAudio",
                        audioBid.getImpid()
                ));

        assertThat(result.getValue())
                .hasSize(1)
                .extracting(BidderBid::getBid)
                .containsExactly(bannerBid);
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidExtIsNull() throws JsonProcessingException {
        final Bid bid = Bid.builder().impid("imp1").ext(null).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        String.format("Bid extension or bid.ext.prebid missing for impression id: imp1",
                        bid.getImpid())
                );
    }

    @Test
    public void makeBidsShouldReturnErrorIfPrebidMissingInBidExt() throws JsonProcessingException {
        final Bid bid = Bid.builder().impid("imp1").ext(createBidExtWithoutPrebid()).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        String.format("Bid extension or bid.ext.prebid missing for impression id: imp1",
                        bid.getImpid()
                ));
    }

    @Test
    public void makeBidsShouldReturnErrorIfPrebidTypeMissingInBidExt() throws JsonProcessingException {
        final Bid bid = Bid.builder().impid("imp1").ext(createBidExtWithEmptyPrebid()).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        String.format("Missing type in bid.ext.prebid for impression id: imp1",
                        bid.getImpid()
                ));
    }

    @Test
    public void makeBidsShouldReturnErrorIfPrebidCannotBeParsed() throws JsonProcessingException {
        final ObjectNode malformedExt = jacksonMapper.mapper().createObjectNode();
        malformedExt.putArray("prebid");

        final Bid bid = Bid.builder().impid("imp1").ext(malformedExt).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .element(0).asString().startsWith(
                        String.format("Failed to parse bid.ext.prebid for impression id: imp1, error: ",
                        bid.getImpid()
                ));
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedPrebidTypeString() throws JsonProcessingException {
        final Bid bid = Bid.builder().impid("imp1").ext(createBidExtWithType("unknown-type")).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .element(0).asString().startsWith(
                    String.format("Failed to parse bid.ext.prebid for impression id: imp1, error: ", bid.getImpid())
                );
    }

    @Test
    public void makeBidsShouldHandleNullBidInSeatBid() throws JsonProcessingException {
        final Bid validBid = Bid.builder().impid("validImp").price(BigDecimal.ONE)
                .ext(createBidExtWithType(BidType.banner.getName())).build();
        final List<Bid> bids = new ArrayList<>();
        bids.add(null);
        bids.add(validBid);

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(bids).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getBid()).isEqualTo(validBid);
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Received null bid object within a seatbid.");
    }

    @Test
    public void makeBidsShouldProcessMultipleBidsAndSeatBidsCorrectly() throws JsonProcessingException {
        final Bid bid1 = Bid.builder().impid("imp1").price(BigDecimal.valueOf(1.0))
                .ext(createBidExtWithType(BidType.banner.getName())).build();
        final Bid bid2 = Bid.builder().impid("imp2").price(BigDecimal.valueOf(2.0))
                .ext(createBidExtWithType(BidType.video.getName())).build();
        final Bid bid3 = Bid.builder().impid("imp3").price(BigDecimal.valueOf(3.0))
                .ext(createBidExtWithType(BidType.xNative.getName())).build();

        final SeatBid seatBid1 = SeatBid.builder().bid(asList(bid1, bid2)).build();
        final SeatBid seatBid2 = SeatBid.builder().bid(singletonList(bid3)).build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(asList(seatBid1, seatBid2))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = BidRequest.builder().build();

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(3)
                .extracting(bidderBid -> bidderBid.getBid().getImpid(), BidderBid::getType)
                .containsExactlyInAnyOrder(
                        tuple("imp1", BidType.banner),
                        tuple("imp2", BidType.video),
                        tuple("imp3", BidType.xNative)
                );
    }

    @Test
    public void makeHttpRequestsShouldHandleEmptyBidderExt() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode());
        impExt.putObject("sparteo");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();
        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        final ObjectNode modifiedImpExt = result.getValue().get(0).getPayload().getImp().get(0).getExt();

        assertThat(modifiedImpExt.has("sparteo")).isTrue();
        assertThat(modifiedImpExt.get("sparteo").isObject()).isTrue();
        assertThat(modifiedImpExt.get("sparteo").has("params")).isTrue();
        assertThat(modifiedImpExt.get("sparteo").get("params").isObject()).isTrue();
        assertThat(modifiedImpExt.get("sparteo").get("params").size()).isEqualTo(0);
    }

    @Test
    public void makeHttpRequestsShouldHandleBidderExtWithoutNetworkId() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode().put("otherParam", "value"));
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("pub1").build()).build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final BidRequest outgoingRequest = result.getValue().get(0).getPayload();

        if (outgoingRequest.getSite() != null && outgoingRequest.getSite().getPublisher() != null) {
            final ExtPublisher publisherExt = (ExtPublisher) outgoingRequest.getSite().getPublisher().getExt();
            if (publisherExt != null && publisherExt.getProperties() != null) {
                assertThat(publisherExt.getProperties().containsKey("params")).isFalse();
            } else {
                assertThat(publisherExt).isNull();
            }
        }
    }

    @Test
    public void makeHttpRequestsShouldHandleBidderExtWithNullNetworkId() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        final ObjectNode bidderNode = jacksonMapper.mapper().createObjectNode();
        bidderNode.putNull("networkId");
        impExt.set("bidder", bidderNode);

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().id("pub1").build()).build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).isEmpty();
        final BidRequest outgoingRequest = result.getValue().get(0).getPayload();

        if (outgoingRequest.getSite() != null && outgoingRequest.getSite().getPublisher() != null) {
            final ExtPublisher publisherExt = (ExtPublisher) outgoingRequest.getSite().getPublisher().getExt();

            if (publisherExt != null && publisherExt.getProperties() != null
                    && publisherExt.getProperties().containsKey("params")) {
                assertThat(publisherExt.getProperties().get("params").has("networkId")).isFalse();
            }
        }
    }

    @Test
    public void makeHttpRequestsShouldNotModifyPublisherExtIfSiteNetworkIdIsNull() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();

        final ObjectNode publisherExtJson = jacksonMapper.mapper().createObjectNode();
        publisherExtJson.put("existing", "value");

        final ExtPublisher originalPublisherExt = jacksonMapper.mapper()
                .convertValue(publisherExtJson, ExtPublisher.class);

        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().publisher(Publisher.builder().ext(originalPublisherExt).build()).build())
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors())
                .isEmpty();
        assertThat(result.getValue())
                .hasSize(1);

        final BidRequest requestPayload = result.getValue().get(0).getPayload();
        final ExtPublisher modifiedPublisherExt = (ExtPublisher) requestPayload.getSite().getPublisher().getExt();

        assertThat(modifiedPublisherExt.getProperties().containsKey("existing"))
                .isTrue();
        assertThat(modifiedPublisherExt.getProperties().get("existing").asText())
                .isEqualTo("value");
        assertThat(modifiedPublisherExt.getProperties().containsKey("params"))
                .isFalse();
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyHandlePreExistingSparteoExtNotAnObjectWhenBidderExtPresent() {
        final ObjectNode impExt = jacksonMapper.mapper().createObjectNode();
        impExt.set("bidder", jacksonMapper.mapper().createObjectNode().put("param", "value"));
        impExt.put("sparteo", "this_is_a_string");

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").ext(impExt).build()))
                .build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final ObjectNode modifiedImpExt = result.getValue().get(0).getPayload().getImp().get(0).getExt();

        assertThat(modifiedImpExt.get("sparteo").isObject()).isTrue();
        assertThat(modifiedImpExt.get("sparteo").get("params").get("param").asText()).isEqualTo("value");
    }

    @Test
    public void makeHttpRequestsShouldHandleNoImpsInRequest() {
        final BidRequest bidRequest = BidRequest.builder().imp(Collections.emptyList()).id("req-no-imps").build();

        final Result<List<HttpRequest<BidRequest>>> result = sparteoBidder.makeHttpRequests(bidRequest);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenPrebidExtIsJsonNull() throws JsonProcessingException {
        final ObjectNode bidExtWithNullPrebid = jacksonMapper.mapper().createObjectNode();
        bidExtWithNullPrebid.set("prebid", NullNode.getInstance());

        final Bid bid = Bid.builder().impid("imp1").ext(bidExtWithNullPrebid).build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly(
                        String.format("Bid extension or bid.ext.prebid missing for impression id: %s",
                        bid.getImpid())
                );
    }

    @Test
    public void makeBidsShouldHandleNullSeatBidInResponse() throws JsonProcessingException {
        final ObjectNode bidExt = createBidExtWithType(BidType.banner.getName());
        final Bid validBid = Bid.builder().impid("validImp").price(BigDecimal.TEN).ext(bidExt).build();
        final SeatBid validSeatBid = SeatBid.builder().bid(singletonList(validBid)).build();

        final List<SeatBid> seatBidsWithNull = new ArrayList<>();
        seatBidsWithNull.add(validSeatBid);
        seatBidsWithNull.add(null);
        seatBidsWithNull.add(SeatBid.builder().bid(singletonList(
                Bid.builder()
                .impid("anotherValidImp")
                .price(BigDecimal.ONE)
                .ext(bidExt)
                .build()
        )).build());

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(seatBidsWithNull)
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));

        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getImpid())
                .containsExactlyInAnyOrder("validImp", "anotherValidImp");
    }

    @Test
    public void makeBidsShouldHandleSeatBidWithNullBidList() throws JsonProcessingException {
        final SeatBid seatBidWithNullBids = SeatBid.builder().bid(null).build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(seatBidWithNullBids))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldHandleSeatBidWithEmptyBidList() throws JsonProcessingException {
        final SeatBid seatBidWithEmptyBids = SeatBid.builder().bid(Collections.emptyList()).build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(seatBidWithEmptyBids))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, 200, mapper.writeValueAsString(bidResponse));
        final Result<List<BidderBid>> result = sparteoBidder.makeBids(httpCall, BidRequest.builder().build());

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, int statusCode, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(statusCode, null, body),
                null);
    }
}
