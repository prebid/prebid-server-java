package org.prebid.server.auction.bidderrequestpostprocessor;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.versionconverter.OrtbVersion;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.spring.config.bidder.model.Ortb;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BidderRequestCurrencyBlockerTest {

    private static final String BIDDER = "bidder";

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private BidderAliases bidderAliases;

    private BidderRequestCurrencyBlocker target;

    @BeforeEach
    public void setUp() {
        target = new BidderRequestCurrencyBlocker(bidderCatalog);
        when(bidderAliases.resolveBidder(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void processShouldReturnSameRequestIfAcceptedCurrenciesAreNotConfigured() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(null));
        final BidderRequest bidderRequest = givenBidderRequest(List.of("USD"));

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameRequestIfAcceptedCurrenciesAreEmpty() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(emptyList()));
        final BidderRequest bidderRequest = givenBidderRequest(List.of("USD"));

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameRequestIfRequestCurrencyIsNull() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(List.of("USD")));
        final BidderRequest bidderRequest = givenBidderRequest(null);

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameRequestIfRequestCurrencyIsEmpty() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(List.of("USD")));
        final BidderRequest bidderRequest = givenBidderRequest(emptyList());

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnSameRequestIfRequestCurrencyContainsAcceptedCurrency() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(List.of("EUR", "USD")));
        final BidderRequest bidderRequest = givenBidderRequest(List.of("UAH", "USD"));

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, bidderAliases, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldReturnFailureIfRequestDoesNotContainsAcceptedCurrency() {
        // given
        given(bidderCatalog.bidderInfoByName(BIDDER)).willReturn(givenBidderInfo(List.of("EUR", "USD")));
        final BidderRequest bidderRequest = givenBidderRequest(List.of("UAH"));

        // when
        final Future<BidderRequestPostProcessingResult> result = target.process(bidderRequest, bidderAliases, null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .asInstanceOf(InstanceOfAssertFactories.type(BidderRequestRejectedException.class))
                .satisfies(e -> {
                    assertThat(e.getRejectionReason())
                            .isEqualTo(BidRejectionReason.REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY);
                    assertThat(e.getErrors()).containsExactly(
                            BidderError.generic("No match between the configured currencies and bidRequest.cur"));
                });
    }

    private static BidderInfo givenBidderInfo(List<String> currencies) {
        return BidderInfo.create(
                true,
                OrtbVersion.ORTB_2_6,
                false,
                "endpoint",
                null,
                "maintainerEmail",
                null,
                null,
                null,
                emptyList(),
                0,
                currencies,
                false,
                false,
                CompressionType.NONE,
                Ortb.of(false),
                0L);
    }

    private static BidderRequest givenBidderRequest(List<String> currencies) {
        return BidderRequest.builder()
                .bidRequest(BidRequest.builder().cur(currencies).build())
                .bidder(BIDDER)
                .build();
    }
}
