package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class ZeroclickfraudTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromZeroclickfraud() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/zeroclickfraud-exchange"))
                .withQueryParam("sid", equalTo("1"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/zeroclickfraud/test-zeroclickfraud-bid-request-1.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/zeroclickfraud/test-zeroclickfraud-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/zeroclickfraud-exchange"))
                .withQueryParam("sid", equalTo("2"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/zeroclickfraud/test-zeroclickfraud-bid-request-2.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/zeroclickfraud/test-zeroclickfraud-bid-response-2.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"zeroclickfraud":"ZF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Inplcm9jbGlja2ZyYXVkIjoiWkYtVUlEIn19")
                .body(jsonFrom("openrtb2/zeroclickfraud/test-auction-zeroclickfraud-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/zeroclickfraud/test-auction-zeroclickfraud-response.json",
                response, singletonList("zeroclickfraud"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbCacheDebugComparator());
    }
}
