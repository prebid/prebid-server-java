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
public class RtbhouseTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheRtbHouse() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rtbhouse-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rtbhouse/test-rtbhouse-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rtbhouse/test-rtbhouse-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/rtbhouse/test-auction-rtbhouse-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/rtbhouse/test-auction-rtbhouse-response.json", response,
                singletonList("rtbhouse"));
    }
}
