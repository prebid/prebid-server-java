package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdgenerationTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdgeneration() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/adgeneration-exchange"))
                .withQueryParam("posall", equalTo("SSPLOC"))
                .withQueryParam("id", equalTo("id"))
                .withQueryParam("sdktype", equalTo("0"))
                .withQueryParam("hb", equalTo("true"))
                .withQueryParam("t", equalTo("json3"))
                .withQueryParam("currency", equalTo("USD"))
                .withQueryParam("sdkname", equalTo("prebidserver"))
                .withQueryParam("tp", equalTo("http://www.example.com"))
                .withQueryParam("adapterver", equalTo("1.0.2"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalTo("some-agent"))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/adgeneration/test-adgeneration-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adgeneration/test-auction-adgeneration-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adgeneration/test-auction-adgeneration-response.json", response,
                singletonList("adgeneration"));
    }
}
