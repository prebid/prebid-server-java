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
public class TripleliftTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTriplelift() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/triplelift-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/triplelift/test-triplelift-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/triplelift/test-triplelift-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/triplelift/test-auction-triplelift-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/triplelift/test-auction-triplelift-response.json", response,
                singletonList("triplelift"));
    }
}

