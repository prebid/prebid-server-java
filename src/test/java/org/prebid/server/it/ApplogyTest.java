package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class ApplogyTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromApplogy() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/applogy-exchange/1234"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/applogy/test-applogy-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/applogy/test-applogy-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/applogy-exchange/12345"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/applogy/test-applogy-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/applogy/test-applogy-bid-response-2.json"))));

        // when
        final Response response = responseFor("openrtb2/applogy/test-auction-applogy-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/applogy/test-auction-applogy-response.json", response, singletonList("applogy"));
    }
}
