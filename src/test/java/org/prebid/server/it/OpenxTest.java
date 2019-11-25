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
public class OpenxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromOpenx() throws IOException, JSONException {
        // given
        // openx bid response for imp 011 and 02
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-openx-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/openx/test-openx-bid-response-1.json"))));

        // openx bid response for imp 03
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-openx-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/openx/test-openx-bid-response-2.json"))));

        // openx bid response for imp 04
        wireMockRule.stubFor(post(urlPathEqualTo("/openx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-openx-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/openx/test-openx-bid-response-3.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/openx/test-cache-openx-request.json"), true, false))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/openx/test-cache-matcher-openx.json")
                ));
        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"openx":"OX-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Im9wZW54IjoiT1gtVUlEIn19")
                .body(jsonFrom("openrtb2/openx/test-auction-openx-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/openx/test-auction-openx-response.json",
                response, singletonList("openx"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
