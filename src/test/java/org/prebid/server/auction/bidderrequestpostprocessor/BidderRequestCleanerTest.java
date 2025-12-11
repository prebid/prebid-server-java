package org.prebid.server.auction.bidderrequestpostprocessor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class BidderRequestCleanerTest extends VertxTest {

    private static final String BIDDER = "bidder";

    private BidderRequestCleaner target;

    @BeforeEach
    public void setUp() {
        target = new BidderRequestCleaner();
    }

    @Test
    public void processShouldReturnSameRequest() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(null);

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnCleanedRequest() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(mapper.createObjectNode());

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBiddercontrols)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidderRequest givenBidderRequest(ObjectNode bidderControls) {
        return BidderRequest.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .biddercontrols(bidderControls)
                                .build()))
                        .build())
                .bidder(BIDDER)
                .build();
    }
}
