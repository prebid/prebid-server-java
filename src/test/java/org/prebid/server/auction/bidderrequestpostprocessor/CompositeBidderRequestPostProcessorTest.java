package org.prebid.server.auction.bidderrequestpostprocessor;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderError;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CompositeBidderRequestPostProcessorTest {

    @Mock
    private BidderRequestPostProcessor bidderRequestPostProcessor1;

    @Mock
    private BidderRequestPostProcessor bidderRequestPostProcessor2;

    @Mock(strictness = LENIENT)
    private BidderAliases bidderAliases;

    private CompositeBidderRequestPostProcessor target;

    @BeforeEach
    public void setUp() {
        target = new CompositeBidderRequestPostProcessor(asList(
                bidderRequestPostProcessor1, bidderRequestPostProcessor2));
        when(bidderAliases.resolveBidder(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void processShouldReturnExpectedResult() {
        // given
        given(bidderRequestPostProcessor1.process(any(), any(), any()))
                .willReturn(Future.succeededFuture(BidderRequestPostProcessingResult.of(
                        BidderRequest.builder().bidder("processed by bidderRequestPostProcessor1").build(),
                        singletonList(BidderError.badInput("Error from bidderRequestPostProcessor1")))));

        given(bidderRequestPostProcessor2.process(
                argThat(request -> "processed by bidderRequestPostProcessor1".equals(request.getBidder())),
                any(),
                any()))
                .willReturn(Future.succeededFuture(BidderRequestPostProcessingResult.of(
                        BidderRequest.builder().bidder("processed by bidderRequestPostProcessor2").build(),
                        singletonList(BidderError.badInput("Error from bidderRequestPostProcessor2")))));

        // when
        final Future<BidderRequestPostProcessingResult> result = target.process(null, bidderAliases, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().getValue())
                .isEqualTo(BidderRequest.builder().bidder("processed by bidderRequestPostProcessor2").build());
        assertThat(result.result().getErrors())
                .containsExactly(
                        BidderError.badInput("Error from bidderRequestPostProcessor1"),
                        BidderError.badInput("Error from bidderRequestPostProcessor2"));
    }

    @Test
    public void processShouldReturnExpectedResultIfSomeOfProcessorsFails() {
        // given
        given(bidderRequestPostProcessor1.process(any(), any(), any()))
                .willReturn(Future.failedFuture("Error from bidderRequestPostProcessor1"));

        // when
        final Future<BidderRequestPostProcessingResult> result = target.process(null, bidderAliases, null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo("Error from bidderRequestPostProcessor1");
        verifyNoInteractions(bidderRequestPostProcessor2);
    }
}
