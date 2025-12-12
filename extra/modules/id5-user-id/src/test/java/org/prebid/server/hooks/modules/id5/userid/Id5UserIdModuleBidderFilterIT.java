package org.prebid.server.hooks.modules.id5.userid;

import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext
@TestPropertySource(properties = {
        // Override ID5 Module Configuration with bidder filter (allowlist)
        "hooks.id5-user-id.enabled=true",
        "hooks.id5-user-id.partner=173",
        "hooks.id5-user-id.inserter-name=prebid-server",
        "hooks.id5-user-id.fetch-endpoint=http://localhost:8090/id5-fetch",
        "hooks.id5-user-id.bidder-filter.exclude=false",
        "hooks.id5-user-id.bidder-filter.values=generic",
        // Settings Configuration
        "settings.filesystem.settings-filename=src/test/resources/test-app-settings.yaml",
        "settings.filesystem.stored-requests-dir=",
        "settings.filesystem.stored-imps-dir=",
        "settings.filesystem.profiles-dir=",
        "settings.filesystem.stored-responses-dir=",
        "settings.filesystem.categories-dir="
})
public class Id5UserIdModuleBidderFilterIT extends Id5UserIdModuleITBase {

    @Test
    public void shouldOnlyInjectEidsIntoAllowlistedBidder() throws Exception {
        // given: ID5 module enabled with bidder filter allowlist (only "generic" bidder allowed)
        final Response response = sendAuctionRequest(createAuctionRequestWithMultipleBidders());

        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        Assertions.assertThat(response.jsonPath().getString("id")).isEqualTo("test-request-id");

        verifyId5FetchCalled(1);
        verifyBidderReceivedRequestWithId5Eid(GENERIC_EXCHANGE_PATH, TEST_ID5_VALUE);
        verifyBidderReceivedRequestWithoutId5Eid(APPNEXUS_EXCHANGE_PATH);
    }

}
