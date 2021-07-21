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
public class AdtargetTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdtarget() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adtarget-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adtarget/test-adtarget-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adtarget/test-adtarget-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/adtarget/test-auction-adtarget-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adtarget/test-auction-adtarget-response.json", response,
                singletonList("adtarget"));
    }
}
