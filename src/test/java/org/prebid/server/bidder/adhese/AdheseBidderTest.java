package org.prebid.server.bidder.adhese;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adhese.model.AdheseBid;
import org.prebid.server.bidder.adhese.model.AdheseOriginData;
import org.prebid.server.bidder.adhese.model.AdheseResponseExt;
import org.prebid.server.bidder.adhese.model.Cpm;
import org.prebid.server.bidder.adhese.model.CpmValues;
import org.prebid.server.bidder.adhese.model.Prebid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adhese.ExtImpAdhese;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AdheseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://ads-{{AccountId}}.adhese.com/json";

    private AdheseBidder adheseBidder;

    @Before
    public void setUp() {
        adheseBidder = new AdheseBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdheseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpressionListSizeIsZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adheseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No impression in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adheseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldModifyIncomingRequestAndSetExpectedHttpRequestUri() {
        // given
        Map<String, List<String>> targets = new HashMap<>();
        targets.put("ci", asList("gent", "brussels"));
        targets.put("ag", singletonList("55"));
        targets.put("tl", singletonList("all"));
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(ExtUser.builder().consent("dummy").build())
                        .build())
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdhese.of("demo",
                                "_adhese_prebid_demo_", "leaderboard", mapper.convertValue(targets, JsonNode.class)))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adheseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsOnly("https://ads-demo.adhese.com/json/sl_adhese_prebid_demo_-leaderboard/ag55/cigent;brussels"
                        + "/tlall/xtdummy");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequestIfTargetsNotPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(ExtUser.builder().consent("dummy").build())
                        .build())
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdhese.of("demo",
                                "_adhese_prebid_demo_", "leaderboard", mapper.convertValue(null, JsonNode.class)))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adheseBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsOnly("https://ads-demo.adhese.com/json/sl_adhese_prebid_demo_-leaderboard/xtdummy");
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<Void> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyIsUnexpected() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall("{}");

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unexpected response body");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseIsEmptyArray() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall("[]");

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseIsArrayWithEmptyObject() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall("[{}]");

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Response resulted in an empty seatBid array");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfSeatbidIsEmpty() throws JsonProcessingException, JsonPatchException {
        // given
        final AdheseBid adheseBid = AdheseBid.builder()
                .body("<div style='background-color:red; height:250px; width:300px'></div>")
                .extension(Prebid.of(Cpm.of(CpmValues.of("1", "USD"))))
                .originInstance("")
                .width("728")
                .height("90")
                .origin("Origin")
                .originData(BidResponse.builder().build())
                .build();

        final AdheseResponseExt adheseResponseExt = AdheseResponseExt.of("60613369", "888", "https://hosts-demo.adhese."
                + "com/rtb_gateway/handlers/client/track/?id=a2f39296-6dd0-4b3c-be85-7baa22e7ff4a", "tag", "js");
        final AdheseOriginData adheseOriginData = AdheseOriginData.of("priority", "orderProperty", "adFormat",
                "adType", "adspaceId", "libId", "slotID", "viewableImpressionCounter");

        final JsonNode adheseBidNode = mapper.valueToTree(adheseBid);
        final JsonNode adheseResponseExtNode = mapper.valueToTree(adheseResponseExt);
        final JsonNode adheseOriginDataNode = mapper.valueToTree(adheseOriginData);

        final JsonNode mergedResponseBid = JsonMergePatch.fromJson(adheseBidNode).apply(adheseResponseExtNode);
        final JsonNode mergedResponse = JsonMergePatch.fromJson(adheseOriginDataNode).apply(mergedResponseBid);

        final ArrayNode body = mapper.createArrayNode().add(mergedResponse);
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(body));

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Response resulted in an empty seatBid array");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException, JsonPatchException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .build()))
                .build();

        final AdheseBid adheseBid = AdheseBid.builder()
                .body("<div style='background-color:red; height:250px; width:300px'></div>")
                .extension(Prebid.of(Cpm.of(CpmValues.of("1", "USD"))))
                .originInstance("")
                .width("728")
                .height("90")
                .origin("JERLICIA")
                .build();

        final AdheseResponseExt adheseResponseExt = AdheseResponseExt.of("60613369", "888", "https://hosts-demo.adhese."
                        + "com/rtb_gateway/handlers/client/track/?id=a2f39296-6dd0-4b3c-be85-7baa22e7ff4a", "tag",
                "js");
        final AdheseOriginData adheseOriginData = AdheseOriginData.of("priority", "orderProperty", "adFormat",
                "adType", "adspaceId", "libId", "slotID", "viewableImpressionCounter");

        final JsonNode adheseBidNode = mapper.valueToTree(adheseBid);
        final JsonNode adheseResponseExtNode = mapper.valueToTree(adheseResponseExt);
        final JsonNode adheseOriginDataNode = mapper.valueToTree(adheseOriginData);

        final JsonNode mergedResponseBid = JsonMergePatch.fromJson(adheseBidNode).apply(adheseResponseExtNode);
        final JsonNode mergedResponse = JsonMergePatch.fromJson(adheseOriginDataNode).apply(mergedResponseBid);

        final ArrayNode body = mapper.createArrayNode().add(mergedResponse);
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(body));

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, bidRequest);

        // then
        final String adm = "<div style='background-color:red; height:250px; width:300px'></div><img src='https://hosts-demo.adhese.com/rtb_gateway/handlers/client/track/?id=a2f39296-6dd0-4b3c-be85-7baa22e7ff4a' style='height:1px; width:1px; margin: -1px -1px; display:none;'/>";
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("1")
                        .impid("impId")
                        .adm(adm)
                        .price(BigDecimal.valueOf(1))
                        .crid("60613369")
                        .dealid("888")
                        .w(728)
                        .h(90)
                        .ext(mapper.valueToTree(AdheseOriginData.of("priority", "orderProperty", "adFormat", "adType",
                                "adspaceId", "libId", "slotID", "viewableImpressionCounter")))
                        .build(),
                BidType.banner, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull().hasSize(1).first().isEqualTo(expected);
    }

    @Test
    public void makeBidsShouldReturnCorrectBidIfAdheseBidContainsVastTag() throws JsonProcessingException,
            JsonPatchException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .build()))
                .build();

        final AdheseBid adheseBid = AdheseBid.builder()
                .body("<vast style='background-color:red; height:250px; width:300px'></vast>")
                .extension(Prebid.of(Cpm.of(CpmValues.of("1", "USD"))))
                .originInstance("")
                .width("728")
                .height("90")
                .origin("JERLICIA")
                .build();

        final AdheseResponseExt adheseResponseExt = AdheseResponseExt.of("60613369", "888", "https://hosts-demo."
                        + "adhese.com/rtb_gateway/handlers/client/track/?id=a2f39296-6dd0-4b3c-be85-7baa22e7ff4a",
                "tag", "js");
        final AdheseOriginData adheseOriginData = AdheseOriginData.of("priority", "orderProperty", "adFormat",
                "adType", "adspaceId", "libId", "slotID", "viewableImpressionCounter");

        final JsonNode adheseBidNode = mapper.valueToTree(adheseBid);
        final JsonNode adheseResponseExtNode = mapper.valueToTree(adheseResponseExt);
        final JsonNode adheseOriginDataNode = mapper.valueToTree(adheseOriginData);

        final JsonNode mergedResponseBid = JsonMergePatch.fromJson(adheseBidNode).apply(adheseResponseExtNode);
        final JsonNode mergedResponse = JsonMergePatch.fromJson(adheseOriginDataNode).apply(mergedResponseBid);

        final ArrayNode body = mapper.createArrayNode().add(mergedResponse);
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(body));

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, bidRequest);

        // then
        final String adm = "<vast style='background-color:red; height:250px; width:300px'></vast>";
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("1")
                        .impid("impId")
                        .adm(adm)
                        .price(BigDecimal.valueOf(1))
                        .crid("60613369")
                        .dealid("888")
                        .w(728)
                        .h(90)
                        .ext(mapper.valueToTree(AdheseOriginData.of("priority", "orderProperty", "adFormat", "adType",
                                "adspaceId", "libId", "slotID", "viewableImpressionCounter")))
                        .build(),
                BidType.video, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull().hasSize(1).first().isEqualTo(expected);
    }

    @Test
    public void makeBidsShouldReturnCorrectBidIfAdheseResponseExtIsNotEqualJs() throws JsonProcessingException,
            JsonPatchException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .build()))
                .build();

        final AdheseBid adheseBid = AdheseBid.builder()
                .body("<vast style='background-color:red; height:250px; width:300px'></vast>")
                .extension(Prebid.of(Cpm.of(CpmValues.of("1", "USD"))))
                .originInstance("")
                .width("728")
                .height("90")
                .origin("JERLICIA")
                .build();

        final AdheseResponseExt adheseResponseExt = AdheseResponseExt.of("60613369", "888", "https://hosts-demo.adhese."
                + "com/rtb_gateway/handlers/client/track/?id=a2f39296-6dd0-4b3c-be85-7baa22e7ff4a", "tag", "ext");
        final AdheseOriginData adheseOriginData = AdheseOriginData.of("priority", "orderProperty", "adFormat",
                "adType", "adspaceId", "libId", "slotID", "viewableImpressionCounter");

        final JsonNode adheseBidNode = mapper.valueToTree(adheseBid);
        final JsonNode adheseResponseExtNode = mapper.valueToTree(adheseResponseExt);
        final JsonNode adheseOriginDataNode = mapper.valueToTree(adheseOriginData);

        final JsonNode mergedResponseBid = JsonMergePatch.fromJson(adheseBidNode).apply(adheseResponseExtNode);
        final JsonNode mergedResponse = JsonMergePatch.fromJson(adheseOriginDataNode).apply(mergedResponseBid);

        final ArrayNode body = mapper.createArrayNode().add(mergedResponse);
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(body));

        // when
        final Result<List<BidderBid>> result = adheseBidder.makeBids(httpCall, bidRequest);

        // then
        final String adm = "tag";
        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("1")
                        .impid("impId")
                        .adm(adm)
                        .price(BigDecimal.valueOf(1))
                        .crid("60613369")
                        .dealid("888")
                        .w(728)
                        .h(90)
                        .ext(mapper.valueToTree(AdheseOriginData.of("priority", "orderProperty", "adFormat", "adType",
                                "adspaceId", "libId", "slotID", "viewableImpressionCounter")))
                        .build(),
                BidType.banner, "USD");

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull().hasSize(1).first().isEqualTo(expected);
    }

    private static HttpCall<Void> givenHttpCall(String responseBody) {
        return HttpCall.success(
                HttpRequest.<Void>builder().build(),
                HttpResponse.of(200, null, responseBody), null);
    }
}
