package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class EplanningTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEplanning() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/eplanning-exchange/12345/1/www.example.com/ROS"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/eplanning/test-eplanning-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/eplanning/test-auction-eplanning-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/eplanning/test-auction-eplanning-response.json", response,
                singletonList("eplanning"));
    }
}
