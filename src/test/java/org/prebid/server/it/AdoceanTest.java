package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdoceanTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdocean() throws IOException, JSONException {

        WIRE_MOCK_RULE.stubFor(get(WireMock.urlPathMatching("/adocean-exchange/_[0-9]*/ad.json"))
                .withQueryParam("pbsrv_v", equalTo("1.2.0"))
                .withQueryParam("id", equalTo("tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7"))
                .withQueryParam("nc", equalTo("1"))
                .withQueryParam("nosecure", equalTo("1"))
                .withQueryParam("aid", equalTo("adoceanmyaozpniqismex:imp_id"))
                .withQueryParam("aosspsizes", equalTo("myaozpniqismex~300x250"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("Content-Type", WireMock.equalTo("application/json;charset=UTF-8"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .willReturn(WireMock.aResponse()
                        .withBody(jsonFrom("openrtb2/adocean/test-adocean-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/adocean/test-auction-adocean-request.json",
                Endpoint.openrtb2_auction);

        System.out.println(response.prettyPrint());
        // then
        assertJsonEquals("openrtb2/adocean/test-auction-adocean-response.json", response,
                singletonList("adocean"));
    }
}
