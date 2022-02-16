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
public class BizzclickTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromBizzclick() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/bizzclick-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/bizzclick/test-bizzclick-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/bizzclick/test-bizzclick-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/bizzclick/test-auction-bizzclick-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/bizzclick/test-auction-bizzclick-response.json", response,
                singletonList("bizzclick"));
    }
}
