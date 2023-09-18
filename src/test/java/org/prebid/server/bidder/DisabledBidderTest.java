package org.prebid.server.bidder;

import org.junit.Test;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DisabledBidderTest {

    private final DisabledBidder target = new DisabledBidder("error message");

    @Test
    public void makeHttpRequestsShouldRespondWithExpectedError() {
        // when
        final Result<List<HttpRequest<Void>>> result = target.makeHttpRequests(null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("error message"));
    }

    @Test
    public void makeBidsShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> target.makeBids(null, null));
    }

    @Test
    public void extractTargetingShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> target.extractTargeting(null));
    }
}
