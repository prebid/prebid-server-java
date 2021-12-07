package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.prebid.server.spring.config.bidder.AdnuntiusBidderConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
@Import(AdnuntiusTest.AdnuntiusTestConfiguration.class)
public class AdnuntiusTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdnuntius() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adnuntius-exchange"))
                .withQueryParam("format", equalTo("json"))
                .withQueryParam("tzo", equalTo("-300"))
                .withQueryParam("gdpr", equalTo("0"))
                .withQueryParam("consentString", equalTo("some_consent"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adnuntius/test-adnuntius-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adnuntius/test-adnuntius-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adnuntius/test-auction-adnuntius-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adnuntius/test-auction-adnuntius-response.json", response,
                singletonList("adnuntius"));
    }

    @TestConfiguration
    static class AdnuntiusTestConfiguration implements AdnuntiusBidderConfiguration.AdnuntiusClockConfigurer {

        @Override
        public Clock getClock() {
            return Clock.system(ZoneId.of("UTC+05:00"));
        }
    }
}
