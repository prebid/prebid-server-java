package org.prebid.server.auction;

import org.junit.Before;
import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AdjustmentFactorResolverTest {

    private AdjustmentFactorResolver adjustmentFactorResolver;

    @Before
    public void setUp() {
        adjustmentFactorResolver = new AdjustmentFactorResolver();
    }

    @Test
    public void resolveShouldReturnOneIfAdjustmentsByMediaTypeAndBidderAreAbsent() {
        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(
                ImpMediaType.video,
                ExtRequestBidAdjustmentFactors.builder().build(),
                "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.ONE);
    }

    @Test
    public void resolveShouldReturnBidderAdjustmentFactorIfAdjustmentsByTypeAreAbsent() {
        // given
        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder().build();
        adjustmentFactors.addFactor("bidder", BigDecimal.valueOf(3.456));

        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(ImpMediaType.video, adjustmentFactors, "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(3.456));
    }

    @Test
    public void resolveShouldReturnSmallestAdjustmentByMediaTypeIfPresent() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("bidder", BigDecimal.valueOf(1.234)),
                ImpMediaType.video_outstream, Map.of("bidder", BigDecimal.valueOf(2.345)),
                ImpMediaType.banner, Map.of("bidder", BigDecimal.valueOf(3.456))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors =
                ExtRequestBidAdjustmentFactors.builder()
                        .mediatypes(adjustmentFactorsByMediaType)
                        .build();

        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(ImpMediaType.video, adjustmentFactors, "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(1.234));
    }

    @Test
    public void resolveShouldReturnSmallestAdjustmentBetweenMediaTypeAndBidderChosen() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("bidder", BigDecimal.valueOf(1.234))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(adjustmentFactorsByMediaType)
                .build();
        adjustmentFactors.addFactor("bidder", BigDecimal.valueOf(0.123));

        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(ImpMediaType.video, adjustmentFactors, "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(0.123));
    }

    @Test
    public void resolveShouldReturnMediaTypeAdjustmentWhenBidderAdjustmentIsAbsent() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("bidder", BigDecimal.valueOf(1.234))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(adjustmentFactorsByMediaType)
                .build();

        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(ImpMediaType.video, adjustmentFactors, "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(1.234));
    }

    @Test
    public void resolveShouldReturnBidderAdjustmentWhenMediaTypeAdjustmentIsAbsent() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("bidder", BigDecimal.valueOf(1.234))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(adjustmentFactorsByMediaType)
                .build();
        adjustmentFactors.addFactor("bidder", BigDecimal.valueOf(3.456));

        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(ImpMediaType.banner, adjustmentFactors, "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.valueOf(3.456));
    }

    @Test
    public void resolveShouldReturnOneWhenAppropriateBidderAdjustmentAndMediaTypeAdjustmentAreAbsent() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaType = new EnumMap<>(Map.of(
                ImpMediaType.video, Map.of("bidder", BigDecimal.valueOf(1.234))));

        final ExtRequestBidAdjustmentFactors adjustmentFactors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(adjustmentFactorsByMediaType)
                .build();
        adjustmentFactors.addFactor("bidder1", BigDecimal.valueOf(3.456));

        // when
        final BigDecimal result = adjustmentFactorResolver.resolve(ImpMediaType.banner, adjustmentFactors, "bidder");

        // then
        assertThat(result).isEqualTo(BigDecimal.ONE);
    }
}
