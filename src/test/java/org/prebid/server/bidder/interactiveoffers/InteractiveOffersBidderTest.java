package org.prebid.server.bidder.interactiveoffers;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.interactiveoffers.ExtImpInteractiveoffers;

public class InteractiveOffersBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private InteractiveOffersBidder interactiveOffersBidder;

    @Before
    public void setup() {
        interactiveOffersBidder = new InteractiveOffersBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void modifyImpShouldThrowExceptionInvalidRequest() {
        Assertions.assertThatThrownBy(() -> interactiveOffersBidder.modifyImp(null, ExtImpInteractiveoffers.of(null)))
                .hasMessage("pubid is empty");
    }



}