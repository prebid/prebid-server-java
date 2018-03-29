package org.prebid.server.bidder;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.exception.PreBidException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class DisabledAdapterTest {

    private DisabledAdapter disabledAdapter;

    @Before
    public void setUp() {
        disabledAdapter = new DisabledAdapter("error message");
    }

    @Test
    public void makeHttpRequestsShouldRespondWithExpectedError() {
        assertThatExceptionOfType(PreBidException.class)
                .isThrownBy(() -> disabledAdapter.makeHttpRequests(null, null))
                .withMessage("error message");
    }

    @Test
    public void extractBidsShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> disabledAdapter.extractBids(null, null));
    }

    @Test
    public void tolerateErrorsShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> disabledAdapter.tolerateErrors());
    }

    @Test
    public void responseTypeReferenceShouldBeUnsupported() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> disabledAdapter.responseTypeReference());
    }
}
