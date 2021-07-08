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
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class DecenteradsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromDecenterads() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/decenterads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/decenterads/test-decenterads-bid-request-1.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/decenterads/test-decenterads-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/decenterads-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/decenterads/test-decenterads-bid-request-2.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/decenterads/test-decenterads-bid-response-2.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"decenterads":"DC-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImRlY2VudGVyYWRzIjoiREMtVUlEIn19")
                .body(jsonFrom("openrtb2/decenterads/test-auction-decenterads-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/decenterads/test-auction-decenterads-response.json",
                response, singletonList("decenterads"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
