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
public class IxTest extends IntegrationTest {

    private static final String IX = "ix";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromIx() throws IOException, JSONException {
        // given
        // ix bid response for imp 6 with 300x250
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/ix-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ix/test-ix-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ix/test-ix-bid-response-1.json"))));

        // ix bid response for imp 6 with 600x480
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/ix-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ix/test-ix-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ix/test-ix-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ix/test-cache-ix-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ix/test-cache-ix-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"ix":"IE-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Iml4IjoiSUUtVUlEIn19")
                .body(jsonFrom("openrtb2/ix/test-auction-ix-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/ix/test-auction-ix-response.json",
                response, singletonList(IX));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
