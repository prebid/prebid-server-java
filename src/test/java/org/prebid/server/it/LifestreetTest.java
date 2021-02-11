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
public class LifestreetTest extends IntegrationTest {

    private static final String LIFESTREET = "lifestreet";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLifestreet() throws IOException, JSONException {
        // given
        // lifestreet bid response for imp 7
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-response-1.json"))));

        // lifestreet bid response for imp 71
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/lifestreet/test-cache-lifestreet-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/lifestreet/test-cache-matcher-lifestreet.json")));
        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"lifestreet":"LS-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImxpZmVzdHJlZXQiOiJMUy1VSUQifX0=")
                .body(jsonFrom("openrtb2/lifestreet/test-auction-lifestreet-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/lifestreet/test-auction-lifestreet-response.json",
                response, singletonList(LIFESTREET));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
