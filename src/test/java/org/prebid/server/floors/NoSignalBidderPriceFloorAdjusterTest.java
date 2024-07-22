package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.floors.model.PriceFloorData;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class NoSignalBidderPriceFloorAdjusterTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private PriceFloorAdjuster delegate;

    private NoSignalBidderPriceFloorAdjuster target;

    @BeforeEach
    public void setUp() {
        given(delegate.adjustForImp(any(), any(), any(), any(), any())).willReturn(Price.of("EUR", BigDecimal.ONE));

        target = new NoSignalBidderPriceFloorAdjuster(delegate);
    }

    @Test
    public void adjustForImpShouldCallDelegateAndSkipAnyNoSignalBiddersChecksWhenSkippedIsTrue() {
        // given
        final PriceFloorRules priceFloorRules = PriceFloorRules.builder()
                .enabled(true)
                .skipped(true)
                .data(PriceFloorData.builder()
                        .noFloorSignalBidders(List.of("bidder", "bidder3"))
                        .modelGroups(List.of(PriceFloorModelGroup.builder()
                                .noFloorSignalBidders(List.of("bidder", "bidder2"))
                                .build()))
                        .build())
                .enforcement(PriceFloorEnforcement.builder()
                        .noFloorSignalBidders(List.of("bidder", "bidder4")).build())
                .build();

        final BidRequest givenBidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().floors(priceFloorRules).build()))
                .build();

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenModelGroupAndDataAndEnforcementHaveNotSetNoSignalBidders() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(null, null, null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        assertThat(debugWarnings).isEmpty();
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenModelGroupHasEmptyBiddersAndTakesPrecendenceOverDataAndEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                Collections.emptyList(),
                List.of("bidder", "bidder2"),
                List.of("bidder", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        assertThat(debugWarnings).isEmpty();
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenDataHasEmptyBiddersAndTakesPrecendenceOverEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                Collections.emptyList(),
                List.of("bidder", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenEnforcementHasEmptyBiddersAndModelGroupAndDataBiddersNotSet() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                null,
                Collections.emptyList());

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenModelGroupHasOtherBiddersAndTakesPrecendenceOverDataAndEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("bidder3", "bidder2"),
                List.of("bidder", "bidder2"),
                List.of("bidder", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenDataHasOtherBiddersAndTakesPrecendenceOverEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                List.of("bidder3", "bidder2"),
                List.of("bidder", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenEnforcementHasOtherBiddersAndModelGroupAndDataBiddersNotSet() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                null,
                List.of("bidder3", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenOnlyModelGroupHasNoSignalBiddersWithBidderValue() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("bidder", "bidder2"),
                null,
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        assertThat(actual).isEqualTo(Price.empty());
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenOnlyModelGroupHasNoSignalBiddersWithBidderValueCaseInsensitive() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("biDDer", "bidder2"),
                null,
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        assertThat(actual).isEqualTo(Price.empty());
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenOnlyDataHasNoSignalBiddersWithBidderValue() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                List.of("bidder", "bidder2"),
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        assertThat(actual).isEqualTo(Price.empty());
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenOnlyDataHasNoSignalBiddersWithBidderValueCaseInsensitive() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                List.of("BiDdeR", "bidder2"),
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        assertThat(actual).isEqualTo(Price.empty());
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenOnlyEnforcementHasNoSignalBiddersWithBidderValue() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                null,
                List.of("bidder", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        assertThat(actual).isEqualTo(Price.empty());
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenOnlyEnforceHasNoSignalBiddersWithBidderValueCaseInsensitive() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                null,
                List.of("BIDDER", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        assertThat(actual).isEqualTo(Price.empty());
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenOnlyModelGroupHasOtherBidders() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("bidder3", "bidder2"),
                null,
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenOnlyDataHasOtherBidders() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                List.of("bidder3", "bidder2"),
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(debugWarnings).isEmpty();
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenOnlyEnforcementHasOtherBidders() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                null,
                List.of("bidder3", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        assertThat(debugWarnings).isEmpty();
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenModelGroupHasBiddersSetAndTakesPrecendenceOverDataAndEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("bidder", "bidder2"),
                List.of("bidder3", "bidder2"),
                List.of("bidder3", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.empty());
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenDataHasBiddersSetAndTakesPrecendenceOverEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                List.of("bidder", "bidder2"),
                List.of("bidder3", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.empty());
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenModelHasWildcardAndTakesPrecendenceOverDataAndEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("*"),
                List.of("bidder", "bidder2"),
                List.of("bidder3", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.empty());
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldReturnEmptyPriceWhenDataHasWildcardAndTakesPrecendenceOverEnforce() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                null,
                List.of("bidder3", "*"),
                List.of("bidder3", "bidder2"));

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.empty());
        assertThat(debugWarnings).containsOnly("noFloorSignal to bidder bidder");
        verifyNoInteractions(delegate);
    }

    @Test
    public void adjustForImpShouldCallDelegateWhenModelHasBidderSetAndTakesPrecendenceOverDataWithWildcard() {
        // given
        final BidRequest givenBidRequest = givenBidRequest(
                List.of("bidder3", "bidder2"),
                List.of("*"),
                null);

        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();
        final List<String> debugWarnings = new ArrayList<>();

        // when
        final Price actual = target.adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);

        // then
        assertThat(actual).isEqualTo(Price.of("EUR", BigDecimal.ONE));
        assertThat(debugWarnings).isEmpty();
        verify(delegate).adjustForImp(givenImp, "bidder", givenBidRequest, givenAccount, debugWarnings);
    }

    @Test
    public void revertAdjustmentForImpShouldAlwaysAndOnlyCallDelegate() {
        // given
        final BidRequest givenBidRequest = BidRequest.builder().build();
        final Imp givenImp = givenImp();
        final Account givenAccount = Account.builder().build();

        final Price expectedPrice = Price.of("EUR", BigDecimal.ONE);

        given(delegate.revertAdjustmentForImp(givenImp, "bidder", givenBidRequest, givenAccount))
                .willReturn(expectedPrice);

        // when
        final Price actual = target.revertAdjustmentForImp(givenImp, "bidder", givenBidRequest, givenAccount);

        // then
        assertThat(actual).isSameAs(expectedPrice);
    }

    private static BidRequest givenBidRequest(List<String> modelGroupBidders,
                                              List<String> dataBidders,
                                              List<String> enforcementBidders) {

        final PriceFloorRules priceFloorRules = PriceFloorRules.builder()
                .enabled(true)
                .data(PriceFloorData.builder()
                        .noFloorSignalBidders(dataBidders)
                        .modelGroups(List.of(PriceFloorModelGroup.builder()
                                .noFloorSignalBidders(modelGroupBidders)
                                .build()))
                        .build())
                .enforcement(PriceFloorEnforcement.builder()
                        .noFloorSignalBidders(enforcementBidders).build())
                .build();

        return BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder().floors(priceFloorRules).build()))
                .build();
    }

    private static Imp givenImp() {
        return Imp.builder()
                .id("impId")
                .bidfloor(BigDecimal.TEN)
                .video(Video.builder().placement(1).build())
                .bidfloorcur("USD")
                .ext(jacksonMapper.mapper().createObjectNode())
                .build();
    }
}
