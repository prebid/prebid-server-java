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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.CPM;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.MULTIPLIER;
import static org.prebid.server.bidadjustments.model.BidAdjustmentType.STATIC;
import static org.prebid.server.proto.openrtb.ext.request.ImpMediaType.banner;
import static org.prebid.server.proto.openrtb.ext.request.ImpMediaType.video_outstream;

@ExtendWith(MockitoExtension.class)
public class FloorAdjustmentsResolverTest extends VertxTest {

    private static final String BIDDER_NAME = "testBidder";
    private static final String USD = "USD";
    private static final String EUR = "EUR";
    private static final String UAH = "UAH";

    @Mock
    private CurrencyConversionService currencyService;

    @Mock(strictness = LENIENT)
    private BidAdjustmentsRulesResolver bidAdjustmentsRulesResolver;

    private FloorAdjustmentsResolver target;

    @BeforeEach
    public void before() {
        target = new FloorAdjustmentsResolver(bidAdjustmentsRulesResolver, currencyService);
    }

    @Test
    public void resolveShouldReturnInitialPriceWhenNoRulesFoundForAnyMediaType() {
        // given
        final Price initialPrice = Price.of(USD, BigDecimal.TEN);
        final BidRequest bidRequest = givenBidRequest(USD);
        final Set<ImpMediaType> mediaTypes = Set.of(banner, video_outstream);

        given(bidAdjustmentsRulesResolver.resolve(bidRequest, banner, BIDDER_NAME)).willReturn(emptyList());
        given(bidAdjustmentsRulesResolver.resolve(bidRequest, video_outstream, BIDDER_NAME)).willReturn(emptyList());
        given(currencyService.convertCurrency(BigDecimal.TEN, bidRequest, USD, USD)).willReturn(BigDecimal.TEN);

        // when
        final Price actual = target.resolve(initialPrice, bidRequest, mediaTypes, BIDDER_NAME);

        // then
        assertThat(actual).isEqualTo(initialPrice);
        verify(currencyService, times(2)).convertCurrency(any(), any(), any(), any());
        verifyNoMoreInteractions(currencyService);
    }

    @Test
    public void resolveShouldApplyMultiplierRuleInReverse() {
        // given
        final Price initialPrice = Price.of(USD, BigDecimal.valueOf(20));
        final BidRequest bidRequest = givenBidRequest(USD);
        final Set<ImpMediaType> mediaTypes = Set.of(banner);

        final BidAdjustmentsRule multiplierRule = givenMultiplier("2");

        given(bidAdjustmentsRulesResolver.resolve(bidRequest, banner, BIDDER_NAME))
                .willReturn(singletonList(multiplierRule));
        given(currencyService.convertCurrency(eq(new BigDecimal("10.0000")), eq(bidRequest), eq(USD), eq(USD)))
                .willReturn(BigDecimal.valueOf(20));

        // when
        final Price actual = target.resolve(initialPrice, bidRequest, mediaTypes, BIDDER_NAME);

        // then
        assertThat(actual).isEqualTo(Price.of(USD, new BigDecimal("10.0000")));

        verify(currencyService).convertCurrency(any(), any(), any(), any());
        verifyNoMoreInteractions(currencyService);
    }

    @Test
    public void resolveShouldApplyCpmRuleInReverse() {
        // given
        final Price initialPrice = Price.of(USD, BigDecimal.valueOf(50));
        final BidRequest bidRequest = givenBidRequest(USD);
        final Set<ImpMediaType> mediaTypes = Set.of(banner);

        final BidAdjustmentsRule cpmRule = givenCpm("5", EUR);

        given(bidAdjustmentsRulesResolver.resolve(bidRequest, banner, BIDDER_NAME)).willReturn(singletonList(cpmRule));
        given(currencyService.convertCurrency(new BigDecimal("5"), bidRequest, EUR, USD))
                .willReturn(BigDecimal.valueOf(0.5));
        given(currencyService.convertCurrency(new BigDecimal("50.5"), bidRequest, USD, USD))
                .willReturn(BigDecimal.valueOf(50));

        // when
        final Price actual = target.resolve(initialPrice, bidRequest, mediaTypes, BIDDER_NAME);

        // then
        final Price expectedPrice = Price.of(USD, new BigDecimal("50.5"));
        assertThat(actual).isEqualTo(expectedPrice);

        verify(currencyService, times(2)).convertCurrency(any(), any(), any(), any());
        verifyNoMoreInteractions(currencyService);
    }

    @Test
    public void resolveShouldThrowExceptionWhenStaticRuleIsEncountered() {
        // given
        final Price initialPrice = Price.of(USD, BigDecimal.TEN);
        final BidRequest bidRequest = givenBidRequest(USD);
        final Set<ImpMediaType> mediaTypes = Set.of(banner);

        final BidAdjustmentsRule staticRule = givenStatic("5", USD);

        given(bidAdjustmentsRulesResolver.resolve(bidRequest, banner, BIDDER_NAME))
                .willReturn(singletonList(staticRule));

        // when and then
        assertThatThrownBy(() -> target.resolve(initialPrice, bidRequest, mediaTypes, BIDDER_NAME))
                .isInstanceOf(PreBidException.class)
                .hasMessage("STATIC type can't be applied to a floor price");

        verifyNoInteractions(currencyService);
    }

    @Test
    public void resolveShouldApplyMultipleRulesInReverseOrder() {
        // given
        final Price initialPrice = Price.of(USD, BigDecimal.valueOf(100));
        final BidRequest bidRequest = givenBidRequest(USD);
        final Set<ImpMediaType> mediaTypes = Set.of(banner);

        final BidAdjustmentsRule rule1 = givenMultiplier("2");
        final BidAdjustmentsRule rule2 = givenCpm("5", EUR);

        given(bidAdjustmentsRulesResolver.resolve(bidRequest, banner, BIDDER_NAME)).willReturn(List.of(rule1, rule2));
        given(currencyService.convertCurrency(new BigDecimal("5"), bidRequest, EUR, USD))
                .willReturn(BigDecimal.valueOf(0.5));
        given(currencyService.convertCurrency(new BigDecimal("50.2500"), bidRequest, USD, USD))
                .willReturn(BigDecimal.valueOf(100));

        // when
        final Price actual = target.resolve(initialPrice, bidRequest, mediaTypes, BIDDER_NAME);

        // then
        assertThat(actual).isEqualTo(Price.of(USD, new BigDecimal("50.2500")));

        verify(currencyService, times(2)).convertCurrency(any(), any(), any(), any());
        verifyNoMoreInteractions(currencyService);
    }

    @Test
    public void resolveShouldChooseMinimalFloorAcrossMediaTypesAfterConversion() {
        // given
        final Price initialPrice = Price.of(USD, BigDecimal.valueOf(100));
        final BidRequest bidRequest = givenBidRequest(EUR);
        final Set<ImpMediaType> mediaTypes = Set.of(banner, video_outstream);

        final BidAdjustmentsRule bannerRule = givenMultiplier("4");
        given(bidAdjustmentsRulesResolver.resolve(bidRequest, banner, BIDDER_NAME))
                .willReturn(singletonList(bannerRule));

        final BidAdjustmentsRule videoRule = givenCpm("500", UAH);
        given(bidAdjustmentsRulesResolver.resolve(bidRequest, video_outstream, BIDDER_NAME))
                .willReturn(singletonList(videoRule));

        given(currencyService.convertCurrency(new BigDecimal("25.0000"), bidRequest, USD, EUR))
                .willReturn(new BigDecimal("250.0000"));
        given(currencyService.convertCurrency(new BigDecimal("500"), bidRequest, UAH, USD))
                .willReturn(new BigDecimal("50"));
        given(currencyService.convertCurrency(new BigDecimal("150"), bidRequest, USD, EUR))
                .willReturn(new BigDecimal("1500"));

        // when
        final Price actual = target.resolve(initialPrice, bidRequest, mediaTypes, BIDDER_NAME);

        // then
        assertThat(actual).isEqualTo(Price.of(USD, new BigDecimal("25.0000")));

        verify(currencyService, times(3)).convertCurrency(any(), any(), any(), any());
        verifyNoMoreInteractions(currencyService);
    }

    private static BidRequest givenBidRequest(String currency) {
        return BidRequest.builder()
                .cur(singletonList(currency))
                .build();
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
