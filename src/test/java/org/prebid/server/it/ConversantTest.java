package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class ConversantTest extends IntegrationTest {

    private static final String CONVERSANT = "conversant";
    private static final String CONVERSANT_ALIAS = "conversantAlias";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConversant() throws IOException, JSONException {
        // given
        // conversant bid response for imp 4
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/test-conversant-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/conversant/test-conversant-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/conversant/test-cache-conversant-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/conversant/test-cache-matcher-conversant.json")));
        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"conversant":"CV-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNvbnZlcnNhbnQiOiJDVi1VSUQifX0=")
                .body(jsonFrom("openrtb2/conversant/test-auction-conversant-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/conversant/test-auction-conversant-response.json",
                response, singletonList(CONVERSANT));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConversantAlias() throws IOException, JSONException {
        // given
        // conversant bid response for imp 4 with alias parameters
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/conversant-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/alias/test-conversant-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/conversant/alias/test-conversant-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/conversant/alias/test-cache-conversant-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/conversant/alias/test-cache-conversant-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"conversant":"CV-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNvbnZlcnNhbnQiOiJDVi1VSUQifX0=")
                .body(jsonFrom("openrtb2/conversant/alias/test-auction-conversant-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/conversant/alias/test-auction-conversant-response.json",
                response, asList(CONVERSANT, CONVERSANT_ALIAS));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
