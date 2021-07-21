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
public class RhythmoneTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromRhythmone() throws IOException, JSONException {
        // given002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rhythmone-exchange/72721/0/mvo"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/rhythmone/test-rhythmone-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/rhythmone/test-rhythmone-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/rhythmone/test-auction-rhythmone-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/rhythmone/test-auction-rhythmone-response.json", response,
                singletonList("rhythmone"));
    }
}
