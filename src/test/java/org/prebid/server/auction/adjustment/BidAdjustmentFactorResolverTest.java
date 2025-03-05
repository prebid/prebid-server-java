package org.prebid.server.auction.adjustment;

import org.junit.jupiter.api.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BidAdjustmentFactorResolverTest {

    private final BidAdjustmentFactorResolver target = new BidAdjustmentFactorResolver();

    @Test
    public void resolveShouldReturnOneIfAdjustmentsByMediaTypeAndBidderAreAbsentForBidderAndForSeat() {
        // when
        final BigDecimal result = target.resolve(
                ImpMediaType.video,
                ExtRequestBidAdjustmentFactors.builder().build(),
                "bidder1",
                "bidder2");

        // then
        assertThat(result).isEqualTo(BigDecimal.ONE);
    }

    @Test
    public void resolveShouldReturnSeatAdjustmentFactorOverBidderIfAdjustmentsByTypeAreAbsentIgnoringCase() {
        // given
        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder().build();
        adjustmentFactors.addFactor("BIDder", BigDecimal.valueOf(3.456));
        adjustmentFactors.addFactor("seat", BigDecimal.valueOf(4.456));

        // when
        final BigDecimal result = target.resolve(ImpMediaType.video, adjustmentFactors, "bidDER", "seAT");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(4.456));
    }

    @Test
    public void resolveShouldReturnBidderAdjustmentFactorIfAdjustmentsByTypeAreAbsentIgnoringCase() {
        // given
        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder().build();
        adjustmentFactors.addFactor("BIDder", BigDecimal.valueOf(3.456));

        // when
        final BigDecimal result = target.resolve(ImpMediaType.video, adjustmentFactors, "bidDER", "seAT");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(3.456));
    }

    @Test
    public void resolveShouldReturnSeatAdjustmentByMediaTypeIfPresentIgnoringCase() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of(
                        "seat", BigDecimal.valueOf(0.234),
                        "BIDder", BigDecimal.valueOf(1.234)),
                ImpMediaType.video_outstream, Map.of(
                        "bidder", BigDecimal.valueOf(2.345),
                        "SEAT", BigDecimal.valueOf(3.345)),
                ImpMediaType.banner, Map.of("bidder", BigDecimal.valueOf(4.456))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder()
                        .mediatypes(adjustmentFactorsByMediaType)
                        .build();

        adjustmentFactors.addFactor("BIDder", BigDecimal.valueOf(5.456));
        adjustmentFactors.addFactor("seat", BigDecimal.valueOf(6.456));

        // when
        final BigDecimal result = target.resolve(ImpMediaType.video, adjustmentFactors, "bidDER", "Seat");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(0.234));
    }

    @Test
    public void resolveShouldReturnSeatAdjustmentFactorOverBidderByMediaTypeIfPresentIgnoringCase() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("BIDder", BigDecimal.valueOf(1.234)),
                ImpMediaType.video_outstream, Map.of("bidder", BigDecimal.valueOf(2.345)),
                ImpMediaType.banner, Map.of("bidder", BigDecimal.valueOf(3.456))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder()
                        .mediatypes(adjustmentFactorsByMediaType)
                        .build();

        adjustmentFactors.addFactor("BIDder", BigDecimal.valueOf(5.456));
        adjustmentFactors.addFactor("seat", BigDecimal.valueOf(6.456));

        // when
        final BigDecimal result = target.resolve(ImpMediaType.video, adjustmentFactors, "bidDER", "Seat");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(6.456));
    }

    @Test
    public void resolveShouldReturnAdjustmentByMediaTypeIfPresentIgnoringCase() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("BIDder", BigDecimal.valueOf(1.234)),
                ImpMediaType.video_outstream, Map.of("bidder", BigDecimal.valueOf(2.345)),
                ImpMediaType.banner, Map.of("bidder", BigDecimal.valueOf(3.456))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder()
                        .mediatypes(adjustmentFactorsByMediaType)
                        .build();

        adjustmentFactors.addFactor("BIDder", BigDecimal.valueOf(5.456));

        // when
        final BigDecimal result = target.resolve(ImpMediaType.video, adjustmentFactors, "bidDER", "Seat");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(1.234));
    }
}
