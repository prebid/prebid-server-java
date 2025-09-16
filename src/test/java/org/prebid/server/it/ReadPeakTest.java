package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.prebid.server.model.Endpoint;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

public class ReadPeakTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromReadPeak() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/readpeak-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/readpeak/test-readpeak-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/readpeak/test-readpeak-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/readpeak/test-auction-readpeak-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/readpeak/test-auction-readpeak-response.json", response, singletonList("readpeak"));
    }
}
