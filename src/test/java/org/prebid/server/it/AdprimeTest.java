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
public class AdprimeTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdprime() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adprime-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adprime/test-adprime-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adprime/test-adprime-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adprime/test-auction-adprime-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adprime/test-auction-adprime-response.json", response,
                singletonList("adprime"));
    }
}
