package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.model.BidderError;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

public class CompositeMediaTypeProcessorTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private MediaTypeProcessor mediaTypeProcessor1;

    @Mock
    private MediaTypeProcessor mediaTypeProcessor2;

    private CompositeMediaTypeProcessor target;

    @Before
    public void setUp() {
        target = new CompositeMediaTypeProcessor(asList(mediaTypeProcessor1, mediaTypeProcessor2));
    }

    @Test
    public void processShouldReturnExpectedResult() {
        // given
        given(mediaTypeProcessor1.process(any(), anyString(), any()))
                .willReturn(MediaTypeProcessingResult.succeeded(
                        BidRequest.builder().id("processed by mediaTypeProcessor1").build(),
                        singletonList(BidderError.badInput("Error from mediaTypeProcessor1"))));

        given(mediaTypeProcessor2.process(
                argThat(request -> request.getId().equals("processed by mediaTypeProcessor1")),
                anyString(),
                any()))
                .willReturn(MediaTypeProcessingResult.succeeded(
                        BidRequest.builder().id("processed by mediaTypeProcessor2").build(),
                        singletonList(BidderError.badInput("Error from mediaTypeProcessor2"))));

        // when
        final MediaTypeProcessingResult result = target.process(BidRequest.builder().build(), "bidder", null);

        // then
        assertThat(result.isRejected()).isFalse();
        assertThat(result.getBidRequest())
                .isEqualTo(BidRequest.builder().id("processed by mediaTypeProcessor2").build());
        assertThat(result.getErrors())
                .containsExactly(
                        BidderError.badInput("Error from mediaTypeProcessor1"),
                        BidderError.badInput("Error from mediaTypeProcessor2"));
    }

    @Test
    public void processShouldReturnExpectedResultIfRejectedBySomeOfProcessors() {
        // given
        given(mediaTypeProcessor1.process(any(), anyString(), any()))
                .willReturn(MediaTypeProcessingResult.rejected(
                        singletonList(BidderError.badInput("Error from mediaTypeProcessor1"))));

        // when
        final MediaTypeProcessingResult result = target.process(BidRequest.builder().build(), "bidder", null);

        // then
        assertThat(result.isRejected()).isTrue();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Error from mediaTypeProcessor1"));
        verifyNoInteractions(mediaTypeProcessor2);
    }
}
