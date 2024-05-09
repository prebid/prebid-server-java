package org.prebid.server.bidder.loyal;

import org.junit.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;


public class LoyalBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test?param={{PlacementId}}";

    private final LoyalBidder target = new LoyalBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LoyalBidder("invalid_url", jacksonMapper));
    }
}
