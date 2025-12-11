package org.prebid.server.hooks.modules.id5.userid;

import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext
@TestPropertySource(properties = {
        // Override ID5 Module Configuration with account filter (blocklist)
        "hooks.id5-user-id.enabled=true",
        "hooks.id5-user-id.partner=173",
        "hooks.id5-user-id.inserter-name=prebid-server",
        "hooks.id5-user-id.fetch-endpoint=http://localhost:8090/id5-fetch",
        "hooks.id5-user-id.account-filter.exclude=true",
        "hooks.id5-user-id.account-filter.values=blocked-account",
        // Settings Configuration
        "settings.filesystem.settings-filename=src/test/resources/test-app-settings.yaml",
        "settings.filesystem.stored-requests-dir=",
        "settings.filesystem.stored-imps-dir=",
        "settings.filesystem.profiles-dir=",
        "settings.filesystem.stored-responses-dir=",
        "settings.filesystem.categories-dir="
})
public class Id5UserIdModuleAccountFilterIT extends Id5UserIdModuleITBase {

    @Test
    public void shouldFetchId5ForNonBlockedAccount() throws Exception {
        // given: ID5 module enabled with account filter blocklist ("blocked-account" excluded)
        // given: Request uses "test-account-id5" which is NOT blocked
        final Response response = sendAuctionRequest(createAuctionRequestWithMultipleBidders());

        Assertions.assertThat(response.statusCode()).isEqualTo(200);

        verifyId5FetchCalled(1);
        verifyBidderReceivedRequestWithId5Eid(GENERIC_EXCHANGE_PATH, TEST_ID5_VALUE);
        verifyBidderReceivedRequestWithId5Eid(APPNEXUS_EXCHANGE_PATH, TEST_ID5_VALUE);
    }

    @Test
    public void shouldSkipFetchForBlockedAccount() throws Exception {
        // given: ID5 module enabled with account filter blocklist ("blocked-account" excluded)
        // given: Request uses "blocked-account" which IS blocked
        final Response response = sendAuctionRequest(createBlockedAccountRequest());

        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        verifyId5FetchCalled(0);
        verifyBidderReceivedRequestWithoutId5Eid(GENERIC_EXCHANGE_PATH);
    }
}
