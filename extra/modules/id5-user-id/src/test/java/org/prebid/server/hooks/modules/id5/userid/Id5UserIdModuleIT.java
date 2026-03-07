package org.prebid.server.hooks.modules.id5.userid;

import io.restassured.response.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class Id5UserIdModuleIT extends Id5UserIdModuleITBase {

    @Test
    public void shouldFetchId5AndInjectEIDsIntoAllBidderRequests() throws Exception {
        // given: ID5 module enabled with no filters (all accounts and bidders allowed)
        final Response response = sendAuctionRequest(createAuctionRequestWithMultipleBidders());

        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        Assertions.assertThat(response.jsonPath().getString("id")).isEqualTo("test-request-id");

        verifyId5FetchCalled(1);
        verifyBidderReceivedRequestWithId5Eid(GENERIC_EXCHANGE_PATH, TEST_ID5_VALUE);
        verifyBidderReceivedRequestWithId5Eid(APPNEXUS_EXCHANGE_PATH, TEST_ID5_VALUE);
    }

    @Test
    public void shouldSkipFetchWhenId5AlreadyPresent() throws Exception {
        // given: ID5 module enabled, request already contains ID5 EID in user.eids
        final Response response = sendAuctionRequest(createAuctionRequestWithExistingId5("existing-id5"));

        Assertions.assertThat(response.statusCode()).isEqualTo(200);
        verifyId5FetchCalled(0);
        verifyBidderReceivedRequestWithExistingId5(GENERIC_EXCHANGE_PATH, "existing-id5");
    }
}
