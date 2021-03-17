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
public class SynacormediaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSynacorMedia() throws IOException, JSONException {
        // given
        // SynacorMedia bid response for imp 001 and imp 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/synacormedia-exchange/228"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/synacormedia/test-synacormedia-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/synacormedia/test-synacormedia-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/synacormedia/test-cache-synacormedia-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/synacormedia/test-cache-matcher-synacormedia.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"synacormedia":"SCM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InN5bmFjb3JtZWRpYSI6IlNDTS1VSUQifX0=")
                .body(jsonFrom("openrtb2/synacormedia/test-auction-synacormedia-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/synacormedia/test-auction-synacormedia-response.json",
                response, singletonList("synacormedia"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
