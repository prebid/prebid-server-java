package org.prebid.server.bidadjustments;

import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidadjustments.model.BidAdjustments;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentsRule;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.MULTIPLIER;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.STATIC;

@ExtendWith(MockitoExtension.class)
public class BidAdjustmentsResolverTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyService;

    private BidAdjustmentsResolver target;

    @BeforeEach
    public void before() {
        target = new BidAdjustmentsResolver(currencyService);

        given(currencyService.convertCurrency(any(), any(), any(), any())).willAnswer(invocation -> {
            final BigDecimal initialPrice = (BigDecimal) invocation.getArguments()[0];
            return initialPrice.multiply(BigDecimal.TEN);
        });
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificMediaType() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "banner|*|*", List.of(givenStatic("15", "EUR")),
                "*|*|*", List.of(givenStatic("25", "UAH"))));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                givenBidAdjustments,
                ImpMediaType.banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", new BigDecimal("15")));
        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardMediaType() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "banner|*|*", List.of(givenCpm("15", "EUR")),
                "*|*|*", List.of(givenCpm("25", "UAH"))));

        final BidRequest givenBidRequest = BidRequest.builder().build();

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                givenBidAdjustments,
                ImpMediaType.video_outstream,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-249")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificBidder() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "*|bidderName|*", List.of(givenMultiplier("15")),
                "*|*|*", List.of(givenMultiplier("25"))));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                givenBidAdjustments,
                ImpMediaType.banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("15")));
        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardBidder() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "*|bidderName|*", List.of(givenStatic("15", "EUR"), givenMultiplier("15")),
                "*|*|*", List.of(givenStatic("25", "UAH"), givenMultiplier("25"))));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                givenBidAdjustments,
                ImpMediaType.banner,
                "anotherBidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("UAH", new BigDecimal("625")));
        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldPickAndApplyRulesBySpecificDealId() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "*|*|dealId", List.of(givenCpm("15", "JPY"), givenStatic("15", "EUR")),
                "*|*|*", List.of(givenCpm("25", "JPY"), givenStatic("25", "UAH"))));
        final BidRequest givenBidRequest = BidRequest.builder().build();

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                givenBidAdjustments,
                ImpMediaType.banner,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", new BigDecimal("15")));
        verify(currencyService).convertCurrency(new BigDecimal("15"), givenBidRequest, "JPY", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardDealId() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "*|*|dealId", List.of(givenMultiplier("15"), givenCpm("15", "EUR")),
                "*|*|*", List.of(givenMultiplier("25"), givenCpm("25", "UAH"))));
        final BidRequest givenBidRequest = BidRequest.builder().build();

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                givenBidAdjustments,
                ImpMediaType.banner,
                "bidderName",
                "anotherDealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-225")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardDealIdWhenDealIdIsNull() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "*|*|dealId", List.of(givenCpm("15", "EUR"), givenCpm("15", "JPY")),
                "*|*|*", List.of(givenCpm("25", "UAH"), givenCpm("25", "JPY"))));
        final BidRequest givenBidRequest = BidRequest.builder().build();

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                givenBidAdjustments,
                ImpMediaType.banner,
                "bidderName",
                null);

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-499")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "JPY", "USD");
    }

    @Test
    public void resolveShouldPickAndApplyRulesByWildcardMediatypeWhenMediatypeIsNull() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "banner|*|dealId", List.of(givenCpm("15", "EUR"), givenCpm("15", "JPY")),
                "*|*|*", List.of(givenCpm("25", "UAH"), givenCpm("25", "JPY"))));
        final BidRequest givenBidRequest = BidRequest.builder().build();

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                givenBidRequest,
                givenBidAdjustments,
                null,
                "bidderName",
                "dealId");

        // then
        assertThat(actual).isEqualTo(Price.of("USD", new BigDecimal("-499")));
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "UAH", "USD");
        verify(currencyService).convertCurrency(new BigDecimal("25"), givenBidRequest, "JPY", "USD");
    }

    @Test
    public void resolveShouldReturnEmptyListWhenNoMatchFound() {
        // given
        final BidAdjustments givenBidAdjustments = BidAdjustments.of(Map.of(
                "*|*|dealId", List.of(givenStatic("15", "EUR"))));

        // when
        final Price actual = target.resolve(
                Price.of("USD", BigDecimal.ONE),
                BidRequest.builder().build(),
                givenBidAdjustments,
                ImpMediaType.banner,
                "bidderName",
                null);

        // then
        assertThat(actual).isEqualTo(Price.of("USD", BigDecimal.ONE));
        verifyNoInteractions(currencyService);
    }

    private static ExtRequestBidAdjustmentsRule givenStatic(String value, String currency) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(STATIC)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static ExtRequestBidAdjustmentsRule givenCpm(String value, String currency) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(CPM)
                .currency(currency)
                .value(new BigDecimal(value))
                .build();
    }

    private static ExtRequestBidAdjustmentsRule givenMultiplier(String value) {
        return ExtRequestBidAdjustmentsRule.builder()
                .adjType(MULTIPLIER)
                .value(new BigDecimal(value))
                .build();
    }
}
