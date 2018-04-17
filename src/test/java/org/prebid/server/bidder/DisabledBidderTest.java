package org.prebid.server.bidder;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DisabledBidderTest {

    private DisabledBidder disabledBidder;

    @Before
    public void setUp() {
        disabledBidder = new DisabledBidder("error message");
    }

    @Test
    public void makeHttpRequestsShouldRespondWithExpectedError() {
        // when
        final Result<List<HttpRequest<Void>>> result = disabledBidder.makeHttpRequests(null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("error message");
    }

    @Test
    public void makeBidsShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> disabledBidder.makeBids(null, null));
    }

    @Test
    public void extractTargetingShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> disabledBidder.extractTargeting(null));
    }
}
