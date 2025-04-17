package org.prebid.server.bidadjustments;

import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidadjustments.model.BidAdjustmentsRule;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.MULTIPLIER;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.STATIC;
import static org.prebid.server.proto.openrtb.ext.request.ImpMediaType.banner;
import static org.prebid.server.proto.openrtb.ext.request.ImpMediaType.video_outstream;

@ExtendWith(MockitoExtension.class)
public class BidAdjustmentsResolverTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyService;

    @Mock(strictness = LENIENT)
    private BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver;

    private BidAdjustmentsResolver target;

    @BeforeEach
    public void before() {
        target = new BidAdjustmentsResolver(currencyService, bidAdjustmentsRulesResolver);

        given(currencyService.convertCurrency(any(), any(), any(), any())).willAnswer(invocation -> {
            final BigDecimal initialPrice = (BigDecimal) invocation.getArguments()[0];
            return initialPrice.multiply(BigDecimal.TEN);
        });
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificMediaType() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "bidderName", "dealId"))
                .willReturn(List.of(givenStatic("15", "EUR")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", new BigDecimal("15")));
        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardMediaType() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, video_outstream, "bidderName", "dealId"))
                .willReturn(List.of(givenCpm("25", "UAH")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                video_outstream,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-249")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificBidder() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "bidderName", "dealId"))
                .willReturn(List.of(givenMultiplier("15")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("15")));
        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardBidder() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "anotherBidderName", "dealId"))
                .willReturn(List.of(givenStatic("25", "UAH"), givenMultiplier("25")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                banner,
                "anotherBidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("UAH", new BigDecimal("625")));
        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificDealId() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "bidderName", "dealId"))
                .willReturn(List.of(givenCpm("15", "JPY"), givenStatic("15", "EUR")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", new BigDecimal("15")));
        verify(currencyService).convertCurrency(new BigDecimal("15"), givenBidRequest, "JPY", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardDealId() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "bidderName", "anotherDealId"))
                .willReturn(List.of(givenMultiplier("25"), givenCpm("25", "UAH")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                banner,
                "bidderName",
                "anotherDealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-225")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardDealIdWhenDealIdIsNull() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "bidderName", null))
                .willReturn(List.of(givenCpm("25", "UAH"), givenCpm("25", "JPY")));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                banner,
                "bidderName",
                null);

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-499")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "JPY", "USD");
    }

    @Test
    public void resolveShouldReturnEmptyListWhenNoMatchFound() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        given(bidAdjustmentsRulesResolver.resolve(givenBidRequest, banner, "bidderName", null))
                .willReturn(Collections.emptyList());

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                banner,
                "bidderName",
                null);

        // then
        assertThat(actual).isEqualTo(Price.of("USD", BigDecimal.ONE));
        verifyNoInteractions(currencyService);
    }

    private static BidAdjustmentsRule givenStatic(String value, String currency) {
        return BidAdjustmentsRule.builder()
                .adjType(STATIC)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static BidAdjustmentsRule givenCpm(String value, String currency) {
        return BidAdjustmentsRule.builder()
                .adjType(CPM)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static BidAdjustmentsRule givenMultiplier(String value) {
        return BidAdjustmentsRule.builder()
                .adjType(MULTIPLIER)
                .value(new BigDecimal(value))
                .build();
    }
}
