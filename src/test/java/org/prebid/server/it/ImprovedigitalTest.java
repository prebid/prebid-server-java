package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class ImprovedigitalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromImproveDigital() throws IOException, JSONException {
        // #1
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/improvedigital/test-improvedigital-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/improvedigital/test-improvedigital-bid-response-1.json"))));

        // when
        final Response response1 = responseFor("openrtb2/improvedigital/test-auction-improvedigital-request-1.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/improvedigital/test-auction-improvedigital-response-1.json", response1,
                singletonList("improvedigital"));

        // #2
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/improvedigital-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/improvedigital/test-improvedigital-bid-request-2.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/improvedigital/test-improvedigital-bid-response-2.json"))));

        // when
        final Response response2 = responseFor("openrtb2/improvedigital/test-auction-improvedigital-request-2.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/improvedigital/test-auction-improvedigital-response-2.json", response2,
                singletonList("improvedigital"));
    }
}
